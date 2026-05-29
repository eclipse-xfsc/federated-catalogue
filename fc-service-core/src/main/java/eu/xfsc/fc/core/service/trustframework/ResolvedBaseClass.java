package eu.xfsc.fc.core.service.trustframework;

/**
 * Carries the resolved trust-framework base class for a credential subject type.
 * Use {@link #UNKNOWN} when no framework claims the type.
 */
public record ResolvedBaseClass(String frameworkProfileId, String baseClass) {

  public static final ResolvedBaseClass UNKNOWN = new ResolvedBaseClass("", "");

  /**
   * Returns {@code true} when this instance represents a known resolved base class.
   */
  public boolean isResolved() {
    return !this.equals(UNKNOWN);
  }
}
