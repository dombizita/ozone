/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.security.x509.certificate.authority;

import static org.apache.hadoop.hdds.HddsConfigKeys.OZONE_METADATA_DIRS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.security.SecurityConfig;
import org.apache.hadoop.hdds.security.exception.SCMSecurityException;
import org.apache.hadoop.hdds.security.x509.certificate.authority.profile.DefaultProfile;
import org.apache.hadoop.hdds.security.x509.certificate.utils.CertificateSignRequest;
import org.apache.hadoop.hdds.security.x509.keys.HDDSKeyGenerator;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the default PKI Profile.
 */
public class TestDefaultProfile {
  private SecurityConfig securityConfig;
  private DefaultProfile defaultProfile;
  private DefaultApprover approver;
  private KeyPair keyPair;

  @BeforeEach
  public void setUp(@TempDir Path tempDir) throws Exception {
    OzoneConfiguration configuration = new OzoneConfiguration();
    configuration.set(OZONE_METADATA_DIRS, tempDir.toString());
    securityConfig = new SecurityConfig(configuration);
    defaultProfile = new DefaultProfile();
    approver = new DefaultApprover(defaultProfile, securityConfig);
    keyPair = new HDDSKeyGenerator(securityConfig).generateKey();
  }

  /**
   * Tests the General Names that we support. The default profile supports only
   * two names right now.
   */
  @Test
  public void testisSupportedGeneralName() {
// Positive tests
    assertTrue(defaultProfile.isSupportedGeneralName(GeneralName.iPAddress));
    assertTrue(defaultProfile.isSupportedGeneralName(GeneralName.dNSName));
    assertTrue(defaultProfile.isSupportedGeneralName(GeneralName.otherName));
// Negative Tests
    assertFalse(defaultProfile.isSupportedGeneralName(
        GeneralName.directoryName));
    assertFalse(defaultProfile.isSupportedGeneralName(GeneralName.rfc822Name));
  }

  /**
   * Test valid keys are validated correctly.
   */
  @Test
  public void testVerifyCertificate() throws Exception {
    //TODO: generateCSR!
    PKCS10CertificationRequest csr = new CertificateSignRequest.Builder()
        .addDnsName("hadoop.apache.org")
        .addIpAddress("8.8.8.8")
        .addServiceName("OzoneMarketingCluster001")
        .setCA(false)
        .setClusterID("ClusterID")
        .setScmID("SCMID")
        .setSubject("Ozone Cluster")
        .setConfiguration(securityConfig)
        .setKey(keyPair)
        .build()
        .generateCSR();
    assertTrue(approver.verifyPkcs10Request(csr));
  }




  /**
   * Test invalid keys fail in the validation.
   */
  @Test
  public void testVerifyCertificateInvalidKeys() throws Exception {
    KeyPair newKeyPair = new HDDSKeyGenerator(securityConfig).generateKey();
    KeyPair wrongKey = new KeyPair(keyPair.getPublic(),
        newKeyPair.getPrivate());
    //TODO: generateCSR!
    PKCS10CertificationRequest csr = new CertificateSignRequest.Builder()
        .addDnsName("hadoop.apache.org")
        .addIpAddress("8.8.8.8")
        .setCA(false)
        .setClusterID("ClusterID")
        .setScmID("SCMID")
        .setSubject("Ozone Cluster")
        .setConfiguration(securityConfig)
        .setKey(wrongKey)
        .build()
        .generateCSR();
    // Signature verification should fail here, since the public/private key
    // does not match.
    assertFalse(approver.verifyPkcs10Request(csr));
  }

  /**
   * Tests that normal valid extensions work with the default profile.
   */
  @Test
  public void testExtensions() throws Exception {
    //TODO: generateCSR!
    PKCS10CertificationRequest csr = new CertificateSignRequest.Builder()
        .addDnsName("hadoop.apache.org")
        .addIpAddress("192.10.234.6")
        .setCA(false)
        .setClusterID("ClusterID")
        .setScmID("SCMID")
        .setSubject("Ozone Cluster")
        .setConfiguration(securityConfig)
        .setKey(keyPair)
        .build()
        .generateCSR();
    assertTrue(approver.verfiyExtensions(csr));
  }

  /**
   * Tests that  invalid extensions cause a failure in validation. We will fail
   * if CA extension is enabled.
   *
   * @throws SCMSecurityException - on Error.
   */

  @Test
  public void testInvalidExtensionsWithCA() throws Exception {
    //TODO: generateCSR!
    PKCS10CertificationRequest csr = new CertificateSignRequest.Builder()
        .addDnsName("hadoop.apache.org")
        .addIpAddress("192.10.234.6")
        .setCA(true)
        .setClusterID("ClusterID")
        .setScmID("SCMID")
        .setSubject("Ozone Cluster")
        .setConfiguration(securityConfig)
        .setKey(keyPair)
        .build()
        .generateCSR();
    assertFalse(approver.verfiyExtensions(csr));
  }

  /**
   * Tests that  invalid extensions cause a failure in validation. We will fail
   * if rfc222 type names are added, we also add the extension as both
   * critical and non-critical fashion to verify that the we catch both cases.
   *
   * @throws SCMSecurityException - on Error.
   */

  @Test
  public void testInvalidExtensionsWithEmail()
      throws IOException, OperatorCreationException {
    Extensions emailExtension = getSANExtension(GeneralName.rfc822Name,
        "bilbo@apache.org", false);
    PKCS10CertificationRequest csr = getInvalidCSR(keyPair, emailExtension);
    assertFalse(approver.verfiyExtensions(csr));

    emailExtension = getSANExtension(GeneralName.rfc822Name, "bilbo" +
        "@apache.org", true);
    csr = getInvalidCSR(keyPair, emailExtension);
    assertFalse(approver.verfiyExtensions(csr));

  }

