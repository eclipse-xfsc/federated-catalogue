package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TrustFrameworkRegistryTest {

  // Minimal ontology: RoleA as a root class, no subclasses declared.
  private static final String MINIMAL_ONTOLOGY = """
      @prefix ex: <https://example.org/test/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      ex:RoleA a owl:Class .
      """;

  // Ontology with declared subclass: RoleASub rdfs:subClassOf RoleA.
  private static final String SUBCLASS_ONTOLOGY = """
      @prefix ex: <https://example.org/test/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      ex:RoleA a owl:Class .
      ex:RoleASub a owl:Class ; rdfs:subClassOf ex:RoleA .
      """;

  // Ontology where RoleBAlt is NOT a subclass of RoleB (framework reality in Gaia-x Loire 2511 ontology).
  private static final String SIBLING_ROLE_ONTOLOGY = """
      @prefix ex: <https://example.org/test/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      ex:RoleB a owl:Class .
      ex:RoleBAlt a owl:Class .
      """;

  // Ontology with 2-hop subclass chain: RoleASubSub → RoleASub → RoleA.
  private static final String TWO_HOP_ONTOLOGY = """
      @prefix ex: <https://example.org/test/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      ex:RoleA a owl:Class .
      ex:RoleASub a owl:Class ; rdfs:subClassOf ex:RoleA .
      ex:RoleASubSub a owl:Class ; rdfs:subClassOf ex:RoleASub .
      """;

  // Ontology that includes an anonymous (blank-node) subclass of RoleA.
  private static final String BLANK_NODE_ONTOLOGY = """
      @prefix ex: <https://example.org/test/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      ex:RoleA a owl:Class .
      [] rdfs:subClassOf ex:RoleA .
      """;

  private static TrustFrameworkBundle shaclBundle(String profileId, String namespace,
                                                  Map<String, BaseClassConfig> roles, String ontologyTtl) {
    var config = new FrameworkBundleConfig(profileId, "test-family", namespace, ValidationType.SHACL, roles, Map.of());
    var ontology = new eu.xfsc.fc.core.pojo.ContentAccessorDirect(ontologyTtl);
    return new TrustFrameworkBundle(config, ontology, null);
  }

  private static TrustFrameworkBundle jsonSchemaBundle(String profileId) {
    var config = new FrameworkBundleConfig(profileId, "test", "https://test/", ValidationType.JSON_SCHEMA,
        Map.of(), Map.of());
    return new TrustFrameworkBundle(config, null, null);
  }

  @Test
  void resolveBaseClass_unknownType_returnsUnknown() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.resolveBaseClass("https://example.org/Unknown")).isEqualTo(ResolvedBaseClass.UNKNOWN);
  }

  @Test
  void resolveBaseClass_rootUri_returnsResolvedBaseClass() {
    var roles = Map.of("RoleA", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveBaseClass("https://example.org/test/RoleA"))
        .isEqualTo(new ResolvedBaseClass("test-framework", "RoleA"));
  }

  @Test
  void resolveBaseClass_subclassOfRoot_returnsResolvedBaseClass() {
    var roles = Map.of("RoleA", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, SUBCLASS_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    // ex:RoleASub rdfs:subClassOf ex:RoleA — must resolve to RoleA
    assertThat(registry.resolveBaseClass("https://example.org/test/RoleASub"))
        .isEqualTo(new ResolvedBaseClass("test-framework", "RoleA"));
  }

  // additionalRoots: RoleBAlt is NOT a subclass of RoleB in OWL
  // but is covered via additionalRoots in BaseClassConfig (framework workaround)
  @Test
  void resolveBaseClass_additionalRoot_returnsResolvedBaseClass() {
    var roles = Map.of("RoleB", new BaseClassConfig(
        List.of("https://example.org/test/RoleBAlt"), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, SIBLING_ROLE_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveBaseClass("https://example.org/test/RoleBAlt"))
        .isEqualTo(new ResolvedBaseClass("test-framework", "RoleB"));
  }

  @Test
  void resolveBaseClass_2hopSubclassOfRoot_returnsResolvedBaseClass() {
    // ex:RoleASubSub rdfs:subClassOf ex:RoleASub rdfs:subClassOf ex:RoleA
    // OWL_MEM_MICRO_RULE_INF infers the transitive closure — both hops must be covered
    var roles = Map.of("RoleA", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, TWO_HOP_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveBaseClass("https://example.org/test/RoleASubSub"))
        .isEqualTo(new ResolvedBaseClass("test-framework", "RoleA"));
  }

  @Test
  void resolveBaseClass_firstBundleWinsOnRootUriConflict() {
    // Same root URI claimed by two bundles — first registration must win (putIfAbsent semantics)
    var roles = Map.of("RoleA", new BaseClassConfig(List.of(), List.of()));
    var bundle1 = shaclBundle("framework-a", "https://example.org/test/", roles, MINIMAL_ONTOLOGY);
    var bundle2 = shaclBundle("framework-b", "https://example.org/test/", roles, MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle1, bundle2));

    assertThat(registry.resolveBaseClass("https://example.org/test/RoleA"))
        .isEqualTo(new ResolvedBaseClass("framework-a", "RoleA"));
  }

  // bundle index methods
  @Test
  void getActiveBundles_returnsAllLoadedBundles() {
    var bundle = shaclBundle("test-framework", "https://example.org/test/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getActiveBundles()).containsExactly(bundle);
  }

  @Test
  void getAllBundles_preservesRegistrationOrder() {
    // Three bundles with profile IDs intentionally not in lexicographic order, so a HashMap-backed
    // index would shuffle them. Iteration must follow input list order.
    var bundleZ = shaclBundle("z-framework", "https://example.org/z/",
        Map.of("RoleA", new BaseClassConfig(List.of(), List.of())), MINIMAL_ONTOLOGY);
    var bundleA = shaclBundle("a-framework", "https://example.org/a/",
        Map.of("RoleA", new BaseClassConfig(List.of(), List.of())), MINIMAL_ONTOLOGY);
    var bundleM = shaclBundle("m-framework", "https://example.org/m/",
        Map.of("RoleA", new BaseClassConfig(List.of(), List.of())), MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundleZ, bundleA, bundleM));

    assertThat(registry.getAllBundles()).containsExactly(bundleZ, bundleA, bundleM);
  }

  @Test
  void getActiveBundles_returnsImmutableSnapshot() {
    var bundle = shaclBundle("test-framework", "https://example.org/test/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThatThrownBy(() -> registry.getActiveBundles().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getBundle_knownProfileId_returnsBundle() {
    var bundle = shaclBundle("test-framework", "https://example.org/test/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getBundle("test-framework")).contains(bundle);
  }

  @Test
  void getBundle_unknownProfileId_returnsEmpty() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.getBundle("no-such-framework")).isEmpty();
  }

  @Test
  void isFrameworkEnabled_knownBundle_returnsTrue() {
    var bundle = shaclBundle("test-framework", "https://example.org/test/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.isFrameworkEnabled("test-framework")).isTrue();
  }

  @Test
  void isFrameworkEnabled_unknownBundle_returnsFalse() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.isFrameworkEnabled("no-such-framework")).isFalse();
  }

  @Test
  void getEffectiveBaseClasses_knownBundle_returnsRoleNames() {
    var roles = Map.of(
        "RoleA", new BaseClassConfig(List.of(), List.of()),
        "RoleB", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getEffectiveBaseClasses("test-framework"))
        .containsExactlyInAnyOrder("RoleA", "RoleB");
  }

  @Test
  void getEffectiveBaseClasses_unknownBundle_returnsEmpty() {
    var registry = new TrustFrameworkRegistry(List.of());

    assertThat(registry.getEffectiveBaseClasses("no-such-framework")).isEmpty();
  }

  @Test
  void getEffectiveBaseClasses_preservesDeclarationOrder() {
    // LinkedHashMap fixes the input declaration order; Map.of() would scramble it.
    // Generic role names — the registry must not assume any framework-specific vocabulary.
    var roles = new LinkedHashMap<String, BaseClassConfig>();
    roles.put("RoleC", new BaseClassConfig(List.of(), List.of()));
    roles.put("RoleA", new BaseClassConfig(List.of(), List.of()));
    roles.put("RoleB", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles,
        """
            @prefix ex: <https://example.org/test/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:RoleA a owl:Class .
            ex:RoleB a owl:Class .
            ex:RoleC a owl:Class .
            """);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.getEffectiveBaseClasses("test-framework"))
        .containsExactly("RoleC", "RoleA", "RoleB");
  }

  @Test
  void getEffectiveBaseClasses_returnsImmutableSnapshot() {
    var roles = Map.of("RoleA", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThatThrownBy(() -> registry.getEffectiveBaseClasses("test-framework").add("fake"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // json-schema bundle: accepted but excluded from type index and active resolution
  @Test
  void resolveBaseClass_jsonSchemaBundle_returnsUnknown() {
    var bundle = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistry(List.of(bundle));

    assertThat(registry.resolveBaseClass("https://example.org/SomeType")).isEqualTo(ResolvedBaseClass.UNKNOWN);
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
    var bundle1 = shaclBundle("test-framework", "https://namespace-a.org/", Map.of(), MINIMAL_ONTOLOGY);
    var bundle2 = shaclBundle("test-framework", "https://namespace-b.org/", Map.of(), MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistry(List.of(bundle1, bundle2));

    assertThat(registry.getBundle("test-framework").get().config().namespace())
        .isEqualTo("https://namespace-a.org/");
  }

  @Test
  void constructor_nullAdditionalRoot_doesNotThrow() {
    // A null entry in additionalRoots (e.g. from malformed YAML) must be skipped, not NPE
    var rolesWithNull = new HashMap<String, BaseClassConfig>();
    rolesWithNull.put("RoleA", new BaseClassConfig(
        new ArrayList<>(Arrays.asList(null, "https://example.org/test/RoleA")),
        List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", rolesWithNull, MINIMAL_ONTOLOGY);

    assertThatCode(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_blankNodeSubclass_doesNotThrow() {
    // An anonymous (blank-node) subclass in the ontology must be skipped, not NPE
    var roles = Map.of("RoleA", new BaseClassConfig(List.of(), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, BLANK_NODE_ONTOLOGY);

    assertThatCode(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_nullNamespaceWithRoles_throwsIllegalArgument() {
    // A SHACL bundle with roles but null namespace cannot construct valid role URIs
    var config = new FrameworkBundleConfig("bad-bundle", "test", null, ValidationType.SHACL,
        Map.of("Role", new BaseClassConfig(List.of(), List.of())), Map.of());
    var bundle = new TrustFrameworkBundle(config,
        new eu.xfsc.fc.core.pojo.ContentAccessorDirect(MINIMAL_ONTOLOGY), null);

    assertThatThrownBy(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_namespaceMissingTrailingSeparator_throwsIllegalArgument() {
    // Namespace without trailing '/' or '#' produces "https://example.orgRole" — invalid
    var config = new FrameworkBundleConfig("bad-bundle", "test", "https://example.org", ValidationType.SHACL,
        Map.of("Role", new BaseClassConfig(List.of(), List.of())), Map.of());
    var bundle = new TrustFrameworkBundle(config,
        new eu.xfsc.fc.core.pojo.ContentAccessorDirect(MINIMAL_ONTOLOGY), null);

    assertThatThrownBy(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_uriWithInjectionCharacter_doesNotThrow() {
    // A URI containing '>' in additionalRoots must not break SPARQL query construction
    var roles = Map.of("RoleB", new BaseClassConfig(
        List.of("https://example.org/bad>uri"), List.of()));
    var bundle = shaclBundle("test-framework", "https://example.org/test/", roles, SIBLING_ROLE_ONTOLOGY);

    assertThatCode(() -> new TrustFrameworkRegistry(List.of(bundle)))
        .doesNotThrowAnyException();
  }

  @Test
  void getActiveBundles_excludesDeferredBundles() {
    // SHACL bundle is active; JSON_SCHEMA bundle is deferred — getActiveBundles() must exclude deferred
    var active = shaclBundle("test-framework", "https://example.org/test/", Map.of(), MINIMAL_ONTOLOGY);
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
