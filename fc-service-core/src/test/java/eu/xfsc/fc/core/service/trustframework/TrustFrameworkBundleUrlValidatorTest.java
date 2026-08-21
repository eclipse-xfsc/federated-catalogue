package eu.xfsc.fc.core.service.trustframework;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Red-state unit tests for {@link TrustFrameworkBundleUrlValidator}. The production stub's
 * {@code isAllowed()} currently throws {@link UnsupportedOperationException} — every test in
 * this class is expected to fail with that error until a second pass implements the real
 * scheme/host allowlist.
 */
class TrustFrameworkBundleUrlValidatorTest {

  private static final String PUBLIC_HTTPS_URL = "https://compliance.example.org/v2";
  private static final String SYMBOLIC_SERVICE_URL = "https://mock.test/v2";
  private static final String SYMBOLIC_TRUST_ANCHOR_URL = "https://registry.test/v1/trust-anchors";
  private static final String LOOPBACK_IPV4_URL = "https://127.0.0.1/x";
  private static final String LOOPBACK_IPV6_URL = "https://[::1]/x";
  private static final String PRIVATE_IPV4_10_URL = "https://10.0.0.5/x";
  private static final String PRIVATE_IPV4_172_URL = "https://172.16.0.5/x";
  private static final String PRIVATE_IPV4_192_URL = "https://192.168.1.5/x";
  private static final String METADATA_IPV4_URL = "https://169.254.169.254/latest/meta-data/";
  private static final String LOCALHOST_HOSTNAME_URL = "https://localhost/x";
  private static final String MALFORMED_URL = "not a url";

  private TrustFrameworkBundleUrlValidator validator;

  @BeforeEach
  void setUp() {
    validator = new TrustFrameworkBundleUrlValidator();
  }

  // --- isAllowed: allowed cases ---

  @Test
  void isAllowed_publicHttpsUrlWithHostname_returnsTrue() {
    boolean result = validator.isAllowed(PUBLIC_HTTPS_URL);

    assertTrue(result);
  }

  /**
   * Symbolic test-only hostnames (RFC 2606 style, e.g. {@code mock.test}) must be accepted
   * without any DNS resolution attempt — CI has no network access and these hostnames will
   * never resolve. These exact strings are already used as example override values in
   * {@code TrustFrameworkAdminControllerTest}; if the validator ever resolves hostnames to
   * check for private IPs, those existing green tests will start failing.
   */
  @Test
  void isAllowed_symbolicServiceUrlHostname_returnsTrueWithoutDnsResolution() {
    boolean result = validator.isAllowed(SYMBOLIC_SERVICE_URL);

    assertTrue(result);
  }

  @Test
  void isAllowed_symbolicTrustAnchorUrlHostname_returnsTrueWithoutDnsResolution() {
    boolean result = validator.isAllowed(SYMBOLIC_TRUST_ANCHOR_URL);

    assertTrue(result);
  }

  // --- isAllowed: disallowed scheme ---

  @Test
  void isAllowed_httpScheme_returnsFalse() {
    boolean result = validator.isAllowed("http://compliance.example.org/v2");

    assertFalse(result);
  }

  @Test
  void isAllowed_fileScheme_returnsFalse() {
    boolean result = validator.isAllowed("file:///etc/passwd");

    assertFalse(result);
  }

  @Test
  void isAllowed_ftpScheme_returnsFalse() {
    boolean result = validator.isAllowed("ftp://compliance.example.org/v2");

    assertFalse(result);
  }

  // --- isAllowed: reserved / internal host literals ---

  @Test
  void isAllowed_loopbackIpv4Literal_returnsFalse() {
    boolean result = validator.isAllowed(LOOPBACK_IPV4_URL);

    assertFalse(result);
  }

  /**
   * On this JDK (Temurin 21.0.10), {@code new URI("https://[::1]/x").getHost()} returns
   * {@code "[::1]"} — brackets included, not stripped. Any implementation must strip the
   * brackets (or otherwise recognize the bracketed form) before checking loopback ranges.
   */
  @Test
  void isAllowed_loopbackIpv6Literal_returnsFalse() {
    boolean result = validator.isAllowed(LOOPBACK_IPV6_URL);

    assertFalse(result);
  }

  @Test
  void isAllowed_privateIpv4Rfc1918TenDotRange_returnsFalse() {
    boolean result = validator.isAllowed(PRIVATE_IPV4_10_URL);

    assertFalse(result);
  }

  @Test
  void isAllowed_privateIpv4Rfc1918SeventeenTwoRange_returnsFalse() {
    boolean result = validator.isAllowed(PRIVATE_IPV4_172_URL);

    assertFalse(result);
  }

  @Test
  void isAllowed_privateIpv4Rfc1918OneNineTwoRange_returnsFalse() {
    boolean result = validator.isAllowed(PRIVATE_IPV4_192_URL);

    assertFalse(result);
  }

  @Test
  void isAllowed_linkLocalCloudMetadataIpv4Literal_returnsFalse() {
    boolean result = validator.isAllowed(METADATA_IPV4_URL);

    assertFalse(result);
  }

  @Test
  void isAllowed_localhostHostnameLiteral_returnsFalse() {
    boolean result = validator.isAllowed(LOCALHOST_HOSTNAME_URL);

    assertFalse(result);
  }

  // --- isAllowed: malformed / absent input ---

  @Test
  void isAllowed_malformedString_returnsFalse() {
    boolean result = validator.isAllowed(MALFORMED_URL);

    assertFalse(result);
  }

  @Test
  void isAllowed_null_returnsFalse() {
    boolean result = validator.isAllowed(null);

    assertFalse(result);
  }

  @Test
  void isAllowed_blank_returnsFalse() {
    boolean result = validator.isAllowed("");

    assertFalse(result);
  }
}
