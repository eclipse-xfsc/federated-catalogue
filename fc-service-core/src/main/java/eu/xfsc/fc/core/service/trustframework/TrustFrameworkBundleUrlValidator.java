package eu.xfsc.fc.core.service.trustframework;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Validates external client identifier URLs (a bundle's {@code serviceUrl} and
 * {@code trustAnchorUrl} overrides) accepted through the admin bundle-config patch endpoint.
 *
 * <p>An admin PATCH lets an operator repoint the outbound compliance client at an arbitrary
 * URL at runtime. Without a scheme/host allowlist this is a server-side request forgery
 * (SSRF) hole: a malicious or hijacked admin session (or CSRF) could aim the catalogue's
 * outbound HTTP client at an internal host or the cloud-metadata address
 * ({@code 169.254.169.254}), and the catalogue would dial it. This validator rejects such
 * targets before they are ever persisted as an override.
 *
 * <p><strong>Scope limitation:</strong> this is a syntactic scheme + reserved-IP-literal
 * check, not a DNS-rebinding-proof network guard. Symbolic hostnames (anything that is not
 * an IP literal) are accepted without any DNS lookup — resolving admin-supplied hostnames at
 * validation time would only close a time-of-check gap, since the resolved address can
 * legitimately change before the outbound call is made (DNS rebinding); a syntactic check
 * cannot close that gap either way, so we do not attempt it here. Operators are expected to
 * only enter hostnames they trust; enforcing the destination at connect time (e.g. an
 * egress proxy or network policy) is the actual defense against DNS rebinding.
 */
@Component
public class TrustFrameworkBundleUrlValidator {

  private static final String HTTPS_SCHEME = "https";
  private static final String LOCALHOST_HOSTNAME = "localhost";

  /**
   * Matches a dot-separated host with 1 to 4 groups where each group is plain decimal or one of
   * the legacy octal ({@code 0}-prefixed) / hex ({@code 0x}-prefixed) forms — e.g. {@code 127.1},
   * {@code 2130706433}, {@code 0177.0.0.1}, {@code 0x7f000001}. On this JDK (21),
   * {@link InetAddress#getByName(String)} parses the decimal/shorthand forms as numeric IPv4
   * literals without any DNS lookup, but no longer applies legacy {@code inet_aton} semantics to
   * the octal/hex forms: a leading zero is simply dropped (so {@code 0177.0.0.1} reads as decimal
   * {@code 177.0.0.1}, not octal), and a {@code 0x}-prefixed group fails to resolve outright. A
   * stricter decimal-only pattern would miss the hex form entirely, letting it fall through to
   * the "symbolic hostname, no DNS check" path instead of being recognized as an IP-literal
   * candidate; {@link #isReservedIpLiteral(String)} rejects any group written in one of these
   * ambiguous forms outright, since a downstream resolver or proxy that re-parses the URL might
   * still apply the legacy octal/hex semantics this JDK does not.
   */
  private static final Pattern IPV4_LITERAL_CANDIDATE =
      Pattern.compile("(0[xX][0-9a-fA-F]+|[0-9]+)(\\.(0[xX][0-9a-fA-F]+|[0-9]+)){0,3}");

  /**
   * Matches a single IPv4 group written in one of the ambiguous legacy forms — {@code 0}-prefixed
   * (historically octal) or {@code 0x}-prefixed (hex) — that this JDK no longer parses as such but
   * a different resolver or proxy might. See {@link #IPV4_LITERAL_CANDIDATE}.
   */
  private static final Pattern AMBIGUOUS_IPV4_OCTET = Pattern.compile("0[xX][0-9a-fA-F]+|0[0-9]+");

