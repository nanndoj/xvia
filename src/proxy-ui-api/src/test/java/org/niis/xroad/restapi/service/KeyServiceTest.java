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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertRequestInfoBuilder;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertificateInfoBuilder;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils.KeyInfoBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * test key service.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class KeyServiceTest {

    // token ids for mocking
    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String AUTH_KEY_ID = "auth-key-id";
    private static final String SIGN_KEY_ID = "sign-key-id";
    private static final String TYPELESS_KEY_ID = "typeless-key-id";
    private static final String REGISTERED_AUTH_CERT_ID = "registered-auth-cert";
    private static final String NONREGISTERED_AUTH_CERT_ID = "unregistered-auth-cert";

    @Autowired
    private KeyService keyService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private ManagementRequestSenderService managementRequestSenderService;

    // allow all actions
    @MockBean
    PossibleActionsRuleEngine possibleActionsRuleEngine;

    @Before
    public void setup() throws Exception {
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("good-token").build();

        // auth key
        KeyInfo authKey = new KeyInfoBuilder()
                .id(AUTH_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .build();
        CertificateInfo registeredCert = new CertificateInfoBuilder()
                .savedToConfiguration(true)
                .certificateStatus(CertificateInfo.STATUS_REGISTERED)
                .id(REGISTERED_AUTH_CERT_ID)
                .build();
        CertificateInfo nonregisteredCert = new CertificateInfoBuilder()
                .savedToConfiguration(true)
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .id(NONREGISTERED_AUTH_CERT_ID)
                .build();
        authKey.getCerts().add(registeredCert);
        authKey.getCerts().add(nonregisteredCert);
        CertRequestInfo certRequestInfo = new CertRequestInfoBuilder()
                .build();
        authKey.getCertRequests().add(certRequestInfo);

        // sign and typeless keys
        KeyInfo signKey = new KeyInfoBuilder()
                .id(SIGN_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .build();
        KeyInfo typelessKey = new KeyInfoBuilder()
                .id(TYPELESS_KEY_ID)
                .keyUsageInfo(null)
                .build();
        tokenInfo.getKeyInfo().add(authKey);
        tokenInfo.getKeyInfo().add(signKey);
        tokenInfo.getKeyInfo().add(typelessKey);
        when(tokenService.getAllTokens()).thenReturn(Collections.singletonList(tokenInfo));

        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            String newKeyName = (String) arguments[1];
            if ("new-friendly-name-update-fails".equals(newKeyName)) {
                throw new CodedException(SIGNER_X + "." + X_KEY_NOT_FOUND);
            }
            if (arguments[0].equals(AUTH_KEY_ID)) {
                ReflectionTestUtils.setField(authKey, "friendlyName", newKeyName);
            } else {
                throw new RuntimeException(arguments[0] + " not supported");
            }
            return null;
        }).when(signerProxyFacade).setKeyFriendlyName(any(), any());
        doAnswer(invocation -> {
            String keyId = (String) invocation.getArguments()[0];
            if (AUTH_KEY_ID.equals(keyId)
                    || SIGN_KEY_ID.equals(keyId)
                    || TYPELESS_KEY_ID.equals(keyId)) {
                return tokenInfo;
            } else {
                throw new KeyNotFoundException(keyId + " not supported");
            }
        }).when(tokenService).getTokenForKeyId(any());

        // by default all actions are possible
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleKeyActions(any(), any());
    }

    @Test
    public void getKey() throws Exception {
        try {
            keyService.getKey(KEY_NOT_FOUND_KEY_ID);
        } catch (KeyNotFoundException expected) {
        }
        KeyInfo keyInfo = keyService.getKey(AUTH_KEY_ID);
        assertEquals(AUTH_KEY_ID, keyInfo.getId());
    }

    @Test
    public void updateKeyFriendlyName() throws Exception {
        KeyInfo keyInfo = keyService.getKey(AUTH_KEY_ID);
        assertEquals("friendly-name", keyInfo.getFriendlyName());
        keyInfo = keyService.updateKeyFriendlyName(AUTH_KEY_ID, "new-friendly-name");
        assertEquals("new-friendly-name", keyInfo.getFriendlyName());
    }

    @Test(expected = KeyNotFoundException.class)
    public void updateKeyFriendlyNameKeyNotExist() throws Exception {
        keyService.updateKeyFriendlyName(KEY_NOT_FOUND_KEY_ID, "new-friendly-name");
    }

    @Test(expected = KeyNotFoundException.class)
    public void updateFriendlyNameUpdatingKeyFails() throws Exception {
        keyService.updateKeyFriendlyName(AUTH_KEY_ID, "new-friendly-name-update-fails");
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY", "SEND_AUTH_CERT_DEL_REQ" })
    public void deleteKey() throws Exception {
        keyService.deleteKey(AUTH_KEY_ID);
        verify(signerProxyFacade, times(1))
                .deleteKey(AUTH_KEY_ID, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(AUTH_KEY_ID, false);
        verify(signerProxyFacade, times(1))
                .setCertStatus(REGISTERED_AUTH_CERT_ID, CertificateInfo.STATUS_DELINPROG);
        verify(managementRequestSenderService, times(1))
                .sendAuthCertDeletionRequest(any());
        verifyNoMoreInteractions(signerProxyFacade);

        try {
            keyService.deleteKey(KEY_NOT_FOUND_KEY_ID);
            fail("should throw exception");
        } catch (KeyNotFoundException expected) {
        }

    }

    @Test(expected = AccessDeniedException.class)
    // missing SEND_AUTH_CERT_DEL_REQ
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY" })
    public void deleteKeyUnregisterRequiresSpecificPermission() throws Exception {
        keyService.deleteKey(AUTH_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "SEND_AUTH_CERT_DEL_REQ" })
    public void deleteAuthKeyPermissionCheck() throws Exception {
        try {
            keyService.deleteKey(SIGN_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        try {
            keyService.deleteKey(TYPELESS_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        keyService.deleteKey(AUTH_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_KEY" })
    public void deleteSignKeyPermissionCheck() throws Exception {
        try {
            keyService.deleteKey(AUTH_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        try {
            keyService.deleteKey(TYPELESS_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        keyService.deleteKey(SIGN_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_KEY" })
    public void deleteTypelessKeyPermissionCheck() throws Exception {
        try {
            keyService.deleteKey(AUTH_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        try {
            keyService.deleteKey(SIGN_KEY_ID);
            fail("should not be allowed");
        } catch (AccessDeniedException expected) {
        }
        keyService.deleteKey(TYPELESS_KEY_ID);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY" })
    public void deleteChecksPossibleActions() throws Exception {
        // prepare so that no actions are possible
        when(possibleActionsRuleEngine.getPossibleKeyActions(any(), any()))
                .thenReturn(EnumSet.noneOf(PossibleActionEnum.class));
        doThrow(new ActionNotPossibleException("")).when(possibleActionsRuleEngine)
                .requirePossibleKeyAction(eq(PossibleActionEnum.DELETE), any(), any());
        try {
            keyService.deleteKey(AUTH_KEY_ID);
            fail("should not be possible");
        } catch (ActionNotPossibleException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getPossibleActionsForKey() throws Exception {
        EnumSet<PossibleActionEnum> possibleActions = keyService.getPossibleActionsForKey(SIGN_KEY_ID);
        Set<PossibleActionEnum> allActions = new HashSet(Arrays.asList(PossibleActionEnum.values()));
        assertEquals(allActions, new HashSet<>(possibleActions));
    }

}
