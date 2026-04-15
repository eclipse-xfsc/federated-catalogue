package eu.xfsc.fc.core.service.verification;

/**
 * Identifies the credential format so the verification pipeline can route
 * to the correct parser path.
 *
 * <ul>
 *   <li>{@link #GAIAX_V2_LOIRE} — VC 2.0 JWT per VC-JOSE-COSE (Gaia-X Loire / ICAM 24.07)</li>
 *   <li>{@link #VC2_DANUBETECH} — VC 2.0 JWT with {@code vc}/{@code vp} wrapper claims (danubetech-style, NOT Gaia-X Danube!)</li>
 *   <li>{@link #UNKNOWN} — format not recognized; should be rejected with a diagnostic message</li>
 * </ul>
 */
public enum CredentialFormat {
  GAIAX_V2_LOIRE,
  VC2_DANUBETECH, // Note: Not Gaia-x! Don't confuse danubetech with the Gaia-X Danube release, which uses Loire-format JWTs (without wrapper claims)!
  UNKNOWN
}
