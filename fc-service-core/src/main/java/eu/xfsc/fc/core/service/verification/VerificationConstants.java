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
   * Gaia-X 2511 role names as declared in the gaia-x-2511 bundle's {@code framework.yaml}.
   *
   * <p>These are dispatch keys, not new Gaia-X coupling: the typed verification endpoints
   * ({@link VerificationService#verifyParticipantCredential} etc.) and the typed result
   * POJOs ({@code CredentialVerificationResultParticipant}, {@code ...Offering},
   * {@code ...Resource}) are already Gaia-X-specific. The constants just centralise the
   * role strings used to map between them.
   *
   * <p>Eliminating Gaia-X from this layer requires making the typed endpoints and result
   * POJOs framework-agnostic (or letting each trust-framework bundle declare
   * {@code role → resultType} in its metadata and dispatching from that). Until then,
   * extracting these strings as constants is strictly better than inlining them.
   */
  public static final String ROLE_PARTICIPANT = "Participant";
  public static final String ROLE_SERVICE_OFFERING = "ServiceOffering";
  public static final String ROLE_RESOURCE = "Resource";

  /**
   * Property URI used to annotate graph claims with their source credential subject IRI.
   */
  public static final String GAIAX_CLAIMS_GRAPH_URI = "https://w3id.org/gaia-x/2511#claimsGraphUri";

  // W3C VC-JOSE-COSE media types (IANA-registered)
  public static final String MEDIA_TYPE_VC_JWT = "application/vc+jwt";
  public static final String MEDIA_TYPE_VP_JWT = "application/vp+jwt";
  public static final String MEDIA_TYPE_VC_LD_JSON = "application/vc+ld+json";
  public static final String MEDIA_TYPE_VP_LD_JSON = "application/vp+ld+json";
  public static final String MEDIA_TYPE_LD_JSON = "application/ld+json";

    // RDF format media types
    public static final String MEDIA_TYPE_TURTLE = "text/turtle";
    public static final String MEDIA_TYPE_NTRIPLES = "application/n-triples";
    public static final String MEDIA_TYPE_RDF_XML = "application/rdf+xml";

  private VerificationConstants() {
  }
}
