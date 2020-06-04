/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider;
import ee.ria.xroad.common.certificateprofile.GetCertificateProfile;
import ee.ria.xroad.common.certificateprofile.impl.AuthCertificateProfileInfoParameters;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.dto.ApprovedCaDto;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.util.OcspUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service that handles approved certificate authorities
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class CertificateAuthorityService {

    // "not available" OCSP response status code.
    // Used in addition to CertificateInfo statuses such as OCSP_RESPONSE_SUSPENDED
    public static final String OCSP_RESPONSE_NOT_AVAILABLE = "not available";

    private static final String GET_CERTIFICATE_AUTHORITIES_CACHE = "certificate-authorities";
    private static final int CACHE_EVICT_RATE = 60000; // 1 min

    private final GlobalConfService globalConfService;
    private final GlobalConfFacade globalConfFacade;
    private final ClientService clientService;
    private final SignerProxyFacade signerProxyFacade;
    private final CurrentSecurityServerId currentSecurityServerId;

    /**
     * constructor
     */
    @Autowired
    public CertificateAuthorityService(GlobalConfService globalConfService,
            GlobalConfFacade globalConfFacade,
            ClientService clientService,
            SignerProxyFacade signerProxyFacade,
            CurrentSecurityServerId currentSecurityServerId) {
        this.globalConfService = globalConfService;
        this.globalConfFacade = globalConfFacade;
        this.clientService = clientService;
        this.signerProxyFacade = signerProxyFacade;
        this.currentSecurityServerId = currentSecurityServerId;
    }

    /**
     * {@link CertificateAuthorityService#getCertificateAuthorities(KeyUsageInfo, boolean)}
     * Returns top level certificate authorities
     * @param keyUsageInfo list CAs for this type of key usage. If null, list all.
     */
    @Cacheable(GET_CERTIFICATE_AUTHORITIES_CACHE)
    public List<ApprovedCaDto> getCertificateAuthorities(KeyUsageInfo keyUsageInfo)
            throws InconsistentCaDataException {
        return getCertificateAuthorities(keyUsageInfo, false);
    }

    /**
     * scheduled method needs to be in a separate component,
     * otherwise we get a problem with service level PreAuthorize
     * and "missing authentication"
     */
    @Component("certificateAuthorityCacheEvictor")
    class CacheEvictor {
        /**
         * Tests need to be able to turn off cache eviction to be predictable
         */
        @Getter
        @Setter
        private boolean evict = true;

        @CacheEvict(allEntries = true, cacheNames = { GET_CERTIFICATE_AUTHORITIES_CACHE },
                condition = "@certificateAuthorityCacheEvictor.evict")
        @Scheduled(fixedDelay = CACHE_EVICT_RATE)
        public void evict() {
            // method is empty on purpose. Functionality is based on annotations
        }
    }

    /**
     * Return approved certificate authorities
     * @param keyUsageInfo list CAs for this type of key usage. If null, list all.
     * @param includeIntermediateCas true = also include intermediate CAs.
     *                               false = only include top CAs
     * @throws InconsistentCaDataException if required CA data could not be extracted, for example due to OCSP
     * responses not being valid
     * @return
     */
    @Cacheable(GET_CERTIFICATE_AUTHORITIES_CACHE)
    public List<ApprovedCaDto> getCertificateAuthorities(KeyUsageInfo keyUsageInfo,
            boolean includeIntermediateCas) throws InconsistentCaDataException {

        log.info("getCertificateAuthorities");
        List<X509Certificate> caCerts = new ArrayList<>(globalConfService.getAllCaCertsForThisInstance());
        List<ApprovedCaDto> dtos = new ArrayList<>();
        // map of each subject - issuer DN pair for easy lookups
        Map<String, String> subjectsToIssuers = caCerts.stream().collect(
                Collectors.toMap(
                        x509 -> x509.getSubjectDN().getName(),
                        x509 -> x509.getIssuerDN().getName()));

        String[] base64EncodedOcspResponses;
        try {
            String[] certHashes = CertUtils.getCertHashes(new ArrayList<>(caCerts));
            base64EncodedOcspResponses = signerProxyFacade.getOcspResponses(certHashes);
        } catch (Exception e) {
            throw new InconsistentCaDataException("failed to get read CA OCSP responses", e);
        }
        if (caCerts.size() != base64EncodedOcspResponses.length) {
            throw new InconsistentCaDataException("ocsp responses do not match ca certs");
        }

        // build dtos
        for (int i = 0; i < caCerts.size(); i++) {
            dtos.add(buildCertificateAuthorityDto(caCerts.get(i),
                    base64EncodedOcspResponses[i],
                    subjectsToIssuers));
        }

        if (keyUsageInfo == KeyUsageInfo.SIGNING) {
            // remove "authentication only" CAs
            dtos = dtos.stream()
                    .filter(dto -> !(Boolean.TRUE.equals(dto.isAuthenticationOnly())))
                    .collect(Collectors.toList());
        }

        if (!includeIntermediateCas) {
            // remove intermediate CAs
            dtos = dtos.stream()
                    .filter(dto -> dto.isTopCa())
                    .collect(Collectors.toList());
        }

        return dtos;
    }

    /**
     * Build a single {@code ApprovedCaDto} object using given parameters
     * @param certificate CA certificate
     * @param base64EncodedOcspResponse OCSP response
     * @param subjectsToIssuers map linking all CA subject DNs to corresponding issuer DNs
     * @return
     * @throws InconsistentCaDataException if required CA data could not be extracted, for example due to OCSP
     * responses not being valid
     */
    private ApprovedCaDto buildCertificateAuthorityDto(
            X509Certificate certificate, String base64EncodedOcspResponse,
            Map<String, String> subjectsToIssuers)
            throws InconsistentCaDataException {
        ApprovedCAInfo approvedCAInfo = globalConfService.getApprovedCAForThisInstance(certificate);
        if (approvedCAInfo == null) {
            throw new InconsistentCaDataException("approved ca info not found");
        }

        // properties from ApprovedCAInfo
        ApprovedCaDto.ApprovedCaDtoBuilder builder = ApprovedCaDto.builder();
        builder.authenticationOnly(Boolean.TRUE.equals(approvedCAInfo.getAuthenticationOnly()));
        builder.name(approvedCAInfo.getName());

        // properties from X509Certificate
        builder.notAfter(FormatUtils.fromDateToOffsetDateTime(certificate.getNotAfter()));
        builder.issuerDistinguishedName(certificate.getIssuerDN().getName());
        String subjectName = certificate.getSubjectDN().getName();
        builder.subjectDistinguishedName(subjectName);

        // properties from ocsp response
        String ocspResponseStatus = null;
        try {
            ocspResponseStatus = OcspUtils.getOcspResponseStatus(base64EncodedOcspResponse);
        } catch (OcspUtils.OcspStatusExtractionException e) {
            throw new InconsistentCaDataException(e);
        }
        if (ocspResponseStatus == null) {
            builder.ocspResponse(OCSP_RESPONSE_NOT_AVAILABLE);
        } else {
            builder.ocspResponse(ocspResponseStatus);
        }

        // path and is-top-ca info
        List<String> subjectDnPath = buildPath(certificate, subjectsToIssuers);
        builder.subjectDnPath(subjectDnPath);
        if (subjectDnPath.size() > 1 || !subjectName.equals(subjectDnPath.get(0))) {
            builder.topCa(false);
        } else {
            builder.topCa(true);
        }

        return builder.build();
    }

    /**
     * Build path from topmost CA down to this CA using subject-issuer relationships
     */
    List<String> buildPath(X509Certificate certificate,
            Map<String, String> subjectsToIssuers) {
        ArrayList<String> pathElements = new ArrayList<>();
        String current = certificate.getSubjectDN().getName();
        String issuer = certificate.getIssuerDN().getName();
        pathElements.add(current);
        while (!current.equals(issuer) && subjectsToIssuers.containsKey(issuer)) {
            pathElements.add(0, issuer);
            current = issuer;
            issuer = subjectsToIssuers.get(current);
        }
        return pathElements;
    }

    /**
     * Return correct CertificateProfileInfo for given parameters
     * @param caName
     * @param keyUsageInfo
     * @param memberId member when key usage = signing, ignored otherwise
     * @return
     * @throws CertificateAuthorityNotFoundException if matching CA was not found
     * @throws CertificateProfileInstantiationException if instantiation of certificate profile failed
     * @throws WrongKeyUsageException if attempted to read signing profile from authenticationOnly ca
     * @throws ClientNotFoundException if client with memberId was not found
     */
    public CertificateProfileInfo getCertificateProfile(String caName, KeyUsageInfo keyUsageInfo, ClientId memberId)
            throws CertificateAuthorityNotFoundException, CertificateProfileInstantiationException,
            WrongKeyUsageException, ClientNotFoundException {
        ApprovedCAInfo caInfo = getCertificateAuthorityInfo(caName);
        if (Boolean.TRUE.equals(caInfo.getAuthenticationOnly()) && KeyUsageInfo.SIGNING == keyUsageInfo) {
            throw new WrongKeyUsageException();
        }
        if (keyUsageInfo == KeyUsageInfo.SIGNING) {
            // validate that the member exists or has a subsystem on this server
            if (!clientService.getLocalClientMemberIds().contains(memberId)) {
                throw new ClientNotFoundException("client with id " + memberId + ", or subsystem for it, not found");
            }
        }
        CertificateProfileInfoProvider provider = null;
        try {
            provider = new GetCertificateProfile(caInfo.getCertificateProfileInfo()).instance();
        } catch (Exception e) {
            throw new CertificateProfileInstantiationException(e);
        }
        SecurityServerId serverId = currentSecurityServerId.getServerId();

        if (KeyUsageInfo.AUTHENTICATION == keyUsageInfo) {
            String ownerName = globalConfFacade.getMemberName(serverId.getOwner());
            AuthCertificateProfileInfoParameters params = new AuthCertificateProfileInfoParameters(
                    serverId, ownerName);
            return provider.getAuthCertProfile(params);
        } else if (KeyUsageInfo.SIGNING == keyUsageInfo) {
            String memberName = globalConfFacade.getMemberName(memberId);
            SignCertificateProfileInfoParameters params = new SignCertificateProfileInfoParameters(
                    serverId, memberId, memberName);
            return provider.getSignCertProfile(params);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Return ApprovedCAInfo for CA with given CN name
     * @param caName CN name
     * @throws CertificateAuthorityNotFoundException if matching CA was not found
     */
    public ApprovedCAInfo getCertificateAuthorityInfo(String caName) throws CertificateAuthorityNotFoundException {
        Collection<ApprovedCAInfo> cas = globalConfService.getApprovedCAsForThisInstance();
        Optional<ApprovedCAInfo> ca = cas.stream()
                .filter(item -> caName.equals(item.getName()))
                .findFirst();
        if (!ca.isPresent()) {
            throw new CertificateAuthorityNotFoundException("certificate authority "
                    + caName + " not_found");
        }
        return ca.get();
    }

    /**
     * Thrown when attempted to find CA certificate status and other details, but failed
     */
    public static class InconsistentCaDataException extends ServiceException {
        public static final String ERROR_CA_CERT_PROCESSING = "ca_cert_status_processing_failure";
        public InconsistentCaDataException(String s, Throwable t) {
            super(s, t, new ErrorDeviation(ERROR_CA_CERT_PROCESSING));
        }
        public InconsistentCaDataException(String s) {
            super(s, new ErrorDeviation(ERROR_CA_CERT_PROCESSING));
        }
        public InconsistentCaDataException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_CA_CERT_PROCESSING));
        }
    }

}
