package eu.xfsc.fc.core.service.trustframework;

import eu.xfsc.fc.core.pojo.ContentAccessor;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory registry of trust framework bundles and their declared roles. Provides efficient
 * resolution of role URIs to the bundle and role name that declares them, including support for
 * subclass hierarchies in SHACL ontologies.
 * It is built at application startup from the static bundle definitions without any dynamic state.
 * It does not track enabled/disabled state or mediate access;
 * it is purely a data structure for role resolution and bundle lookup.
 *
 * <p>
 * This is separate from the persistence layer {@link eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRepository}
 * (which tracks enabled/disabled state) and the service layer {@link TrustFrameworkService}
 * (which mediates access to the registry and applies enabled/disabled logic).
 *
 * <p>Bundles with unsupported validation types are registered but not active — their roles are not
 * indexed for resolution, and they are excluded from the active bundles list. This allows the
 * registry to be pre-populated with all known bundles at startup, even if the validation engine
 * is not yet wired to handle some of them. Deferred bundles will become active once their
 * validation type is supported.
 *
 * <p>The registry is immutable after construction; any changes require creating a new instance.
 * This simplifies thread safety and allows the registry to be safely shared across the application
 * without synchronization.
 */
@Slf4j
public class TrustFrameworkRegistry {

  /**
   * Regex pattern for validating SPARQL URIs.
   * Matches URIs that do NOT contain characters unsafe for SPARQL injection:
   * - angle brackets: < >
   * - whitespace: space, newline, carriage return
   */
  private static final Pattern VALID_SPARQL_URI_PATTERN = Pattern.compile("^[^<>\\s\\n\\r]*$");

  private final Map<String, ResolvedRole> typeIndex;
  private final Map<String, TrustFrameworkBundle> bundleIndex;
  private final Set<String> activeProfileIds;

  /**
   * Constructs the registry from a list of bundles.
   * Bundles with duplicate IDs are ignored with a warning; first registration wins.
   * Bundles with unsupported validation types are ignored with a warning; they may be activated in the future when the validation engine is wired.
   *
   * @param bundles the list of bundles to register
   */
  public TrustFrameworkRegistry(List<TrustFrameworkBundle> bundles) {
    this.bundleIndex = new HashMap<>();
    this.typeIndex = new HashMap<>();
    var active = new java.util.HashSet<String>();
    for (TrustFrameworkBundle bundle : bundles) {
      String profileId = bundle.config().id();
      if (bundleIndex.containsKey(profileId)) {
        log.warn("Duplicate bundle ID '{}' ignored — first registration wins", profileId);
        continue;
      }
      bundleIndex.put(profileId, bundle);
      if (bundle.config().validationType() == ValidationType.SHACL) {
        validateNamespace(bundle.config());
        indexBundle(bundle);
        active.add(profileId);
      } else {
        log.warn("Bundle '{}' has validationType '{}' — validation engine not yet wired, bundle deferred",
            profileId, bundle.config().validationType());
      }
    }
    this.activeProfileIds = Set.copyOf(active);
  }

  /**
   * Validates that if roles are declared, the namespace is non-null and ends with '/' or '#'.
   * This is required for correct URI concatenation during type resolution.
   */
  private static void validateNamespace(FrameworkBundleConfig config) {
    if (config.roles().isEmpty()) {
      return;
    }
    String ns = config.namespace();
    if (ns == null) {
      throw new IllegalArgumentException(
          "Bundle '" + config.id() + "' has roles but namespace is null");
    }
    if (!ns.endsWith("/") && !ns.endsWith("#")) {
      throw new IllegalArgumentException(
          "Bundle '" + config.id() + "' namespace '" + ns + "' must end with '/' or '#'");
    }
  }

  private static boolean isValidSparqlUri(String uri) {
    return uri != null && VALID_SPARQL_URI_PATTERN.matcher(uri).matches();
  }

