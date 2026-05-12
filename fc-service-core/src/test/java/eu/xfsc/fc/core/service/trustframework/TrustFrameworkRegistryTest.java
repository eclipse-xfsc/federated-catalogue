package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TrustFrameworkRegistryTest {

  // Minimal ontology: Participant as a root class, no subclasses declared.
  private static final String MINIMAL_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      gx:Participant a owl:Class .
      """;

  // Ontology with declared subclass: LegalPerson rdfs:subClassOf Participant.
  private static final String SUBCLASS_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      gx:Participant a owl:Class .
      gx:LegalPerson a owl:Class ; rdfs:subClassOf gx:Participant .
      """;

  // Ontology where DigitalServiceOffering is NOT a subclass of ServiceOffering (gx-2511 reality).
  private static final String DSO_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      gx:ServiceOffering a owl:Class .
      gx:DigitalServiceOffering a owl:Class .
      """;

  // Ontology with 2-hop subclass chain: PrivateLegalPerson → LegalPerson → Participant.
  private static final String TWO_HOP_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      gx:Participant a owl:Class .
      gx:LegalPerson a owl:Class ; rdfs:subClassOf gx:Participant .
      gx:PrivateLegalPerson a owl:Class ; rdfs:subClassOf gx:LegalPerson .
      """;

  // Ontology that includes an anonymous (blank-node) subclass of Participant.
  private static final String BLANK_NODE_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      gx:Participant a owl:Class .
      [] rdfs:subClassOf gx:Participant .
      """;

  private static TrustFrameworkBundle shaclBundle(String profileId, String namespace,
                                                  Map<String, RoleConfig> roles, String ontologyTtl) {
    var config = new FrameworkBundleConfig(profileId, "gaia-x", namespace, ValidationType.SHACL, roles, Map.of());
    var ontology = new eu.xfsc.fc.core.pojo.ContentAccessorDirect(ontologyTtl);
    return new TrustFrameworkBundle(config, ontology, null);
  }

  private static TrustFrameworkBundle jsonSchemaBundle(String profileId) {
    var config = new FrameworkBundleConfig(profileId, "test", "https://test/", ValidationType.JSON_SCHEMA,
        Map.of(), Map.of());
    return new TrustFrameworkBundle(config, null, null);
  }

  @Test
  void resolveRole_unknownType_returnsUnknown() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.resolveRole("https://example.org/Unknown")).isEqualTo(ResolvedRole.UNKNOWN);
  }

  @Test
  void resolveRole_rootUri_returnsResolvedRole() {
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveRole("https://w3id.org/gaia-x/Participant"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "Participant"));
  }

  @Test
  void resolveRole_subclassOfRoot_returnsResolvedRole() {
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, SUBCLASS_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    // gx:LegalPerson rdfs:subClassOf gx:Participant — must resolve to Participant
    assertThat(registry.resolveRole("https://w3id.org/gaia-x/LegalPerson"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "Participant"));
  }

  // additionalRoots: DigitalServiceOffering is NOT a subclass of ServiceOffering in OWL
  // but is covered via additionalRoots in RoleConfig (gx-2511 workaround)
  @Test
  void resolveRole_additionalRoot_returnsResolvedRole() {
    var roles = Map.of("ServiceOffering", new RoleConfig(
        List.of("https://w3id.org/gaia-x/DigitalServiceOffering"), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, DSO_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveRole("https://w3id.org/gaia-x/DigitalServiceOffering"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "ServiceOffering"));
  }

  @Test
  void resolveRole_2hopSubclassOfRoot_returnsResolvedRole() {
    // gx:PrivateLegalPerson rdfs:subClassOf gx:LegalPerson rdfs:subClassOf gx:Participant
    // OWL_MEM_MICRO_RULE_INF infers the transitive closure — both hops must be covered
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, TWO_HOP_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveRole("https://w3id.org/gaia-x/PrivateLegalPerson"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "Participant"));
  }

  @Test
  void resolveRole_firstBundleWinsOnRootUriConflict() {
    // Same root URI claimed by two bundles — first registration must win (putIfAbsent semantics)
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle1 = shaclBundle("framework-a", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);
    var bundle2 = shaclBundle("framework-b", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle1, bundle2));

    assertThat(registry.resolveRole("https://w3id.org/gaia-x/Participant"))
        .isEqualTo(new ResolvedRole("framework-a", "Participant"));
  }

  // bundle index methods
  @Test
  void getActiveBundles_returnsAllLoadedBundles() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getActiveBundles()).containsExactly(bundle);
  }

  @Test
  void getActiveBundles_returnsImmutableSnapshot() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThatThrownBy(() -> registry.getActiveBundles().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getBundle_knownProfileId_returnsBundle() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getBundle("gaia-x-2511")).contains(bundle);
  }

  @Test
  void getBundle_unknownProfileId_returnsEmpty() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.getBundle("no-such-framework")).isEmpty();
  }

  @Test
  void isFrameworkEnabled_knownBundle_returnsTrue() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.isFrameworkEnabled("gaia-x-2511")).isTrue();
  }

  @Test
  void isFrameworkEnabled_unknownBundle_returnsFalse() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.isFrameworkEnabled("no-such-framework")).isFalse();
  }

  @Test
  void getEffectiveRoles_knownBundle_returnsRoleNames() {
    var roles = Map.of(
        "Participant", new RoleConfig(List.of(), List.of()),
        "ServiceOffering", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getEffectiveRoles("gaia-x-2511"))
        .containsExactlyInAnyOrder("Participant", "ServiceOffering");
  }

  @Test
  void getEffectiveRoles_unknownBundle_returnsEmpty() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.getEffectiveRoles("no-such-framework")).isEmpty();
  }

  @Test
  void getEffectiveRoles_returnsImmutableSnapshot() {
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThatThrownBy(() -> registry.getEffectiveRoles("gaia-x-2511").add("fake"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // json-schema bundle: accepted but excluded from type index and active resolution
  @Test
  void resolveRole_jsonSchemaBundle_returnsUnknown() {
    var bundle = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveRole("https://example.org/SomeType")).isEqualTo(ResolvedRole.UNKNOWN);
  }

  @Test
  void isFrameworkEnabled_jsonSchemaBundle_returnsFalse() {
    // JSON_SCHEMA bundles are loaded but deferred — not actively resolving, so not "enabled"
    var bundle = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.isFrameworkEnabled("untp-v1")).isFalse();
  }

  @Test
  void isFrameworkEnabled_unknownValidationTypeBundle_returnsFalse() {
    // UNKNOWN validationType (e.g. typo in framework.yaml) is treated the same as non-SHACL — deferred
    var config = new FrameworkBundleConfig("unknown-fw", "test", "https://test/",
        ValidationType.UNKNOWN, Map.of(), Map.of());
    var bundle = new TrustFrameworkBundle(config, null, null);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.isFrameworkEnabled("unknown-fw")).isFalse();
  }

  @Test
  void constructor_duplicateBundleId_keepsFirstBundle() {
    // Two bundles with the same ID — first registration wins; second is ignored
    var bundle1 = shaclBundle("gaia-x-2511", "https://namespace-a.org/", Map.of(), MINIMAL_ONTOLOGY);
    var bundle2 = shaclBundle("gaia-x-2511", "https://namespace-b.org/", Map.of(), MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle1, bundle2));

    assertThat(registry.getBundle("gaia-x-2511").get().config().namespace())
        .isEqualTo("https://namespace-a.org/");
  }

  @Test
  void constructor_nullAdditionalRoot_doesNotThrow() {
    // A null entry in additionalRoots (e.g. from malformed YAML) must be skipped, not NPE
    var rolesWithNull = new HashMap<String, RoleConfig>();
    rolesWithNull.put("Participant", new RoleConfig(
        new ArrayList<>(Arrays.asList(null, "https://w3id.org/gaia-x/Participant")),
        List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", rolesWithNull, MINIMAL_ONTOLOGY);

    assertThatCode(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_blankNodeSubclass_doesNotThrow() {
    // An anonymous (blank-node) subclass in the ontology must be skipped, not NPE
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, BLANK_NODE_ONTOLOGY);

    assertThatCode(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_nullNamespaceWithRoles_throwsIllegalArgument() {
    // A SHACL bundle with roles but null namespace cannot construct valid role URIs
    var config = new FrameworkBundleConfig("bad-bundle", "test", null, ValidationType.SHACL,
        Map.of("Role", new RoleConfig(List.of(), List.of())), Map.of());
    var bundle = new TrustFrameworkBundle(config,
        new eu.xfsc.fc.core.pojo.ContentAccessorDirect(MINIMAL_ONTOLOGY), null);

    assertThatThrownBy(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_namespaceMissingTrailingSeparator_throwsIllegalArgument() {
    // Namespace without trailing '/' or '#' produces "https://example.orgRole" — invalid
    var config = new FrameworkBundleConfig("bad-bundle", "test", "https://example.org", ValidationType.SHACL,
        Map.of("Role", new RoleConfig(List.of(), List.of())), Map.of());
    var bundle = new TrustFrameworkBundle(config,
        new eu.xfsc.fc.core.pojo.ContentAccessorDirect(MINIMAL_ONTOLOGY), null);

    assertThatThrownBy(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_uriWithInjectionCharacter_doesNotThrow() {
    // A URI containing '>' in additionalRoots must not break SPARQL query construction
    var roles = Map.of("ServiceOffering", new RoleConfig(
        List.of("https://example.org/bad>uri"), List.of()));
    var bundle = shaclBundle("test", "https://example.org/", roles, DSO_ONTOLOGY);

    assertThatCode(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .doesNotThrowAnyException();
  }

  @Test
  void getActiveBundles_excludesDeferredBundles() {
    // SHACL bundle is active; JSON_SCHEMA bundle is deferred — getActiveBundles() must exclude deferred
    var active = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var deferred = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistry(List.of(active, deferred));

    assertThat(registry.getActiveBundles())
        .containsExactly(active)
        .doesNotContain(deferred);
  }

  @Test
  void getActiveBundles_allDeferred_returnsEmpty() {
    var deferred = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistry(List.of(deferred));

    assertThat(registry.getActiveBundles()).isEmpty();
  }
}
