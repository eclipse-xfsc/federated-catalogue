package eu.xfsc.fc.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.shared.impl.JenaParameters;
import org.apache.jena.vocabulary.RDF;

import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.ResolvedRole;
import eu.xfsc.fc.core.service.trustframework.RoleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClaimValidator {
    // Stored to temporarily deviate from the standard Jena behavior of parsing
    // literals
	// don't see how it can work in multithreaded scenario!
    private boolean eagerJenaLiteralValidation;
    private boolean jenaAcceptanceOfUnknownLiteralDatatypes;

    private void switchOnJenaLiteralValidation() {
        // save the actual settings to not interfere with other modules which
        // rely on other settings
        eagerJenaLiteralValidation =
            JenaParameters.enableEagerLiteralValidation;
        jenaAcceptanceOfUnknownLiteralDatatypes =
            JenaParameters.enableSilentAcceptanceOfUnknownDatatypes;

        // Now switch to picky mode
      JenaParameters.enableEagerLiteralValidation = true;
      JenaParameters.enableSilentAcceptanceOfUnknownDatatypes = false;
    }

    private void resetJenaLiteralValidation() {
      JenaParameters.enableEagerLiteralValidation =
                eagerJenaLiteralValidation;
      JenaParameters.enableSilentAcceptanceOfUnknownDatatypes =
                jenaAcceptanceOfUnknownLiteralDatatypes;
    }

    /**
     * Validates if a claim elements are following the required syntax and
     * conditions before sending them to Neo4J
     *
     * @param claimList the set of claims to be validated
     * @return the claim as a formatted triple string
     */
    public Model validateClaims(List<RdfClaim> claimList) {
        Model listClaims = ModelFactory.createDefaultModel();
        StringBuilder payload = new StringBuilder();
        for (RdfClaim claim : claimList) {
            validateRDFTripleSyntax(claim);
            payload.append(claim.asTriple());
        }
        InputStream in = IOUtils.toInputStream(payload, StandardCharsets.UTF_8);
        RDFDataMgr.read(listClaims, in, Lang.TTL);
        return listClaims;
    }

    /**
     * Method to validate that a claim follow the RDF triple syntax, i.e., <
     * (URI, blank node) , URI, (URI, blank node, literal) >
     *
     * @param claim the claim to be validated
     */
    private void validateRDFTripleSyntax(RdfClaim claim) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = IOUtils.toInputStream(claim.asTriple(), StandardCharsets.UTF_8)) {
            switchOnJenaLiteralValidation();
            RDFDataMgr.read(model, in, Lang.TTL);
        } catch (IOException | DatatypeFormatException | RiotException e) {
            log.debug("Error in Validating validateRDFTripleSyntax {}", e.getMessage());
            throw new QueryException(String.format("Triple %s has syntax error: %s", claim.asTriple(), e.getMessage()));
        } finally {
            resetJenaLiteralValidation();
        }

        Triple triple = model.getGraph().find().next();
        // --- subject ----------------------------------------------------
        Node s = triple.getSubject();
        if (s.isURI()) {
            // Caution! Jena will automatically apply modifications
            // to generate valid URIs. E.g. the broken URI
            // htw3id.org/gaia-x/indiv#serviceElasticSearch.json
            // will be converted to the URL
            // file:///home/user/some/path/fc-service/htw3id.org/gaia-x/indiv#serviceElasticSearch.json
            // Hence, we have to use the original URI string here
            // otherwise calling URI.create( ) will not fail in case
            // of a broken URI.
            // AND: We will have to strip off the angle brackets!
            // Any abbreviated URIS (i.e. something like ex:Foo without
            // angle brackets) will already be rejected above as Jena
            // should complain about the not defined prefix (e.g. ex in
            // the ex:Foo example above).
            String subjectStr = claim.getSubjectValue();
            try {
                new URI(subjectStr);
            } catch (URISyntaxException e) {
                throw new QueryException(String.format("Subject in triple %s is not a valid URI", claim.asTriple()));
            } // else it should be a blank node
        }
        // --- predicate --------------------------------------------------
        Node p = triple.getPredicate();
        if (p.isURI()) {
            // c.f. the comment for handling subject nodes above
            String predicateStr = claim.getPredicateValue();
            try {
                new URI(predicateStr);
            } catch (URISyntaxException e) {
                throw new QueryException(String.format("Predicate in triple %s is not a valid URI", claim.asTriple()));
            }
        }
        // --- object -----------------------------------------------------
        Node o = triple.getObject();
        if (o.isURI()) {
            // c.f. the comment for handling subject nodes above
            String objectStr = claim.getObjectValue();
            try {
                new URI(objectStr);
            } catch (URISyntaxException e) {
                throw new QueryException(String.format("Object in triple %s is not a valid URI", claim.asTriple()));
            }
        } else //noinspection StatementWithEmptyBody // suppressed as the comment explains why we don't need to do anything here
            if (o.isLiteral()) {
            // Nothing needs to be done here as literal syntax errors and
            // datatype errors are already handled by the parser directly.
            // See the catch blocks after the RDFDataMgr.read( ) call above.

        } // else it's a blank node, which is OK
    }
    
    public Pair<String, Set<String>> resolveClaims(List<RdfClaim> claims, String subject) {
        Model model = validateClaims(claims);
      String added = ExtendClaims.addPropertyGraphUri(model, subject, VerificationConstants.GAIAX_CLAIMS_GRAPH_URI);
        Set<String> props = ExtendClaims.getMultivalProp(model);
        return Pair.of(added, props);
    }
    
    
    private static final String CREDENTIAL_SUBJECT = CredentialConstants.CREDENTIAL_SUBJECT_URI;

  /**
   * Resolves the trust-framework role of the credential subject.
   *
   * <p>Fast path: looks up each type URI in the registry's pre-built index (populated at boot
   * from bundle ontologies). Slow path: when the registry has no match and a composite ontology
   * is provided, performs a SPARQL subclass walk over that ontology — needed for subclasses
   * introduced via dynamically uploaded schemas.
   *
   * @param sm                Jena stream manager (for JSON-LD context resolution)
   * @param subject           JSON-LD credential string whose {@code credentialSubject} type is inspected
   * @param registry          the active trust-framework registry
   * @param compositeOntology union ontology to use as fallback; may be {@code null}
   * @return the first resolved role, or {@link ResolvedRole#UNKNOWN} when no framework claims the type
   */
  public static ResolvedRole resolveSubjectRole(StreamManager sm, String subject,
                                                TrustFrameworkRegistry registry, ContentAccessor compositeOntology) {
        try {
          Model data = ModelFactory.createDefaultModel();
          RDFParser.create()
                  .streamManager(sm)
                  .source(new StringReader(subject))
                  .lang(Lang.JSONLD11)
                  .parse(data);

          List<String> unresolved = new ArrayList<>();
          NodeIterator node = data.listObjectsOfProperty(data.createProperty(CREDENTIAL_SUBJECT));
          while (node.hasNext()) {
            NodeIterator typeNode = data.listObjectsOfProperty(node.nextNode().asResource(), RDF.type);
            for (RDFNode rdfNode : typeNode.toList()) {
              if (!rdfNode.isURIResource()) {
                continue;
              }
              String typeUri = rdfNode.asResource().getURI();
              ResolvedRole role = registry.resolveRole(typeUri);
              if (role.isResolved()) {
                return role;
              }
              unresolved.add(typeUri);
            }
          }
          if (compositeOntology != null && !unresolved.isEmpty()) {
            return resolveViaOntology(unresolved, registry, compositeOntology);
          }
        } catch (Exception e) {
          log.debug("resolveSubjectRole.error: {}", e.getMessage());
        }
    return ResolvedRole.UNKNOWN;
  }

  private static ResolvedRole resolveViaOntology(List<String> typeUris,
                                                 TrustFrameworkRegistry registry, ContentAccessor compositeOntology) {
    OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        model.read(new StringReader(compositeOntology.getContentAsString()), null, Lang.TURTLE.getName());
    for (TrustFrameworkBundle bundle : registry.getActiveBundles()) {
      FrameworkBundleConfig config = bundle.config();
      for (Map.Entry<String, RoleConfig> roleEntry : config.roles().entrySet()) {
        String roleName = roleEntry.getKey();
        RoleConfig roleConfig = roleEntry.getValue();
        List<String> rootUris = new ArrayList<>();
        if (config.namespace() != null) {
          rootUris.add(config.namespace() + roleName);
        }
        rootUris.addAll(roleConfig.additionalRoots());
        ResolvedRole resolved = new ResolvedRole(config.id(), roleName);
        for (String typeUri : typeUris) {
          for (String rootUri : rootUris) {
            if (isSubclassOf(typeUri, rootUri, model)) {
              return resolved;
            }
          }
        }
      }
    }
    return ResolvedRole.UNKNOWN;
  }

  private static boolean isSubclassOf(String typeUri, String rootUri, OntModel model) {
    if (!isValidSparqlUri(typeUri) || !isValidSparqlUri(rootUri)) {
      return false;
    }
    String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
        + "ASK { <" + typeUri + "> rdfs:subClassOf+ <" + rootUri + "> }";
    try (var qe = QueryExecutionFactory.create(QueryFactory.create(query), model)) {
      return qe.execAsk();
    }
  }

  private static boolean isValidSparqlUri(String uri) {
    return uri != null
        && !uri.contains(">")
        && !uri.contains("<")
        && !uri.contains(" ")
        && !uri.contains("\n")
        && !uri.contains("\r");
  }

}