  /**
   * Resolves a role URI to its corresponding ResolvedRole, which contains the bundle ID and role name.
   * If the URI is not recognized, returns ResolvedRole.UNKNOWN.
   *
   * @param typeUri the role URI to resolve
   * @return the ResolvedRole corresponding to the URI, or ResolvedRole.UNKNOWN if not recognized
   */
  public ResolvedRole resolveRole(String typeUri) {
    return typeIndex.getOrDefault(typeUri, ResolvedRole.UNKNOWN);
  }

  /**
   * Returns only the bundles that are currently active (i.e. their validation engine is wired and
   * their types participate in role resolution).
   * Modifications to the returned collection will not affect the registry's internal state.
   *
   * <p>Deferred bundles — those registered with an unsupported {@code validationType} — are
   * excluded.
   *
   * @return immutable collection of active bundles; never null
   */
  public Collection<TrustFrameworkBundle> getActiveBundles() {
    return bundleIndex.values().stream()
        .filter(b -> activeProfileIds.contains(b.config().id()))
        .toList();
  }

  /**
   * Retrieves the bundle associated with the given profile ID, if it exists.
   *
   * @param profileId the ID of the profile to look up
   * @return an Optional containing the TrustFrameworkBundle if found, or empty if no bundle with the given ID is registered
   */
  public Optional<TrustFrameworkBundle> getBundle(String profileId) {
    return Optional.ofNullable(bundleIndex.get(profileId));
  }

  /**
   * Returns the set of effective role names declared in the bundle associated with the given profile ID.
   * If no bundle is registered under the profile ID, returns an empty set.
   *
   * @param profileId the ID of the profile to look up
   * @return a set of role names declared in the bundle, or an empty set if no bundle is registered under the profile ID
   */
  public Set<String> getEffectiveRoles(String profileId) {
    return bundleIndex.containsKey(profileId)
        ? Set.copyOf(bundleIndex.get(profileId).config().roles().keySet())
        : Set.of();
  }

  /**
   * Returns true if the registry has an active bundle registered under the given profile ID.
   * An active bundle is one that has a supported validation type and was successfully indexed.
   *
   * @param profileId the ID of the profile to check
   * @return true if an active bundle is registered under the profile ID, false otherwise
   */
  public boolean isFrameworkEnabled(String profileId) {
    return activeProfileIds.contains(profileId);
  }

  private void indexBundle(TrustFrameworkBundle bundle) {
    if (bundle.ontology() == null) {
      log.warn("Bundle '{}' has validationType SHACL but no ontology — subclass walk skipped", bundle.config().id());
    }
    OntModel model = loadOntModel(bundle.ontology());
    var config = bundle.config();
    config.roles().forEach((roleName, roleConfig) -> {
      var resolved = new ResolvedRole(config.id(), roleName);
      indexTypeAndSubclasses(config.namespace() + roleName, resolved, model);
      for (String additionalRoot : roleConfig.additionalRoots()) {
        if (additionalRoot == null) {
          log.warn("Bundle '{}' role '{}' has a null additionalRoot entry — skipped", config.id(), roleName);
          continue;
        }
        indexTypeAndSubclasses(additionalRoot, resolved, model);
      }
    });
  }

  private OntModel loadOntModel(ContentAccessor ontology) {
    OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
    if (ontology != null) {
      model.read(new StringReader(ontology.getContentAsString()), null, Lang.TURTLE.getName());
    }
    return model;
  }

  private void indexTypeAndSubclasses(String rootUri, ResolvedRole resolved, OntModel model) {
    if (!isValidSparqlUri(rootUri)) {
      log.warn("Skipping URI '{}' — contains characters unsafe for SPARQL injection", rootUri);
      return;
    }
    typeIndex.putIfAbsent(rootUri, resolved);
    String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
        + "SELECT ?sub WHERE { ?sub rdfs:subClassOf <" + rootUri + "> FILTER(?sub != <" + rootUri + ">) }";
    try (var qe = QueryExecutionFactory.create(QueryFactory.create(query), model)) {
      qe.execSelect().forEachRemaining(row -> {
        Resource res = row.getResource("sub");
        if (res != null && res.isURIResource()) {
          typeIndex.putIfAbsent(res.getURI(), resolved);
        }
      });
    }
  }
}
