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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * Operations related to internal tls certificates
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class InternalTlsCertificateService {

    public static final String KEY_CERT_GENERATION_FAILED = "key_and_cert_generation_failed";
    public static final String IMPORT_INTERNAL_CERT_FAILED = "import_internal_cert_failed";

    private static final String CERT_PEM_FILENAME = "./cert.pem";
    private static final String CERT_CER_FILENAME = "./cert.cer";

    private final ExternalProcessRunner externalProcessRunner;
    private final String generateCertScriptArgs;

    @Setter
    private String generateCertScriptPath;
    @Setter
    private InternalTlsCertificateRepository internalTlsCertificateRepository;
    @Setter
    private String internalCertPath = SystemProperties.getConfPath() + InternalSSLKey.CRT_FILE_NAME;
    @Setter
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;
    @Setter
    private String internalKeystorePath = SystemProperties.getConfPath() + InternalSSLKey.KEY_FILE_NAME;

    @Autowired
    public InternalTlsCertificateService(InternalTlsCertificateRepository internalTlsCertificateRepository,
            ExternalProcessRunner externalProcessRunner,
            @Value("${script.generate-certificate.path}") String generateCertScriptPath,
            @Value("${script.generate-certificate.args}") String generateCertScriptArgs) {
        this.internalTlsCertificateRepository = internalTlsCertificateRepository;
        this.externalProcessRunner = externalProcessRunner;
        this.generateCertScriptPath = generateCertScriptPath;
        this.generateCertScriptArgs = generateCertScriptArgs;
    }

    public X509Certificate getInternalTlsCertificate() {
        return internalTlsCertificateRepository.getInternalTlsCertificate();
    }

    /**
     * Builds a tar.gz package which contains internal tls certificate as
     * two files:
     * - cert.pem PEM encoded certificate
     * - cert.cer DER encoded certificate
     * @return byte array that contains the exported certs.tar.gz
     */
    public byte[] exportInternalTlsCertificate() {
        X509Certificate certificate = internalTlsCertificateRepository.getInternalTlsCertificate();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (
                GzipCompressorOutputStream gzipCompressorOutputStream =
                        new GzipCompressorOutputStream(byteArrayOutputStream);
                BufferedOutputStream bufferedOutputStream =
                        new BufferedOutputStream(gzipCompressorOutputStream);
                TarArchiveOutputStream tarOutputStream =
                        new TarArchiveOutputStream(bufferedOutputStream)
        ) {
            ByteArrayOutputStream pemStream = new ByteArrayOutputStream();
            CryptoUtils.writeCertificatePem(certificate.getEncoded(), pemStream);
            writeFileToArchive(tarOutputStream, pemStream.toByteArray(), CERT_PEM_FILENAME);
            writeFileToArchive(tarOutputStream, certificate.getEncoded(), CERT_CER_FILENAME);

        } catch (IOException | CertificateEncodingException e) {
            log.error("writing certificate file failed", e);
            throw new RuntimeException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * create a file inside the tar container
     */
    private void writeFileToArchive(TarArchiveOutputStream tarOutputStream, byte[] fileBytes, String fileName)
            throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
        archiveEntry.setSize(fileBytes.length);
        tarOutputStream.putArchiveEntry(archiveEntry);
        tarOutputStream.write(fileBytes);
        tarOutputStream.closeArchiveEntry();
    }

    /**
     * Generates a new TLS key and certificate for internal use for the current Security Server. A runtime
     * exception will be thrown if the generation is interrupted or otherwise unable to be executed.
     * @throws InterruptedException if the thread running the key generator is interrupted. <b>The interrupted thread
     * has already been handled with so you can choose to ignore this exception if you so please.</b>
     */
    public void generateInternalTlsKeyAndCertificate() throws InterruptedException {
        try {
            externalProcessRunner.executeAndThrowOnFailure(generateCertScriptPath,
                    generateCertScriptArgs.split("\\s+"));
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            log.error("Failed to generate internal TLS key and cert", e);
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(KEY_CERT_GENERATION_FAILED));
        }
    }

    /**
     * Imports a new internal TLS certificate.
     * @param certificateBytes
     * @return X509Certificate
     * @throws InvalidCertificateException
     */
    public X509Certificate importInternalTlsCertificate(byte[] certificateBytes) throws InvalidCertificateException {
        X509Certificate x509Certificate = null;
        try {
            x509Certificate = CryptoUtils.readCertificate(certificateBytes);
        } catch (Exception e) {
            throw new InvalidCertificateException("cannot convert bytes to certificate", e);
        }
        try {
            CertUtils.writePemToFile(certificateBytes, internalCertPath);
            CertUtils.createPkcs12(internalKeyPath, internalCertPath, internalKeystorePath);
        } catch (Exception e) {
            throw new DeviationAwareRuntimeException("cannot import internal tls cert", e,
                    new ErrorDeviation(IMPORT_INTERNAL_CERT_FAILED));
        }
        return x509Certificate;
    }
}
