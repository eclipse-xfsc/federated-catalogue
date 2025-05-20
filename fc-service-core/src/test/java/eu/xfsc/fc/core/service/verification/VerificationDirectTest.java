package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static eu.xfsc.fc.core.util.DidUtils.resolveWebUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdEmbed;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.rdf.RdfDataset;
import com.apicatalog.rdf.RdfGraph;
import com.apicatalog.rdf.RdfNQuad;
import com.apicatalog.rdf.RdfTriple;
import com.apicatalog.rdf.RdfValue;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.danubetech.verifiablecredentials.validation.Validation;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VerificationDirectTest {
    
    // TODO: this test is to see how Neo4j works only, will be removed at some point later on
	
	@Test
	void convertDIDUri() throws Exception {
		String did = "did:web:example.com%3A3000:user:alice"; 
		URI uri = URI.create(did);
		URI durl = resolveWebUri(uri);
		assertEquals("https://example.com:3000/user/alice/did.json", durl.toString());
		did = "did:web:compliance.lab.gaia-x.eu";
		uri = URI.create(did);
		durl = resolveWebUri(uri);
		assertEquals("https://compliance.lab.gaia-x.eu/.well-known/did.json", durl.toString());
		did = "did:web:compliance.lab.gaia-x.eu#key-1";
		uri = URI.create(did);
		durl = resolveWebUri(uri);
		assertEquals("https://compliance.lab.gaia-x.eu/.well-known/did.json#key-1", durl.toString());
		did = "did:web:integration.gxfs.dev:api:dynamic:did:cloudService1#key-1";
		uri = URI.create(did);
		durl = resolveWebUri(uri);
		assertEquals("https://integration.gxfs.dev/api/dynamic/did/cloudService1/did.json#key-1", durl.toString());
	}
    
    @Test
    void parseJSONLDDirectly() throws Exception {
        //String path = "Claims-Extraction-Tests/participantTest.jsonld";
        String path = "Claims-Extraction-Tests/neo4jTest.jsonld";
        //String path = "Claims-Extraction-Tests/providerTest.jsonld";
        ContentAccessor content = getAccessor(VerificationDirectTest.class, path);

        // Read the file into an Object (The type of this object will be a List, Map, String, Boolean,
        // Number or null depending on the root object in the file).
        Object jsonObject = JsonUtils.fromInputStream(content.getContentAsStream());
        // Create a context JSON map containing prefixes and definitions
        //Map context = new HashMap();
        // Customise context...
        // Create an instance of JsonLdOptions with the standard JSON-LD options
        JsonLdOptions options = new JsonLdOptions();
        options.setProcessingMode(JsonLdOptions.JSON_LD_1_1);
        // Customise options...
        // Call whichever JSONLD function you want! (e.g. compact)
        //Object compact = JsonLdProcessor.compact(jsonObject, context, options);
        Object rdf = JsonLdProcessor.toRDF(jsonObject);
        // Print out the result (or don't, it's your call!)
        log.debug("RDF: {}; {}", rdf, JsonUtils.toPrettyString(rdf));
    }
    
    //@Test
    void parseJSONLDDirectly3() throws Exception {
        String path = "Claims-Extraction-Tests/providerTest.jsonld";
        ContentAccessor content = getAccessor(VerificationDirectTest.class, path);
        Object jsonObject = JsonUtils.fromInputStream(content.getContentAsStream());
        Object rdf = JsonLdProcessor.toRDF(jsonObject, new LoggingTripleCallback());
        log.debug("RDF: {}; {}", rdf, JsonUtils.toPrettyString(rdf));
    }
    
    @Test
    void extractClaimsDirectly() throws Exception {
        ContentAccessor content = getAccessor(VerificationDirectTest.class, "Claims-Extraction-Tests/providerTest.jsonld");
        VerifiablePresentation vp = VerifiablePresentation.fromJson(content.getContentAsString());
        Map<String, Object> claims = vp.getVerifiableCredential().getCredentialSubject().getClaims();
        log.debug("provider claims: {}", claims);
        log.debug("provider RDF: {}", vp.getVerifiableCredential().getCredentialSubject().toDataset().toList());

        content = getAccessor(VerificationDirectTest.class, "Claims-Extraction-Tests/participantTest.jsonld");
        vp = VerifiablePresentation.fromJson(content.getContentAsString());
        claims = vp.getVerifiableCredential().getCredentialSubject().getClaims();
        log.debug("participant claims: {}", claims);
        log.debug("participant RDF: {}", vp.getVerifiableCredential().getCredentialSubject().toDataset().toList());

        //content = getAccessor("Claims-Extraction-Tests/participantSD.jsonld");
        //vp = VerifiablePresentation.fromJson(content.getContentAsString());
        //claims = vp.getVerifiableCredential().getCredentialSubject().getClaims();
        //log.debug("big participant claims: {}", claims);
        //log.debug("big participant RDF: {}", vp.getVerifiableCredential().getCredentialSubject().toDataset().toList());
    }
    
    @Test
    void validateVP() throws Exception {
        //validate(VerifiableCredential verifiableCredential)
        ContentAccessor content = getAccessor(VerificationDirectTest.class, "VerificationService/jsonld/input.vp.jsonld");
        VerifiablePresentation vp = VerifiablePresentation.fromJson(content.getContentAsString());
        try {
            Validation.validate(vp);
            Assertions.assertNotNull(vp.getVerifiableCredential());
            Validation.validate(vp.getVerifiableCredential());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    void testExtract() throws Exception {
        //ContentAccessor content = getAccessor("Claims-Tests/legalPerson_two_VC.jsonld");
        //ContentAccessor content = getAccessor("Claims-Tests/credentialSubject2.jsonld");
        //ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantSD.jsonld");
        ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantTwoVCs.jsonld"); // participantTwoCSs.jsonld");
        Document doc = JsonDocument.of(content.getContentAsStream());
        com.apicatalog.jsonld.JsonLdOptions opts = new com.apicatalog.jsonld.JsonLdOptions();
        opts.setEmbed(JsonLdEmbed.ALWAYS);
        JsonArray arr = JsonLd.expand(doc).options(opts).get();
        log.debug("extractClaims; expanded: {}", arr);
        JsonObject vp = arr.get(0).asJsonObject();
        JsonArray vcs = vp.get("https://www.w3.org/2018/credentials#verifiableCredential").asJsonArray();
        List<SdClaim> claims = new ArrayList<>();
        Map<String, String> subMap = new HashMap<>();
        int idxVC = 0;
        for (JsonValue vcv: vcs) {
            log.debug("extractClaims; VCV: {}", vcv);
            JsonObject vc = vcv.asJsonObject();
            JsonArray graph = vc.get("@graph").asJsonArray();
            //log.debug("extractClaims; graph: {}", graph);
            for (JsonValue val: graph) {
                log.debug("extractClaims; VAL: {}", val);
                idxVC++;
                int idxCS = 0;
                JsonObject obj = val.asJsonObject();
                JsonArray css = obj.getJsonArray("https://www.w3.org/2018/credentials#credentialSubject");
                for (JsonValue cs: css) {
                    log.debug("extractClaims; CS: {}", cs);
                    idxCS++;
                    Document csDoc = JsonDocument.of(cs.asJsonObject());
                    RdfDataset rdf = JsonLd.toRdf(csDoc).produceGeneralizedRdf(true).get();
                    RdfGraph rdfGraph = rdf.getDefaultGraph();
                    List<RdfTriple> triples = rdfGraph.toList();
                    for (RdfTriple triple: triples) {
                        //log.debug("extractClaims; got triple: {}", triple);
                        String suffix = idxVC + "." + idxCS;
                        SdClaim claim = new SdClaim(rdf2String(triple.getSubject(), subMap, suffix), 
                                rdf2String(triple.getPredicate(), subMap, suffix), rdf2String(triple.getObject(), subMap, suffix));
                        claims.add(claim);
                        log.debug("extractClaims; claim: {}", claim);
                    }
                }
            }
        }
        
        Set<String> subjects = new HashSet<>();
        Set<String> objects = new HashSet<>();
        for (SdClaim claim : claims) {
          subjects.add(claim.getSubjectString());
          objects.add(claim.getObjectString());
        }

        subjects.removeAll(objects);
        log.debug("remains: {}", subjects);
    }

    @Test
    void testExpandTree() throws Exception {
        //ContentAccessor content = getAccessor(VerificationDirectTest.class, "Claims-Extraction-Tests/providerTest.jsonld");
        //ContentAccessor content = getAccessor("Claims-Tests/legalPerson_two_VC.jsonld");
        //ContentAccessor content = getAccessor("Claims-Tests/credentialSubject2.jsonld");
        ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantSD.jsonld");
        Document doc = JsonDocument.of(content.getContentAsStream());
        JsonObject vp = doc.getJsonContent().get().asJsonObject();
        log.debug("extractTree; VP: {}", vp);
        JsonValue val = vp.get("verifiableCredential");
        //log.debug("extractTree; VC: {}, type: {}", val, val.getValueType());
        List<JsonValue> vcs = val.getValueType() == ValueType.OBJECT ? List.of(val) : val.asJsonArray();
        for (JsonValue vcv: vcs) {
            JsonObject vc = vcv.asJsonObject();
            val = vc.get("credentialSubject");
            List<JsonValue> css = val.getValueType() == ValueType.OBJECT ? List.of(val) : val.asJsonArray();
            for (JsonValue csv: css) {
                JsonObject cs = csv.asJsonObject();
                Document csDoc = JsonDocument.of(cs);
                RdfDataset rdf = JsonLd.toRdf(csDoc).get(); // .produceGeneralizedRdf(true).get();
                List<RdfNQuad> quads = rdf.toList();
                for (RdfNQuad quad: quads) {
                    log.debug("extractClaims; got triple: {}", quad);
                }
            }
        }
    }
    
    private String rdf2String(RdfValue rdf, Map<String, String> subMap, String suffix) {
        if (rdf.isBlankNode()) {
            return subMap.computeIfAbsent(rdf.getValue(), k -> k + "." + suffix);
        }
        if (rdf.isLiteral()) return "\"" + rdf.getValue() + "\"";
        // rdf is IRI. here we could try to make it absolute..
        return "<" + rdf.getValue() + ">";
    }    
    
    @Test
    void testFlatten() throws Exception {
        //ContentAccessor content = getAccessor(VerificationDirectTest.class, "Claims-Extraction-Tests/providerTest.jsonld");
        ContentAccessor content = getAccessor("Claims-Tests/legalPerson_two_VC.jsonld");
        Document doc = JsonDocument.of(content.getContentAsStream());
        JsonStructure str = JsonLd.flatten(doc).get();
        log.debug("extractClaims; flatten: {}", str);
/*
        JsonObject vp = arr.get(0).asJsonObject();
        JsonArray vcs = vp.get("https://www.w3.org/2018/credentials#verifiableCredential").asJsonArray();
        for (JsonValue vcv: vcs) {
            JsonObject vc = vcv.asJsonObject();
            JsonArray graph = vc.get("@graph").asJsonArray();
            //log.debug("extractClaims; graph: {}", graph);
            for (JsonValue val: graph) {
                JsonObject obj = val.asJsonObject();
                JsonArray css = obj.getJsonArray("https://www.w3.org/2018/credentials#credentialSubject");
                for (JsonValue cs: css) {
                    Document csDoc = JsonDocument.of(cs.asJsonObject());
                    RdfDataset rdf = JsonLd.toRdf(csDoc).produceGeneralizedRdf(true).get();
                    RdfGraph rdfGraph = rdf.getDefaultGraph();
                    List<RdfTriple> triples = rdfGraph.toList();
                    for (RdfTriple triple: triples) {
                        log.debug("extractClaims; got triple: {}", triple);
                    }
                }
            }
        }
*/        
    }
    
    @Test
    void testJenaLoad() throws Exception {
        ContentAccessor content = getAccessor("Claims-Tests/legalPerson_two_VC.jsonld");
        Model model = ModelFactory.createDefaultModel();
        model.read(content.getContentAsStream(), null, "JSONLD11");
        
        //listStatements(model.listStatements(), 0);

        //StmtIterator stmts = model.listStatements( null, RDF.type, model.getResource( "http://www.w3.org/ns/dcat#" + "Dataset" ));
        StmtIterator stmts = model.listStatements();
        while ( stmts.hasNext() ) {
            rdfDFS( stmts.next().getSubject(), new HashSet<RDFNode>(), "" );
        }
        //model.write( System.out, "N3" );
    }
    
    private void listStatements(StmtIterator iter, int ident) {
        log.debug("list; enter: {}", ident);
        while (iter.hasNext()) {
            Statement stmt = iter.next();
            if (stmt.getObject().isResource()) {
                log.debug("Jena; resource statement: {}", stmt);
                listStatements(stmt.getObject().asResource().listProperties(), ident + 2);
            } else {
                log.debug("Jena; non-resource statement: {}", stmt);
            }
        }
        log.debug("list; exit: {}", ident);
    }
    
    public static void rdfDFS( RDFNode node, Set<RDFNode> visited, String prefix ) {
        if ( visited.contains( node )) {
            return;
        } else {
            visited.add( node );
            //System.out.println( prefix + node );
            log.debug("prefix: {}, node: {}", prefix, node);
            if ( node.isResource() ) {
                StmtIterator stmts = node.asResource().listProperties();
                while ( stmts.hasNext() ) {
                    Statement stmt = stmts.next();
                    rdfDFS( stmt.getObject(), visited, prefix + node + " =[" + stmt.getPredicate() + "]=> " );
                }
            }
        }
    }   
    
}
 