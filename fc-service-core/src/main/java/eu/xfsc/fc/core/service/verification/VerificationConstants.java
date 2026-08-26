package eu.xfsc.fc.core.service.verification;

/**
 * Shared string constants for the verification pipeline.
 */
public final class VerificationConstants {

  public static final String JWT_PREFIX = "eyJ";

  public static final String VC_20_CONTEXT = "https://www.w3.org/ns/credentials/v2";

  // Note: the Gaia-X 2511 context URL (https://w3id.org/gaia-x/2511#) is intentionally absent here.
  // Loire format detection is based on JWT structure (typ header + top-level @context presence),
  // not on specific vocabulary namespaces. The 2511 URL is Gaia-X domain vocabulary — any
  // trust framework could use the Loire JWT format without it.

  public static final String RDF_CONTEXT_KEY = "@context";

  public static final String DATA_URI_PREFIX = "data:";

  // W3C Verifiable Credentials Data Model type strings
  public static final String VP_TYPE = "VerifiablePresentation";
  public static final String VC_TYPE = "VerifiableCredential";
  public static final String EVC_TYPE = "EnvelopedVerifiableCredential";
  public static final String EVP_TYPE = "EnvelopedVerifiablePresentation";

  public static final String VERIFIABLE_CREDENTIAL_KEY = "verifiableCredential";

  /**
   * Property URI used to annotate graph claims with their source credential subject IRI.
   */
  public static final String GAIAX_CLAIMS_GRAPH_URI = "https://w3id.org/gaia-x/2511#claimsGraphUri";

  private VerificationConstants() {
  }
}
