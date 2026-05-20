package eu.xfsc.fc.core.service.verification;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.TimeUnit;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryCancelledException;
import org.apache.jena.query.QueryException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shared.JenaException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.OntologyImpactEntry;
import eu.xfsc.fc.api.generated.model.OntologyImpactList;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.RoleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes the per-ontology contribution of runtime-uploaded ontologies to each
 * registered trust-framework role.
 *
 * <p>For every {@link SchemaType#ONTOLOGY} row in the schema store, this service parses
 * the ontology with Jena and counts the {@code rdfs:subClassOf+} descendants reachable
 * from each registered role's primary root URI and its {@code additional_roots} siblings.
 * The result is surfaced to the admin schema-validation page so the OWL toggle's effect
 * is concrete: admins can see what each stored ontology contributes before flipping the
 * toggle off.
 *
 * <p>When an ontology fails to parse the entry carries {@code parseError=true} so the
 * UI can warn the admin instead of silently hiding the row — otherwise an unparseable
 * ontology looks identical to a healthy ontology with zero matching roles, and an admin
 * would draw the wrong conclusion about whether disabling OWL is safe.
 *
 * <p>The computation is read-only and consults only data already in the catalogue. No
 * caching is in place — Jena parsing is fast for the expected ontology count; add
 * {@code @Cacheable} keyed on the schema content hash if measurements show it slow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OntologyImpactService {

  private static final String SUBCLASS_QUERY =
      "SELECT ?sub WHERE { ?sub rdfs:subClassOf+ ?root FILTER(?sub != ?root) }";
  private static final String PARSE_ERROR_MESSAGE = "Could not parse ontology";
  private static final String SUBCLASS_TIMEOUT_MESSAGE =
      "Subclass walk timed out — ontology may have a deeply cyclic subClassOf graph";

  /**
   * Per-root SPARQL timeout for the {@code rdfs:subClassOf+} walk. A pathological ontology
   * (deeply cyclic or very large class hierarchy) could otherwise block the admin HTTP
   * thread indefinitely. The walk is read-only so a hard cap is safe; the entry is marked
   * {@code parseError=true} when the cap fires.
   */
  private static final long SUBCLASS_QUERY_TIMEOUT_MS = 2_000L;

  /**
   * Base URI used when parsing an ontology so relative IRIs (e.g. {@code <MyClass>})
   * resolve to predictable absolute IRIs instead of unresolved literals. Concatenated
   * with the schema id, so each ontology gets its own stable base.
   */
  private static final String ONTOLOGY_BASE_URI_PREFIX = "urn:fc:ontology:";

  private final SchemaStore schemaStore;
  private final TrustFrameworkRegistry trustFrameworkRegistry;

  /**
   * Returns the impact list: one entry per stored ontology with a role-to-count map,
   * plus a {@code noActiveBundles} flag when there are no active bundles to compute
   * contributions against.
   *
   * @return populated {@link OntologyImpactList}; entries is empty when no ontologies
   *         are stored
   */
  public OntologyImpactList computeImpact() {
    Map<SchemaType, List<String>> schemaList = schemaStore.getSchemaList();
    List<String> ontologyIds = schemaList.getOrDefault(SchemaType.ONTOLOGY, List.of());
    Map<String, Set<String>> roleRoots = collectRoleRoots();
    boolean noActiveBundles = roleRoots.isEmpty();

    List<OntologyImpactEntry> entries = new ArrayList<>(ontologyIds.size());
    for (String schemaId : ontologyIds) {
      SchemaRecord record;
      try {
        record = schemaStore.getSchemaRecord(schemaId);
      } catch (RuntimeException ex) {
        // Defensive iteration: one bad record must not break the whole batch. The exact
        // exception types thrown by SchemaStore implementations vary (NotFoundException,
        // DataAccessException, etc.), so the catch is intentionally broad here only.
        log.warn("computeImpact; could not load schema record '{}'", schemaId, ex);
        entries.add(parseErrorEntry(schemaId, "Could not load schema record"));
        continue;
      }
      entries.add(buildEntry(record, roleRoots));
    }

    return new OntologyImpactList().items(entries).noActiveBundles(noActiveBundles);
  }

  /**
   * Collects all role root URIs (primary + additional_roots) across every active bundle,
   * keyed by role name. Counts under the same role name from different bundles are
   * aggregated.
   */
  private Map<String, Set<String>> collectRoleRoots() {
    Map<String, Set<String>> roleRoots = new HashMap<>();
    Collection<TrustFrameworkBundle> bundles = trustFrameworkRegistry.getActiveBundles();
    for (TrustFrameworkBundle bundle : bundles) {
      FrameworkBundleConfig config = bundle.config();
      String namespace = config.namespace();
      for (Map.Entry<String, RoleConfig> roleEntry : config.roles().entrySet()) {
        String roleName = roleEntry.getKey();
        Set<String> rootsForRole = roleRoots.computeIfAbsent(roleName, k -> new HashSet<>());
        rootsForRole.add(namespace + roleName);
        for (String additionalRoot : roleEntry.getValue().additionalRoots()) {
          if (additionalRoot != null && !additionalRoot.isBlank()) {
            rootsForRole.add(additionalRoot);
          }
        }
      }
    }
    return roleRoots;
  }

  private OntologyImpactEntry buildEntry(SchemaRecord record, Map<String, Set<String>> roleRoots) {
    Map<String, Integer> contribs = new HashMap<>();
    String displayName = record.getId();
    boolean parseError = false;
    String parseErrorMessage = null;

    OntModel model = parseOntology(record);
    if (model == null) {
      parseError = true;
      parseErrorMessage = PARSE_ERROR_MESSAGE;
    } else {
      try {
        String resolvedName = extractDisplayName(model);
        if (resolvedName != null && !resolvedName.isBlank()) {
          displayName = resolvedName;
        }
        for (Map.Entry<String, Set<String>> roleEntry : roleRoots.entrySet()) {
          Set<String> subclasses = new HashSet<>();
          for (String root : roleEntry.getValue()) {
            subclasses.addAll(querySubclasses(model, root));
          }
          if (!subclasses.isEmpty()) {
            contribs.put(roleEntry.getKey(), subclasses.size());
          }
        }
      } catch (QueryCancelledException ex) {
        // The subclass walk timed out — mark the entry parseError so the admin sees the
        // gap. Drop any partial contributions because the count is no longer trustworthy.
        log.warn("computeImpact; subclass walk timed out for ontology '{}'", record.getId());
        contribs.clear();
        parseError = true;
        parseErrorMessage = SUBCLASS_TIMEOUT_MESSAGE;
      } finally {
        model.close();
      }
    }

    return new OntologyImpactEntry()
        .id(record.getId())
        .name(displayName)
        .uploadedAt(record.createdAt())
        .contributions(contribs)
        .parseError(parseError)
        .parseErrorMessage(parseErrorMessage);
  }

  private OntologyImpactEntry parseErrorEntry(String schemaId, String message) {
    return new OntologyImpactEntry()
        .id(schemaId)
        .name(schemaId)
        .contributions(new HashMap<>())
        .parseError(true)
        .parseErrorMessage(message);
  }

  private OntModel parseOntology(SchemaRecord record) {
    // No inference: we only want explicit rdfs:subClassOf edges. The `+` operator in the
    // SPARQL query handles transitive closure. A reasoner like OWL_MEM_MICRO_RULE_INF
    // would otherwise count owl:Nothing as a subclass of every class.
    OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
    // A stable per-ontology base URI ensures relative IRIs in Turtle (e.g. `<MyClass>`)
    // resolve to a predictable absolute form instead of `file://` (default working dir)
    // or empty-base failure. Without this, an ontology authored with relative IRIs
    // contributes zero subclasses to every role and silently misleads admins.
    String baseUri = ONTOLOGY_BASE_URI_PREFIX + record.getId();
    try {
      model.read(new StringReader(record.content()), baseUri, Lang.TURTLE.getName());
    } catch (RiotException ex) {
      log.warn("computeImpact; could not parse ontology '{}' as Turtle",
          record.getId(), ex);
      model.close();
      return null;
    }
    return model;
  }

  /**
   * Returns the URI of the first {@code owl:Ontology} subject declared in the model,
   * or {@code null} if none is present.
   */
  private String extractDisplayName(OntModel model) {
    ExtendedIterator<Ontology> ontologies = null;
    try {
      ontologies = model.listOntologies();
      if (ontologies.hasNext()) {
        Ontology ontology = ontologies.next();
        if (ontology != null && ontology.isURIResource()) {
          return ontology.getURI();
        }
      }
    } catch (JenaException ex) {
      log.debug("computeImpact; could not extract owl:Ontology display name", ex);
    } finally {
      if (ontologies != null) {
        ontologies.close();
      }
    }
    return null;
  }

  private Set<String> querySubclasses(OntModel model, String rootUri) {
    Set<String> result = new HashSet<>();
    ParameterizedSparqlString pss = new ParameterizedSparqlString();
    pss.setNsPrefix("rdfs", RDFS.getURI());
    pss.setCommandText(SUBCLASS_QUERY);
    try {
      pss.setIri("root", rootUri);
    } catch (IllegalArgumentException ex) {
      log.warn("computeImpact; could not bind root URI '{}'", rootUri, ex);
      return result;
    }
    // Hard cap so a pathological ontology (deep cyclic subClassOf, very large class
    // hierarchy) cannot hang the admin HTTP thread. Cancellation propagates as
    // QueryCancelledException; the caller marks the entry parseError.
    try (QueryExecution qe = QueryExecution.model(model)
        .query(pss.asQuery())
        .timeout(SUBCLASS_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()) {
      qe.execSelect().forEachRemaining(row -> {
        Resource res = row.getResource("sub");
        if (res != null && res.isURIResource()) {
          result.add(res.getURI());
        }
      });
    } catch (QueryCancelledException ex) {
      // Bubble up so buildEntry can mark the whole entry parseError — partial counts from
      // earlier roots would mislead the admin.
      log.warn("computeImpact; SPARQL subclass query for root '{}' timed out", rootUri);
      throw ex;
    } catch (QueryException ex) {
      log.warn("computeImpact; SPARQL subclass query for root '{}' failed",
          rootUri, ex);
    }
    return result;
  }
}
