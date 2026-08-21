package eu.xfsc.fc.core.service.trustframework;

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
   * Matches any all-digit, dot-separated host with 1 to 4 groups — e.g. {@code 127.1},
   * {@code 2130706433}, {@code 0177.0.0.1}. {@link InetAddress#getByName(String)} parses all of
   * these as numeric IPv4 literals (decimal / shorthand / legacy BSD {@code inet_aton} forms)
   * without any DNS lookup, so this pattern must be treated as an IP literal candidate — a
   * stricter dotted-quad-only check would let these known SSRF-filter-bypass encodings of
   * loopback/private/metadata addresses fall through to the "symbolic hostname, no DNS check"
   * path.
   */
  private static final Pattern NUMERIC_IPV4_CANDIDATE = Pattern.compile("[0-9]+(\\.[0-9]+){0,3}");

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
   * Checks whether a host string is an IP literal rather than a symbolic hostname.
   *
   * @param literal host string with any IPv6 brackets already stripped
   * @return {@code true} if the string parses as an IPv4 literal (dotted-quad or one of the
   *     decimal/shorthand alternate encodings, see {@link #NUMERIC_IPV4_CANDIDATE}) or an IPv6
   *     literal
   */
  private static boolean isIpLiteral(String literal) {
    return literal.contains(":") || NUMERIC_IPV4_CANDIDATE.matcher(literal).matches();
  }

  /**
   * Checks whether an IP literal falls in a reserved (non-public) range.
   *
   * @param literal IPv4 or IPv6 (unbracketed) address literal
   * @return {@code true} if the address is loopback, link-local, site-local/private,
   *     multicast, or the any-local/wildcard address
   */
  private static boolean isReservedIpLiteral(String literal) {
    try {
      InetAddress address = InetAddress.getByName(literal);
      return address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()
          || address.isMulticastAddress()
          || address.isAnyLocalAddress();
    } catch (UnknownHostException e) {
      // Not a parseable IP literal after all (e.g. malformed IPv6) — treat as unsafe.
      return true;
    }
  }
}
