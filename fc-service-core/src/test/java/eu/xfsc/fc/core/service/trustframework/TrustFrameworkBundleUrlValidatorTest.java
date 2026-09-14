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
  private static final String LOCALHOST_HOSTNAME_TRAILING_DOT_URL = "https://localhost./x";
  private static final String UNIQUE_LOCAL_IPV6_RFC4193_URL = "https://[fd00::1]/x";
  private static final String IPV4_COMPATIBLE_IPV6_LOOPBACK_URL = "https://[::127.0.0.1]/x";
  private static final String LEADING_ZERO_OCTET_IPV4_URL = "https://0177.0.0.1/x";
  private static final String HEX_PREFIXED_IPV4_URL = "https://0x7f000001/x";
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

  /**
   * A trailing "." is a valid way to write a fully-qualified name; a resolver strips it before
   * lookup, so {@code localhost.} must be recognized as {@code localhost}, not fall through to
   * the "symbolic hostname, no DNS check" path.
   */
  @Test
  void isAllowed_localhostHostnameWithTrailingDot_returnsFalse() {
    boolean result = validator.isAllowed(LOCALHOST_HOSTNAME_TRAILING_DOT_URL);

    assertFalse(result);
  }

  /**
   * {@code fc00::/7} (RFC 4193) is the unique local address range in current use — e.g.
   * dual-stack Kubernetes — unlike the deprecated {@code fec0::/10} predecessor that
   * {@link java.net.Inet6Address#isSiteLocalAddress()} recognizes.
   */
  @Test
  void isAllowed_uniqueLocalIpv6Rfc4193Literal_returnsFalse() {
    boolean result = validator.isAllowed(UNIQUE_LOCAL_IPV6_RFC4193_URL);

    assertFalse(result);
  }

  /**
   * The deprecated IPv4-compatible IPv6 form ({@code ::a.b.c.d}) embeds an IPv4 address that
   * none of {@link java.net.Inet6Address}'s own reserved-range predicates recognize.
   */
  @Test
  void isAllowed_ipv4CompatibleIpv6LoopbackLiteral_returnsFalse() {
    boolean result = validator.isAllowed(IPV4_COMPATIBLE_IPV6_LOOPBACK_URL);

    assertFalse(result);
  }

  /**
   * On this JDK (21), a {@code 0}-prefixed IPv4 octet is no longer parsed as octal — the leading
   * zero is simply dropped, so this resolves to the public {@code 177.0.0.1}. It is rejected
   * anyway as a defense against a downstream resolver or proxy that still applies octal
   * semantics to a re-parsed URL.
   */
  @Test
  void isAllowed_leadingZeroOctetIpv4Literal_returnsFalse() {
    boolean result = validator.isAllowed(LEADING_ZERO_OCTET_IPV4_URL);

    assertFalse(result);
  }

  /**
   * A {@code 0x}-prefixed IPv4 literal fails to resolve on this JDK, but must still be recognized
   * as an IP-literal candidate and rejected outright — not treated as an unresolvable symbolic
   * hostname that is accepted without a DNS check.
   */
  @Test
  void isAllowed_hexPrefixedIpv4Literal_returnsFalse() {
    boolean result = validator.isAllowed(HEX_PREFIXED_IPV4_URL);

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