  /**
   * Same test for URI.
   * @throws IOException - On Error.
   * @throws OperatorCreationException- on Error.
   */
  @Test
  public void testInvalidExtensionsWithURI() throws IOException,
      OperatorCreationException {
    Extensions oExtension = getSANExtension(
        GeneralName.uniformResourceIdentifier, "s3g.ozone.org", false);
    PKCS10CertificationRequest csr = getInvalidCSR(keyPair, oExtension);
    assertFalse(approver.verfiyExtensions(csr));
    oExtension = getSANExtension(GeneralName.uniformResourceIdentifier,
        "s3g.ozone.org", false);
    csr = getInvalidCSR(keyPair, oExtension);
    assertFalse(approver.verfiyExtensions(csr));
  }

  /**
   * Assert that if DNS is marked critical our PKI profile will reject it.
   * @throws IOException - on Error.
   * @throws OperatorCreationException - on Error.
   */
  @Test
  public void testInvalidExtensionsWithCriticalDNS() throws IOException,
      OperatorCreationException {
    Extensions dnsExtension = getSANExtension(GeneralName.dNSName,
        "ozone.hadoop.org",
        true);
    PKCS10CertificationRequest csr = getInvalidCSR(keyPair, dnsExtension);
    assertFalse(approver.verfiyExtensions(csr));
    // This tests should pass, hence the assertTrue
    dnsExtension = getSANExtension(GeneralName.dNSName,
        "ozone.hadoop.org",
        false);
    csr = getInvalidCSR(keyPair, dnsExtension);
    assertTrue(approver.verfiyExtensions(csr));
  }


  /**
   * Verify that valid Extended Key usage works as expected.
   * @throws IOException - on Error.
   * @throws OperatorCreationException - on Error.
   */
  @Test
  public void testValidExtendedKeyUsage() throws IOException,
      OperatorCreationException {
    Extensions extendedExtension =
        getKeyUsageExtension(KeyPurposeId.id_kp_clientAuth, false);
    PKCS10CertificationRequest csr = getInvalidCSR(keyPair, extendedExtension);
    assertTrue(approver.verfiyExtensions(csr));

    extendedExtension = getKeyUsageExtension(KeyPurposeId.id_kp_serverAuth,
        false);
    csr = getInvalidCSR(keyPair, extendedExtension);
    assertTrue(approver.verfiyExtensions(csr));
  }


  /**
   * Verify that Invalid Extended Key usage works as expected, that is rejected.
   * @throws IOException - on Error.
   * @throws OperatorCreationException - on Error.
   */
  @Test
  public void testInValidExtendedKeyUsage() throws IOException,
      OperatorCreationException {
    Extensions extendedExtension =
        getKeyUsageExtension(KeyPurposeId.id_kp_clientAuth, true);
    PKCS10CertificationRequest csr = getInvalidCSR(keyPair, extendedExtension);
    assertFalse(approver.verfiyExtensions(csr));

    extendedExtension = getKeyUsageExtension(KeyPurposeId.id_kp_OCSPSigning,
        false);
    csr = getInvalidCSR(keyPair, extendedExtension);
    assertFalse(approver.verfiyExtensions(csr));
  }

  /**
   * Generates an CSR with the extension specified.
   * This function is used to get an Invalid CSR and test that PKI profile
   * rejects these invalid extensions, Hence the function name, by itself it
   * is a well formed CSR, but our PKI profile will treat it as invalid CSR.
   *
   * @param kPair - Key Pair.
   * @return CSR  - PKCS10CertificationRequest
   * @throws OperatorCreationException - on Error.
   */
  private PKCS10CertificationRequest getInvalidCSR(KeyPair kPair,
      Extensions extensions) throws OperatorCreationException {
    X500NameBuilder namebuilder =
        new X500NameBuilder(X500Name.getDefaultStyle());
    namebuilder.addRDN(BCStyle.CN, "invalidCert");
    PKCS10CertificationRequestBuilder p10Builder =
        new JcaPKCS10CertificationRequestBuilder(namebuilder.build(),
            keyPair.getPublic());
    p10Builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest,
        extensions);
    JcaContentSignerBuilder csBuilder =
        new JcaContentSignerBuilder(this.securityConfig.getSignatureAlgo());
    ContentSigner signer = csBuilder.build(keyPair.getPrivate());
    return p10Builder.build(signer);
  }

  /**
   * Generate an Extension with rfc822Name.
   * @param extensionCode - Extension Code.
   * @param value  - email to be added to the certificate
   * @param critical - boolean value that marks the extension as critical.
   * @return - An Extension list with email address.
   * @throws IOException
   */
  private Extensions getSANExtension(int extensionCode, String value,
      boolean critical) throws IOException {
    GeneralName extn = new GeneralName(extensionCode,
        value);
    ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
    extensionsGenerator.addExtension(Extension.subjectAlternativeName, critical,
        new GeneralNames(extn));
    return extensionsGenerator.generate();
  }

  /**
   * Returns a extension with Extended Key usage.
   * @param purposeId - Usage that we want to encode.
   * @param critical -  makes the extension critical.
   * @return Extensions.
   */
  private Extensions getKeyUsageExtension(KeyPurposeId purposeId,
      boolean critical) throws IOException {
    ExtendedKeyUsage extendedKeyUsage = new ExtendedKeyUsage(purposeId);
    ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
    extensionsGenerator.addExtension(
        Extension.extendedKeyUsage, critical, extendedKeyUsage);
    return extensionsGenerator.generate();
  }
}
