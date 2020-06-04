/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.KeyUsage;
import org.niis.xroad.restapi.openapi.model.PossibleAction;
import org.niis.xroad.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.restapi.service.CertificateNotFoundException;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.CsrNotFoundException;
import org.niis.xroad.restapi.service.InvalidCertificateException;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.PossibleActionEnum;
import org.niis.xroad.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertificateInfoBuilder;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.util.TestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils.KeyInfoBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.niis.xroad.restapi.service.TokenCertificateService.AuthCertificateNotSupportedException.AUTH_CERT_NOT_SUPPORTED;
import static org.niis.xroad.restapi.util.CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH;
import static org.niis.xroad.restapi.util.CertificateTestUtils.MOCK_CERTIFICATE_HASH;
import static org.niis.xroad.restapi.util.TestUtils.assertLocationHeader;

/**
 * test certificates api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class TokenCertificatesApiControllerIntegrationTest {

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @Autowired
    private TokenCertificatesApiController tokenCertificatesApiController;

    @SpyBean
    private PossibleActionsRuleEngine possibleActionsRuleEngine;

    @Before
    public void setup() throws Exception {
        doAnswer(answer -> "key-id").when(signerProxyFacade).importCert(any(), any(), any());
        doAnswer(answer -> null).when(globalConfFacade).verifyValidity();
        doAnswer(answer -> TestUtils.INSTANCE_FI).when(globalConfFacade).getInstanceIdentifier();
        doAnswer(answer -> TestUtils.getM1Ss1ClientId()).when(globalConfFacade).getSubjectName(any(), any());
        CertificateInfo certificateInfo = new CertificateInfoBuilder()
                .certificateStatus("SAVED").build();
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        doAnswer(answer -> "key-id").when(signerProxyFacade).getKeyIdForCertHash(any());
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder().build();
        KeyInfo keyInfo = new KeyInfoBuilder().id("key-id").build();
        tokenInfo.getKeyInfo().add(keyInfo);
        doAnswer(answer -> Collections.singletonList(tokenInfo)).when(signerProxyFacade).getTokens();
        TokenInfoAndKeyId tokenInfoAndKeyId = new TokenInfoAndKeyId(tokenInfo, keyInfo.getId());
        doAnswer(answer -> tokenInfoAndKeyId).when(signerProxyFacade).getTokenAndKeyIdForCertRequestId(any());
        doAnswer(answer -> tokenInfoAndKeyId).when(signerProxyFacade).getTokenAndKeyIdForCertHash(any());
        // by default all actions are possible
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCertificateActions(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificate() {
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        ResponseEntity<TokenCertificate> response = tokenCertificatesApiController.importCertificate(body);
        TokenCertificate addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSignCertificateDetails(addedCert);
        assertLocationHeader("/api/token-certificates/" + addedCert.getCertificateDetails().getHash(),
                response);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importSignCertificateWithWrongPermission() {
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        tokenCertificatesApiController.importCertificate(body);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importAuthCertificate() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockAuthCertificate();
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(mockAuthCert)
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .build();
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        Resource body = CertificateTestUtils.getResource(mockAuthCert.getEncoded());
        ResponseEntity<TokenCertificate> response = tokenCertificatesApiController.importCertificate(body);
        TokenCertificate addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertAuthCertificateDetails(addedCert);
        assertLocationHeader("/api/token-certificates/" + addedCert.getCertificateDetails().getHash(),
                response);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importAuthCertificateWithWrongPermission() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockAuthCertificate();
        Resource body = CertificateTestUtils.getResource(mockAuthCert.getEncoded());
        tokenCertificatesApiController.importCertificate(body);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificateMissingClient() throws Exception {
        ClientId notFoundId = TestUtils.getClientId(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO,
                TestUtils.MEMBER_CODE_M2, TestUtils.SUBSYSTEM3);
        doAnswer(answer -> notFoundId).when(globalConfFacade).getSubjectName(any(), any());
        X509Certificate mockCert = CertificateTestUtils.getMockCertificate();
        Resource body = CertificateTestUtils.getResource(mockCert.getEncoded());
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(ClientNotFoundException.ERROR_CLIENT_NOT_FOUND, error.getCode());
            assertEquals(FormatUtils.xRoadIdToEncodedId(notFoundId), error.getMetadata().get(0));
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importExistingSignCertificate() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_CERT_EXISTS, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (ConflictException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(CertificateAlreadyExistsException.ERROR_CERTIFICATE_ALREADY_EXISTS, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importIncorrectSignCertificate() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_INCORRECT_CERTIFICATE, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(InvalidCertificateException.INVALID_CERT, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importWrongUsageSignCertificate() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_WRONG_CERT_USAGE, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(TokenCertificateService.WrongCertificateUsageException.ERROR_CERTIFICATE_WRONG_USAGE,
                    error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificateCsrMissing() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_CSR_NOT_FOUND, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (ConflictException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(CsrNotFoundException.ERROR_CSR_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificateKeyNotFound() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_KEY_NOT_FOUND, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(KeyNotFoundException.ERROR_KEY_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importInvalidSignCertificate() throws Exception {
        Resource body = CertificateTestUtils.getResource(new byte[] {0, 0, 0, 0});
        try {
            tokenCertificatesApiController.importCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(InvalidCertificateException.INVALID_CERT, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "VIEW_CERT")
    public void getCertificateForHash() throws Exception {
        ResponseEntity<TokenCertificate> response =
                tokenCertificatesApiController.getCertificate(MOCK_CERTIFICATE_HASH);
        TokenCertificate addedCert = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSignCertificateDetails(addedCert);
    }

    @Test
    @WithMockUser(authorities = "VIEW_CERT")
    public void getCertificateForHashNotFound() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_CERT_NOT_FOUND, "mock code", "mock msg"))
                .when(signerProxyFacade).getCertForHash(any());
        try {
            tokenCertificatesApiController.getCertificate("knock knock");
        } catch (ResourceNotFoundException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(CertificateNotFoundException.ERROR_CERTIFICATE_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importCertificateFromToken() throws Exception {
        ResponseEntity<TokenCertificate> response =
                tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
        TokenCertificate addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSignCertificateDetails(addedCert);
        assertLocationHeader("/api/token-certificates/" + addedCert.getCertificateDetails().getHash(),
                response);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importCertificateFromTokenActionNotPossible() throws Exception {
        // by default all actions are possible
        doReturn(EnumSet.noneOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCertificateActions(any(), any(), any());

        tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importCertificateFromTokenHashNotFound() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_CERT_NOT_FOUND, "mock code", "mock msg"))
                .when(signerProxyFacade).getCertForHash(any());
        try {
            tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
        } catch (ResourceNotFoundException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(CertificateNotFoundException.ERROR_CERTIFICATE_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importAuthCertificateFromToken() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockAuthCertificate();
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(mockAuthCert)
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .build();
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        try {
            tokenCertificatesApiController.importCertificateFromToken(MOCK_AUTH_CERTIFICATE_HASH);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(AUTH_CERT_NOT_SUPPORTED, error.getCode());
        }
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importSignCertificateFromTokenWithWrongPermission() {
        tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
    }

    private static void assertSignCertificateDetails(TokenCertificate tokenCertificate) {
        CertificateDetails certificateDetails = tokenCertificate.getCertificateDetails();
        assertEquals("N/A", certificateDetails.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"),
                certificateDetails.getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"),
                certificateDetails.getNotAfter());
        assertEquals("1", certificateDetails.getSerial());
        assertEquals(new Integer(3), certificateDetails.getVersion());
        assertEquals("SHA512withRSA", certificateDetails.getSignatureAlgorithm());
        assertEquals("RSA", certificateDetails.getPublicKeyAlgorithm());
        assertEquals("A2293825AA82A5429EC32803847E2152A303969C", certificateDetails.getHash());
        assertTrue(certificateDetails.getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(certificateDetails.getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(new Integer(65537), certificateDetails.getRsaPublicKeyExponent());
        assertEquals(new ArrayList<>(Collections.singletonList(KeyUsage.NON_REPUDIATION)),
                new ArrayList<>(certificateDetails.getKeyUsages()));
    }

    private static void assertAuthCertificateDetails(TokenCertificate tokenCertificate) {
        CertificateDetails certificateDetails = tokenCertificate.getCertificateDetails();
        assertEquals("Customized Test CA CN", certificateDetails.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("2019-11-28T09:20:27Z"),
                certificateDetails.getNotBefore());
        assertEquals(OffsetDateTime.parse("2039-11-23T09:20:27Z"),
                certificateDetails.getNotAfter());
        assertEquals("8", certificateDetails.getSerial());
        assertEquals(new Integer(3), certificateDetails.getVersion());
        assertEquals("SHA256withRSA", certificateDetails.getSignatureAlgorithm());
        assertEquals("RSA", certificateDetails.getPublicKeyAlgorithm());
        assertEquals("BA6CCC3B13E23BB1D40FD17631B7D93CF8334C0E", certificateDetails.getHash());
        assertTrue(certificateDetails.getSignature().startsWith("a11c4675cf4e2fa1664464"));
        assertTrue(certificateDetails.getRsaPublicKeyModulus().startsWith("92e952dfc1d84648c2873"));
        assertEquals(new Integer(65537), certificateDetails.getRsaPublicKeyExponent());
        assertEquals(Arrays.asList(KeyUsage.DIGITAL_SIGNATURE, KeyUsage.KEY_ENCIPHERMENT, KeyUsage.DATA_ENCIPHERMENT,
                KeyUsage.KEY_AGREEMENT), new ArrayList<>(certificateDetails.getKeyUsages()));
    }

    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificate() throws Exception {
        ResponseEntity<Void> response =
                tokenCertificatesApiController.deleteCertificate(MOCK_CERTIFICATE_HASH);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateNotFound() throws Exception {
        doThrow(CodedException
                .tr(X_CERT_NOT_FOUND, "mock code", "mock msg")
                .withPrefix(SIGNER_X))
                .when(signerProxyFacade).getCertForHash(any());
        doThrow(CodedException
                .tr(X_CERT_NOT_FOUND, "mock code", "mock msg")
                .withPrefix(SIGNER_X))
                .when(signerProxyFacade).deleteCert(any());
        try {
            tokenCertificatesApiController.deleteCertificate("knock knock");
        } catch (ResourceNotFoundException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(CertificateNotFoundException.ERROR_CERTIFICATE_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getPossibleActionsForCertificate() throws Exception {
        ResponseEntity<List<PossibleAction>> response = tokenCertificatesApiController
                .getPossibleActionsForCertificate(MOCK_CERTIFICATE_HASH);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<PossibleAction> allActions = new HashSet(Arrays.asList(PossibleAction.values()));
        assertEquals(allActions, new HashSet<>(response.getBody()));
    }

}