  /**
   * Checks whether a candidate bundle-config URL is safe to accept.
   *
   * @param url candidate value for a bundle's {@code serviceUrl} or {@code trustAnchorUrl} override
   * @return {@code true} if the URL is an https URL with a public, non-reserved host
   */
  public boolean isAllowed(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }

    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      return false;
    }

    if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
      return false;
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return false;
    }
    host = stripTrailingDot(host);
    if (LOCALHOST_HOSTNAME.equalsIgnoreCase(host)) {
      return false;
    }

    String literal = stripIpv6Brackets(host);
    if (isIpLiteral(literal)) {
      return !isReservedIpLiteral(literal);
    }

    // Symbolic hostname: accepted without DNS resolution — see class Javadoc.
    return true;
  }

  private static String stripIpv6Brackets(String host) {
    if (host.length() >= 2 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
      return host.substring(1, host.length() - 1);
    }
    return host;
  }

  /**
   * Strips a single trailing "." — the root-label terminator of a fully-qualified domain name,
   * e.g. {@code localhost.} — so it compares equal to its unqualified form. A resolver strips
   * this internally before lookup; this validator must not fail to recognize it and let a
   * trivially disguised loopback/reserved hostname fall through to the "symbolic hostname, no
   * DNS check" path.
   */
  private static String stripTrailingDot(String host) {
    if (host.length() > 1 && host.charAt(host.length() - 1) == '.') {
      return host.substring(0, host.length() - 1);
    }
    return host;
  }

  /**
   * Checks whether a host string is an IP literal rather than a symbolic hostname.
   *
   * @param literal host string with any IPv6 brackets already stripped
   * @return {@code true} if the string parses as an IPv4 literal (dotted-quad or one of the
   *     decimal/shorthand/ambiguous alternate encodings, see {@link #IPV4_LITERAL_CANDIDATE}) or
   *     an IPv6 literal
   */
  private static boolean isIpLiteral(String literal) {
    return literal.contains(":") || IPV4_LITERAL_CANDIDATE.matcher(literal).matches();
  }

  /**
   * Checks whether an IP literal falls in a reserved (non-public) range.
   *
   * @param literal IPv4 or IPv6 (unbracketed) address literal
   * @return {@code true} if the address is loopback, link-local, site-local/private, unique-local
   *     (IPv6 {@code fc00::/7}), multicast, the any-local/wildcard address, an IPv4-mapped or
   *     IPv4-compatible IPv6 literal embedding a reserved IPv4 address, or a group is written in
   *     an ambiguous legacy octal/hex form (see {@link #AMBIGUOUS_IPV4_OCTET})
   */
  private static boolean isReservedIpLiteral(String literal) {
    if (hasAmbiguousIpv4Octet(literal)) {
      return true;
    }

    try {
      InetAddress address = InetAddress.getByName(literal);
      if (address instanceof Inet6Address ipv6Address) {
        InetAddress embeddedIpv4 = extractEmbeddedIpv4(ipv6Address);
        if (embeddedIpv4 != null && isReservedAddress(embeddedIpv4)) {
          return true;
        }
        if (isUniqueLocalIpv6(ipv6Address)) {
          return true;
        }
      }
      return isReservedAddress(address);
    } catch (UnknownHostException e) {
      // Not a parseable IP literal after all (e.g. malformed IPv6) — treat as unsafe.
      return true;
    }
  }

  private static boolean isReservedAddress(InetAddress address) {
    return address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || address.isAnyLocalAddress();
  }

  private static boolean hasAmbiguousIpv4Octet(String literal) {
    for (String group : literal.split("\\.", -1)) {
      if (AMBIGUOUS_IPV4_OCTET.matcher(group).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether an IPv6 literal falls in the unique local address range ({@code fc00::/7},
   * RFC 4193) — e.g. dual-stack Kubernetes pod networks. {@link Inet6Address#isSiteLocalAddress()}
   * only recognizes the deprecated {@code fec0::/10} predecessor and returns {@code false} here.
   */
  private static boolean isUniqueLocalIpv6(Inet6Address address) {
    return (address.getAddress()[0] & 0xFE) == 0xFC;
  }

  /**
   * Extracts the embedded IPv4 address from an IPv4-mapped ({@code ::ffff:a.b.c.d}) or the
   * deprecated IPv4-compatible ({@code ::a.b.c.d}) IPv6 literal, so its reserved-range status can
   * be checked directly — {@link Inet6Address}'s own predicates do not recognize either form.
   *
   * @return the embedded IPv4 address, or {@code null} if {@code address} is neither form
   */
  private static InetAddress extractEmbeddedIpv4(Inet6Address address) {
    byte[] bytes = address.getAddress();
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return null;
      }
    }
    boolean ipv4Mapped = (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    boolean ipv4Compatible = bytes[10] == 0 && bytes[11] == 0;
    if (!ipv4Mapped && !ipv4Compatible) {
      return null;
    }

    try {
      return InetAddress.getByAddress(new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] });
    } catch (UnknownHostException e) {
      // Unreachable: a 4-byte address is always a valid IPv4 literal.
      return null;
    }
  }
}
