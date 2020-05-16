/**
 * The MIT License
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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.niis.xroad.restapi.service.SecurityHelper.verifyAuthority;

/**
 * Service that handles keys
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class KeyService {

    private final SignerProxyFacade signerProxyFacade;
    private final TokenService tokenService;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final ManagementRequestSenderService managementRequestSenderService;

    /**
     * KeyService constructor
     */
    @Autowired
    public KeyService(TokenService tokenService, SignerProxyFacade signerProxyFacade,
            PossibleActionsRuleEngine possibleActionsRuleEngine,
            ManagementRequestSenderService managementRequestSenderService) {
        this.tokenService = tokenService;
        this.signerProxyFacade = signerProxyFacade;
        this.possibleActionsRuleEngine = possibleActionsRuleEngine;
        this.managementRequestSenderService = managementRequestSenderService;
    }

    /**
     * Return one key
     * @param keyId
     * @return
     * @throws KeyNotFoundException if key was not found
     */
    public KeyInfo getKey(String keyId) throws KeyNotFoundException {
        Collection<TokenInfo> tokens = tokenService.getAllTokens();
        Optional<KeyInfo> keyInfo = tokens.stream()
                .map(TokenInfo::getKeyInfo)
                .flatMap(List::stream)
                .filter(key -> keyId.equals(key.getId()))
                .findFirst();
        if (!keyInfo.isPresent()) {
            throw new KeyNotFoundException("key with id " + keyId + " not found");
        }

        return keyInfo.get();
    }

    /**
     * Finds matching KeyInfo from this TokenInfo, or throws exception
     * @param tokenInfo token
     * @param keyId id of a key inside the token
     * @throws NoSuchElementException if key with keyId was not found
     */
    public KeyInfo getKey(TokenInfo tokenInfo, String keyId) throws NoSuchElementException {
        return tokenInfo.getKeyInfo().stream()
                .filter(k -> k.getId().equals(keyId))
                .findFirst()
                .get();
    }

    /**
     * Updates key friendly name
     * @throws KeyNotFoundException if key was not found
     * @throws ActionNotPossibleException if friendly name could not be updated for this key
     */
    public KeyInfo updateKeyFriendlyName(String id, String friendlyName) throws KeyNotFoundException,
            ActionNotPossibleException {

        // check that updating friendly name is possible
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(id);
        KeyInfo keyInfo = getKey(tokenInfo, id);
        possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.EDIT_FRIENDLY_NAME,
                tokenInfo, keyInfo);

        try {
            signerProxyFacade.setKeyFriendlyName(id, friendlyName);
            keyInfo = getKey(id);
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (CodedException e) {
            if (isCausedByKeyNotFound(e)) {
                throw new KeyNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Update key friendly name failed", e);
        }

        return keyInfo;
    }

    /**
     * Generate a new key for selected token
     * @param tokenId
     * @param keyLabel
     * @return {@link KeyInfo}
     * @throws TokenNotFoundException if token was not found
     * @throws ActionNotPossibleException if generate key was not possible for this token
     */
    public KeyInfo addKey(String tokenId, String keyLabel) throws TokenNotFoundException,
            ActionNotPossibleException {

        // check that adding a key is possible
        TokenInfo tokenInfo = tokenService.getToken(tokenId);
        possibleActionsRuleEngine.requirePossibleTokenAction(PossibleActionEnum.GENERATE_KEY,
                tokenInfo);

        KeyInfo keyInfo = null;
        try {
            keyInfo = signerProxyFacade.generateKey(tokenId, keyLabel);
        } catch (CodedException e) {
            throw e;
        } catch (Exception other) {
            throw new RuntimeException("adding a new key failed", other);
        }
        return keyInfo;
    }

    static boolean isCausedByKeyNotFound(CodedException e) {
        return KEY_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    private static String signerFaultCode(String detail) {
        return SIGNER_X + "." + detail;
    }

    static final String KEY_NOT_FOUND_FAULT_CODE = signerFaultCode(X_KEY_NOT_FOUND);

    /**
     * Deletes one key
     * @param keyId
     * @throws ActionNotPossibleException if delete was not possible for the key
     * @throws KeyNotFoundException if key with given id was not found
     * @throws org.niis.xroad.restapi.service.GlobalConfOutdatedException if global conf was outdated
     */
    public void deleteKey(String keyId) throws KeyNotFoundException, ActionNotPossibleException,
            GlobalConfOutdatedException {
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(keyId);
        KeyInfo keyInfo = getKey(tokenInfo, keyId);

        // verify permissions
        if (keyInfo.getUsage() == null) {
            verifyAuthority("DELETE_KEY");
        } else if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION) {
            verifyAuthority("DELETE_AUTH_KEY");
        } else if (keyInfo.getUsage() == KeyUsageInfo.SIGNING) {
            verifyAuthority("DELETE_SIGN_KEY");
        }

        // verify that action is possible
        possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.DELETE,
                tokenInfo, keyInfo);

        // unregister possible auth certs
        if (keyInfo.getUsage() == KeyUsageInfo.AUTHENTICATION) {
            for (CertificateInfo certificateInfo : keyInfo.getCerts()) {
                if (certificateInfo.getStatus().equals(CertificateInfo.STATUS_REGINPROG)
                        || certificateInfo.getStatus().equals(CertificateInfo.STATUS_REGISTERED)) {
                    unregisterAuthCert(certificateInfo);
                }
            }
        }

        // delete key needs to be done twice. First call deletes the certs & csrs
        try {
            signerProxyFacade.deleteKey(keyId, false);
            signerProxyFacade.deleteKey(keyId, true);
        } catch (CodedException e) {
            throw e;
        } catch (Exception other) {
            throw new RuntimeException("delete key failed", other);
        }
    }

    /**
     * Unregister one auth cert
     */
    private void unregisterAuthCert(CertificateInfo certificateInfo)
            throws GlobalConfOutdatedException {
        // this permission is not checked by unregisterCertificate()
        verifyAuthority("SEND_AUTH_CERT_DEL_REQ");

        // do not use tokenCertificateService.unregisterAuthCert because
        // - it does a bit of extra work to what we need (and makes us do extra work)
        // - we do not want to solve circular dependency KeyService <-> TokenCertificateService

        try {
            // management request to unregister / delete
            managementRequestSenderService.sendAuthCertDeletionRequest(
                    certificateInfo.getCertificateBytes());
            // update status
            signerProxyFacade.setCertStatus(certificateInfo.getId(), CertificateInfo.STATUS_DELINPROG);
        } catch (GlobalConfOutdatedException | CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not unregister auth cert", e);
        }
    }

    /**
     * Return possible actions for one key
     * @throw KeyNotFoundException if key with given id was not found
     */
    public EnumSet<PossibleActionEnum> getPossibleActionsForKey(String keyId) throws KeyNotFoundException {
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(keyId);
        KeyInfo keyInfo = getKey(tokenInfo, keyId);
        EnumSet<PossibleActionEnum> possibleActions = possibleActionsRuleEngine
                .getPossibleKeyActions(tokenInfo, keyInfo);
        return possibleActions;
    }

}
