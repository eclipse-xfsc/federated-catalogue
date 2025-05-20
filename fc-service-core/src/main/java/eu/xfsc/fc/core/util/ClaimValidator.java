package eu.xfsc.fc.core.util;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStatement;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.util.ModelPrinter;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import com.apicatalog.rdf.RdfValue;

import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass;
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
                org.apache.jena.shared.impl.JenaParameters.enableEagerLiteralValidation;
        jenaAcceptanceOfUnknownLiteralDatatypes =
                org.apache.jena.shared.impl.JenaParameters.enableSilentAcceptanceOfUnknownDatatypes;

        // Now switch to picky mode
        org.apache.jena.shared.impl.JenaParameters.enableEagerLiteralValidation = true;
        org.apache.jena.shared.impl.JenaParameters.enableSilentAcceptanceOfUnknownDatatypes = false;
    }

    private void resetJenaLiteralValidation() {
        org.apache.jena.shared.impl.JenaParameters.enableEagerLiteralValidation =
                eagerJenaLiteralValidation;
        org.apache.jena.shared.impl.JenaParameters.enableSilentAcceptanceOfUnknownDatatypes =
                jenaAcceptanceOfUnknownLiteralDatatypes;
    }

    /**
     * Validates if a claim elements are following the required syntax and
     * conditions before sending them to Neo4J
     *
     * @param sdClaimList the set of claims to be validated
     * @return the claim as a formatted triple string
     * @throws IOException 
     */
    public Model validateClaims(List<SdClaim> sdClaimList) { 
        Model listClaims = ModelFactory.createDefaultModel();
        StringBuilder payload = new StringBuilder();
        for (SdClaim sdClaim : sdClaimList) {
            validateRDFTripleSyntax(sdClaim);
            payload.append(sdClaim.asTriple());
        }
        InputStream in = IOUtils.toInputStream(payload, StandardCharsets.UTF_8);
        RDFDataMgr.read(listClaims, in, Lang.TTL);
        return listClaims;
    }

    /**
     * Method to validate that a claim follow the RDF triple syntax, i.e., <
     * (URI, blank node) , URI, (URI, blank node, literal) >
     *
     * @param sdClaim the claim to be validated
     */
    private void validateRDFTripleSyntax(SdClaim sdClaim) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = IOUtils.toInputStream(sdClaim.asTriple(), StandardCharsets.UTF_8)) {
            switchOnJenaLiteralValidation();
            RDFDataMgr.read(model, in, Lang.TTL);
        } catch (IOException | DatatypeFormatException | RiotException e) {
            log.debug("Error in Validating validateRDFTripleSyntax {}", e.getMessage());
            throw new QueryException(String.format("Triple %s has syntax error: %s", sdClaim.asTriple(), e.getMessage()));
        } finally {
            resetJenaLiteralValidation();
        }

    	URI uri;
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
            String subjectStr = sdClaim.getSubjectValue();
            try {
                uri = new URI(subjectStr);
            } catch (URISyntaxException e) {
                throw new QueryException(String.format("Subject in triple %s is not a valid URI", sdClaim.asTriple()));
            } // else it should be a blank node
        }
        // --- predicate --------------------------------------------------
        Node p = triple.getPredicate();
        if (p.isURI()) {
            // c.f. the comment for handling subject nodes above
            String predicateStr = sdClaim.getPredicateValue();
            try {
                uri = new URI(predicateStr);
            } catch (URISyntaxException e) {
                throw new QueryException(String.format("Predicate in triple %s is not a valid URI", sdClaim.asTriple()));
            }
        }
        // --- object -----------------------------------------------------
        Node o = triple.getObject();
        if (o.isURI()) {
            // c.f. the comment for handling subject nodes above
            String objectStr = sdClaim.getObjectValue();
            try {
                uri = new URI(objectStr);
            } catch (URISyntaxException e) {
                throw new QueryException(String.format("Object in triple %s is not a valid URI", sdClaim.asTriple()));
            }
        } else if (o.isLiteral()) {
            // Nothing needs to be done here as literal syntax errors and
            // datatype errors are already handled by the parser directly.
            // See the catch blocks after the RDFDataMgr.read( ) call above.

        } // else it's a blank node, which is OK
    }
    
    public Pair<String, Set<String>> resolveClaims(List<SdClaim> claims, String subject) {
        Model model = validateClaims(claims);
        String added = ExtendClaims.addPropertyGraphUri(model, subject);
        Set<String> props = ExtendClaims.getMultivalProp(model);
        return Pair.of(added, props);
    }
    
    
    /**
     * Method that validates a dataGraph against shaclShape
     *
     * @param payload    ContentAccessor of a self-Description payload to be validated
     * @param shaclShape ContentAccessor of a union schemas of type SHACL
     * @return SemanticValidationResult object
     */
    public static String validateClaimsBySchema(List<SdClaim> claims, ContentAccessor schema, StreamManager sm) {
      Model data = ModelFactory.createDefaultModel();
      Model shape = ModelFactory.createDefaultModel();
      RDFParser.create()
              .streamManager(sm)
              .source(schema.getContentAsStream())
              .lang(Lang.TURTLE)
              .parse(shape);

      RDFNode node;
      TypeMapper typeMapper = TypeMapper.getInstance();
      for (SdClaim claim: claims) {
        log.trace("validateClaimsBySchema; {}", claim);
        RdfValue object = claim.getObject();
        if (object.isLiteral()) {
          RDFDatatype objectType = typeMapper.getSafeTypeByName(object.asLiteral().getDatatype());
          log.trace("validateClaimsBySchema; objectType is: {}", objectType);
          node = createTypedLiteral(object.getValue(), objectType);
        } else {
          node = createResource(object.getValue());
        }
        Statement s = createStatement(createResource(claim.getSubject().getValue()), createProperty(claim.getPredicate().getValue()), node);
        data.add(s);
      }
      
      Resource reportResource = ValidationUtil.validateModel(data, shape, true);
      log.debug("validateClaimsBySchema; got result: {}", reportResource);
      data.close();
      shape.close();

      if (reportResource.getProperty(SH.conforms).getBoolean()) {
    	  return null;
      }
      return ModelPrinter.get().print(reportResource.getModel());
    }   
    
    private static final String CREDENTIAL_SUBJECT = "https://www.w3.org/2018/credentials#credentialSubject";
    
    public static TrustFrameworkBaseClass getSubjectType(ContentAccessor ontology, StreamManager sm, String subject, Map<TrustFrameworkBaseClass, String> сlassUris) {
        try {
          Model data = ModelFactory.createDefaultModel();
          RDFParser.create()
                  .streamManager(sm)
                  .source(new StringReader(subject))
                  .lang(Lang.JSONLD11)
                  .parse(data);

          NodeIterator node = data.listObjectsOfProperty(data.createProperty(CREDENTIAL_SUBJECT));
          while (node.hasNext()) {
            NodeIterator typeNode = data.listObjectsOfProperty(node.nextNode().asResource(), RDF.type);
            List <RDFNode> rdfNodeList = typeNode.toList();
            for (RDFNode rdfNode: rdfNodeList) {
              String resourceURI = rdfNode.asResource().getURI();
              // Check whether the type is or is at least derived from one of the base types according to the TrustFramework
              for (Map.Entry<TrustFrameworkBaseClass, String> classEntry: сlassUris.entrySet()) {
                if (checkTypeSubClass(ontology, resourceURI, classEntry.getValue())) {
                  return classEntry.getKey();
                }
              }
            }
          }
        } catch (Exception e) {
          log.debug("getSDType.error: {}", e.getMessage());
        }
        return null;
      }

    private static boolean checkTypeSubClass(ContentAccessor ontology, String type, String gaxType) {
        log.trace("checkTypeSubClass.enter; got type: {}, gaxType: {}", type, gaxType);
        if (type.equals(gaxType)) {
          return true;
        }
        
        String queryString = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                             "select ?uri where { ?uri rdfs:subClassOf <" + gaxType + ">}";
        Query query = QueryFactory.create(queryString);
        //ContentAccessor gaxOntology = schemaStore.getCompositeSchema(SchemaStore.SchemaType.ONTOLOGY);
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        model.read(new StringReader(ontology.getContentAsString()), null, Lang.TURTLE.getName());
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        ResultSet results = qe.execSelect();
        while (results.hasNext()) {
          QuerySolution q = results.next();
          String node =  q.get("uri").toString();
          if (node.equals(type)) {
            return true;
          }
        }
        return false;
    }    

}
