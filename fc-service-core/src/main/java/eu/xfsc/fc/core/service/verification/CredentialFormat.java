package eu.xfsc.fc.core.service.verification;

/**
 * Identifies the credential format so the verification pipeline can route
 * to the correct parser path.
 *
 * <ul>
 *   <li>{@link #GAIAX_V1_TAGUS} — VC 1.1 with Linked Data Proof (Gaia-X Tagus / ICAM 22.10)</li>
 *   <li>{@link #GAIAX_V2_LOIRE} — VC 2.0 JWT per VC-JOSE-COSE (Gaia-X Loire / ICAM 24.07)</li>
 *   <li>{@link #VC2_DANUBETECH} — VC 2.0 JWT with {@code vc}/{@code vp} wrapper claims (danubetech-style)</li>
 *   <li>{@link #UNKNOWN} — format not recognized; should be rejected with a diagnostic message</li>
 * </ul>
 */
public enum CredentialFormat {
  GAIAX_V1_TAGUS,
  GAIAX_V2_LOIRE,
  VC2_DANUBETECH,
  UNKNOWN
}
