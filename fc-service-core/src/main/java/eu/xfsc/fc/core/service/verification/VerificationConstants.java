package eu.xfsc.fc.core.service.verification;

/**
 * Shared string constants for the verification pipeline.
 */
public final class VerificationConstants {

  public static final String JWT_PREFIX = "eyJ";

  public static final String VC_11_CONTEXT = "https://www.w3.org/2018/credentials/v1";
  public static final String VC_20_CONTEXT = "https://www.w3.org/ns/credentials/v2";

  // Note: the Gaia-X 2511 context URL (https://w3id.org/gaia-x/2511#) is intentionally absent here.
  // Loire format detection is based on JWT structure (typ header + top-level @context presence),
  // not on specific vocabulary namespaces. The 2511 URL is Gaia-X domain vocabulary — any
  // trust framework could use the Loire JWT format without it.

  public static final String RDF_CONTEXT_KEY = "@context";

  private VerificationConstants() {
  }
}
