/**
 * The MIT License
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

import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.AnchorFile;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.Anchor;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.niis.xroad.restapi.openapi.model.Version;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;
import org.niis.xroad.restapi.service.AnchorNotFoundException;
import org.niis.xroad.restapi.service.SystemService;
import org.niis.xroad.restapi.service.TimestampingServiceNotFoundException;
import org.niis.xroad.restapi.service.VersionService;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.util.TestUtils.ANCHOR_FILE;

/**
 * test system api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class SystemApiControllerTest {

    @MockBean
    private InternalTlsCertificateRepository mockRepository;

    @MockBean
    private VersionService versionService;

    @Autowired
    private SystemApiController systemApiController;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private SystemService systemService;

    private static final String TSA_1_URL = "https://tsa.com";

    private static final String TSA_1_NAME = "TSA 1";

    private static final String TSA_2_URL = "https://example.com";

    private static final String TSA_2_NAME = "TSA 2";

    private static final String ANCHOR_HASH =
            "CE2CA4FBBB67260F6CE97F9BCB73501F40432A1A2C4E5DA6F9F50DD1";

    private static final String ANCHOR_CREATED_AT = "2019-04-28T09:03:31.841Z";

    private static final Long ANCHOR_CREATED_AT_MILLIS = 1556442211841L;

    @Before
    public void setup() throws Exception {
        when(globalConfFacade.getInstanceIdentifier()).thenReturn("TEST");
        AnchorFile anchorFile = new AnchorFile(ANCHOR_HASH);
        anchorFile.setCreatedAt(new Date(ANCHOR_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));
        when(systemService.getAnchorFileFromBytes(any(), anyBoolean())).thenReturn(anchorFile);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_PROXY_INTERNAL_CERT" })
    public void getSystemCertificateWithViewProxyInternalCertPermission() throws Exception {
        getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_INTERNAL_SSL_CERT" })
    public void getSystemCertificateWithViewInternalSslCertPermission() throws Exception {
        getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_VERSION" })
    public void getVersion() throws Exception {
        String versionNumber = "6.24.0";
        given(versionService.getVersion()).willReturn(versionNumber);
        ResponseEntity<Version> response = systemApiController.systemVersion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(versionNumber, response.getBody().getInfo());
    }

    private void getSystemCertificate() throws IOException {
        X509Certificate x509Certificate = null;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("internal.crt")) {
            x509Certificate = CryptoUtils.readCertificate(stream);
        }
        given(mockRepository.getInternalTlsCertificate()).willReturn(x509Certificate);

        CertificateDetails certificate =
                systemApiController.getSystemCertificate().getBody();
        assertEquals("xroad2-lxd-ss1", certificate.getIssuerCommonName());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServices() {
        when(systemService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<>(
                Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME),
                        TestUtils.createTspType(TSA_2_URL, TSA_2_NAME))));

        ResponseEntity<List<TimestampingService>> response =
                systemApiController.getConfiguredTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(2, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServicesEmptyList() {
        when(systemService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<TspType>());

        ResponseEntity<List<TimestampingService>> response =
                systemApiController.getConfiguredTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(0, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addConfiguredTimestampingService() {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_2_URL, TSA_2_NAME);

        ResponseEntity<TimestampingService> response = systemApiController
                .addConfiguredTimestampingService(timestampingService);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(TSA_2_NAME, response.getBody().getName());
        assertEquals(TSA_2_URL, response.getBody().getUrl());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addDuplicateConfiguredTimestampingService() throws
            SystemService.DuplicateConfiguredTimestampingServiceException, TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        doThrow(new SystemService.DuplicateConfiguredTimestampingServiceException("")).when(systemService)
                .addConfiguredTimestampingService(any());

        try {
            ResponseEntity<TimestampingService> response = systemApiController
                    .addConfiguredTimestampingService(timestampingService);
            fail("should throw ConflictException");
        } catch (ConflictException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addNonExistingConfiguredTimestampingService() throws
            SystemService.DuplicateConfiguredTimestampingServiceException,
            TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils
                .createTimestampingService("http://dummy.com", "Dummy");

        doThrow(new TimestampingServiceNotFoundException("")).when(systemService)
                .addConfiguredTimestampingService(any());

        try {
            ResponseEntity<TimestampingService> response = systemApiController
                    .addConfiguredTimestampingService(timestampingService);
            fail("should throw ResourceNotFoundException");
        } catch (BadRequestException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteConfiguredTimestampingService() {
        ResponseEntity<Void> response = systemApiController
                .deleteConfiguredTimestampingService(TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteNonExistingConfiguredTimestampingService() throws TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        doThrow(new TimestampingServiceNotFoundException("")).when(systemService)
                .deleteConfiguredTimestampingService(any());

        try {
            ResponseEntity<Void> response = systemApiController
                    .deleteConfiguredTimestampingService(timestampingService);
            fail("should throw ResourceNotFoundException");
        } catch (BadRequestException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ANCHOR" })
    public void getAnchor() throws AnchorNotFoundException {
        AnchorFile anchorFile = new AnchorFile(ANCHOR_HASH);
        anchorFile.setCreatedAt(new Date(ANCHOR_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));
        when(systemService.getAnchorFile()).thenReturn(anchorFile);

        ResponseEntity<Anchor> response = systemApiController.getAnchor();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Anchor anchor = response.getBody();
        assertEquals(ANCHOR_HASH, anchor.getHash());
        assertEquals(ANCHOR_CREATED_AT, anchor.getCreatedAt().toString());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ANCHOR" })
    public void getAnchorNotFound() throws AnchorNotFoundException {
        doThrow(new AnchorNotFoundException("")).when(systemService).getAnchorFile();

        try {
            ResponseEntity<Anchor> response = systemApiController.getAnchor();
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DOWNLOAD_ANCHOR" })
    public void downloadAnchor() throws AnchorNotFoundException, IOException {
        byte[] bytes = "teststring".getBytes(StandardCharsets.UTF_8);
        when(systemService.readAnchorFile()).thenReturn(bytes);
        when(systemService.getAnchorFilenameForDownload())
                .thenReturn("configuration_anchor_UTC_2019-04-28_09_03_31.xml");

        ResponseEntity<Resource> response = systemApiController.downloadAnchor();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(bytes.length, response.getBody().contentLength());
    }

    @Test
    @WithMockUser(authorities = { "DOWNLOAD_ANCHOR" })
    public void downloadAnchorNotFound() throws AnchorNotFoundException {
        doThrow(new AnchorNotFoundException("")).when(systemService).readAnchorFile();

        try {
            ResponseEntity<Resource> response = systemApiController.downloadAnchor();
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "UPLOAD_ANCHOR" })
    public void replaceAnchor() throws IOException {
        Resource anchorResource = new ByteArrayResource(FileUtils.readFileToByteArray(ANCHOR_FILE));
        ResponseEntity<Void> response = systemApiController.replaceAnchor(anchorResource);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/system/anchor", response.getHeaders().getLocation().getPath());
    }

    @Test
    @WithMockUser(authorities = { "UPLOAD_ANCHOR" })
    public void previewAnchor() throws IOException {
        Resource anchorResource = new ByteArrayResource(FileUtils.readFileToByteArray(ANCHOR_FILE));
        ResponseEntity<Anchor> response = systemApiController.previewAnchor(true, anchorResource);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Anchor anchor = response.getBody();
        assertEquals(ANCHOR_HASH, anchor.getHash());
        assertEquals(ANCHOR_CREATED_AT, anchor.getCreatedAt().toString());
    }
}
