package eu.xfsc.fc.core.service.trustframework;

/**
 * Carries the resolved trust-framework role for a credential subject type.
 * Use {@link #UNKNOWN} when no framework claims the type.
 */
public record ResolvedRole(String frameworkProfileId, String role) {

  public static final ResolvedRole UNKNOWN = new ResolvedRole("", "");

  /**
   * Returns {@code true} when this instance represents a known resolved role.
   */
  public boolean isResolved() {
    return !this.equals(UNKNOWN);
  }
}
