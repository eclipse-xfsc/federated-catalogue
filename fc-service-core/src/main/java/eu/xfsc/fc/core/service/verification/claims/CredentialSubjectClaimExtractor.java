package eu.xfsc.fc.core.service.verification.claims;

import java.util.ArrayList;
import java.util.List;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.RdfClaim;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts RDF claims from the {@code credentialSubject} of a W3C Verifiable Credential
 * JSON-LD document. Uses Titanium JSON-LD for expansion and RDF conversion.
 */
@Slf4j
public class CredentialSubjectClaimExtractor implements ClaimExtractor {

  private static final String CREDENTIAL_SUBJECT_URI =
      "https://www.w3.org/2018/credentials#credentialSubject";
  private static final String VERIFIABLE_CREDENTIAL_URI =
      "https://www.w3.org/2018/credentials#verifiableCredential";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public List<RdfClaim> extractClaims(ContentAccessor content) throws Exception {
    log.debug("extractClaims.enter; got content: {}", content);
    List<RdfClaim> claims = new ArrayList<>();
    Document document = JsonDocument.of(content.getContentAsStream());
    JsonArray jsonLdArray = JsonLd.expand(document).get();
    log.trace("extractClaims; expanded: {}", jsonLdArray);
    JsonObject jsonLdObject = jsonLdArray.getFirst().asJsonObject();
    if (jsonLdObject.containsKey(VERIFIABLE_CREDENTIAL_URI)) {
      List<JsonValue> verifiableCredential = jsonLdObject.get(VERIFIABLE_CREDENTIAL_URI).asJsonArray();
      for (JsonValue vcValue : verifiableCredential) {
        JsonObject vc = vcValue.asJsonObject();
        JsonArray graph = vc.get("@graph").asJsonArray();
        for (JsonValue val : graph) {
          addClaims(claims, val.asJsonObject());
        }
      }
    } else {
      // content contains just single verifiable credential
      addClaims(claims, jsonLdObject);
    }
    log.debug("extractClaims.exit; returning claims: {}", claims.size());
    return claims;
  }

  private void addClaims(List<RdfClaim> claims, JsonObject verifiableCredential) throws JsonLdError {
    JsonArray credentialSubjects = verifiableCredential.getJsonArray(CREDENTIAL_SUBJECT_URI);
    for (JsonValue credentialSubject : credentialSubjects) {
      Document credentialSubjectDocument = JsonDocument.of(credentialSubject.asJsonObject());
      Model model = ModelFactory.createDefaultModel();
      JsonLd.toRdf(credentialSubjectDocument).produceGeneralizedRdf(true).provide(
        // using anonymous class to return this for chaining, as required by the RdfQuadConsumer interface
        new RdfQuadConsumer() {
          @Override
          public RdfQuadConsumer quad(String subject, String predicate, String object,
              String datatype, String language, String direction, String graph)
          {
            log.trace("extractClaims; got triple: {} {} {}", subject, predicate, object);
            Resource subjectResource = RdfQuadConsumer.isBlank(subject)
                ? model.createResource(new AnonId(subject))
                : model.createResource(subject);
            Property property = model.createProperty(predicate);
            RDFNode objectNode;
            if (language != null || direction != null) {
              objectNode = model.createLiteral(object, language);
            } else if (datatype != null) {
              objectNode = model.createTypedLiteral(object,
                  TypeMapper.getInstance().getSafeTypeByName(datatype));
            } else if (RdfQuadConsumer.isBlank(object)) {
              objectNode = model.createResource(new AnonId(object));
            } else {
              objectNode = model.createResource(object);
            }
            Statement stmt = model.createStatement(subjectResource, property, objectNode);
            model.add(stmt);
            claims.add(new CredentialClaim(stmt, objectMapper));
            return this;
          }
        });
    }
  }
}
