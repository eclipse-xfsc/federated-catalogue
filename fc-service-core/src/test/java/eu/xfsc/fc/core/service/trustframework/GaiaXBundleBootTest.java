package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the built-in Gaia-X 2511 bundle loads from classpath at boot
 * and that its type-index is a strict superset of the legacy hardcoded URI set.
 */
class GaiaXBundleBootTest {

  // Legacy hardcoded URIs from application.yml (the set the new index must be a superset of)
  private static final String GX_PARTICIPANT = "https://w3id.org/gaia-x/2511#Participant";
  private static final String GX_SERVICE_OFFERING = "https://w3id.org/gaia-x/2511#ServiceOffering";
  private static final String GX_DIGITAL_SERVICE_OFFERING = "https://w3id.org/gaia-x/2511#DigitalServiceOffering";
  private static final String GX_RESOURCE = "https://w3id.org/gaia-x/2511#Resource";

  // Previously-missing subclasses — confirms the new index is strictly wider than legacy
  private static final String GX_LEGAL_PERSON = "https://w3id.org/gaia-x/2511#LegalPerson";

  private List<TrustFrameworkBundle> loadBundles() throws IOException {
    return new TrustFrameworkBundleLoader().load();
  }

  @Test
  void loader_loadsGaiaX2511Bundle_fromClasspath() throws IOException {
    var bundles = loadBundles();

    assertThat(bundles)
        .as("Classpath must contain the built-in gaia-x-2511 bundle")
        .anyMatch(b -> "gaia-x-2511".equals(b.config().id()));
  }

  @Test
  void registry_gaiaX2511_isEnabled() throws IOException {
    var registry = new TrustFrameworkRegistry(loadBundles());

    assertThat(registry.isFrameworkEnabled("gaia-x-2511")).isTrue();
  }

  @Test
  void registry_allLegacyUris_resolve() throws IOException {
    var registry = new TrustFrameworkRegistry(loadBundles());

    assertThat(registry.resolveBaseClass(GX_PARTICIPANT)).isNotEqualTo(ResolvedBaseClass.UNKNOWN);
    assertThat(registry.resolveBaseClass(GX_SERVICE_OFFERING)).isNotEqualTo(ResolvedBaseClass.UNKNOWN);
    assertThat(registry.resolveBaseClass(GX_DIGITAL_SERVICE_OFFERING)).isNotEqualTo(ResolvedBaseClass.UNKNOWN);
    assertThat(registry.resolveBaseClass(GX_RESOURCE)).isNotEqualTo(ResolvedBaseClass.UNKNOWN);
  }

  @Test
  void registry_typeIndex_isSupersetOfLegacy_includesSubclasses() throws IOException {
    var registry = new TrustFrameworkRegistry(loadBundles());

    // gx:LegalPerson rdfs:subClassOf gx:Participant — was NOT in legacy hardcoded list
    assertThat(registry.resolveBaseClass(GX_LEGAL_PERSON))
        .as("gx:LegalPerson must resolve as Participant via OWL subclass walk (was missing in legacy)")
        .isEqualTo(new ResolvedBaseClass("gaia-x-2511", "Participant"));
  }

  @Test
  void registry_legacyUris_resolveToCorrectRoles() throws IOException {
    var registry = new TrustFrameworkRegistry(loadBundles());

    assertThat(registry.resolveBaseClass(GX_PARTICIPANT))
        .isEqualTo(new ResolvedBaseClass("gaia-x-2511", "Participant"));
    assertThat(registry.resolveBaseClass(GX_SERVICE_OFFERING))
        .isEqualTo(new ResolvedBaseClass("gaia-x-2511", "ServiceOffering"));
    assertThat(registry.resolveBaseClass(GX_DIGITAL_SERVICE_OFFERING))
        .isEqualTo(new ResolvedBaseClass("gaia-x-2511", "ServiceOffering"));
    assertThat(registry.resolveBaseClass(GX_RESOURCE))
        .isEqualTo(new ResolvedBaseClass("gaia-x-2511", "Resource"));
  }
}
