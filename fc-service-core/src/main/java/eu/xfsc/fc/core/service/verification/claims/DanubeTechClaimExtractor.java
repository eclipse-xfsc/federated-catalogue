package eu.xfsc.fc.core.service.verification.claims;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VP_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.danubetech.verifiablecredentials.CredentialSubject;
import com.danubetech.verifiablecredentials.VerifiableCredentialV2;
import com.danubetech.verifiablecredentials.VerifiablePresentationV2;
import com.fasterxml.jackson.core.type.TypeReference;
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

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DanubeTechClaimExtractor implements ClaimExtractor {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public List<RdfClaim> extractClaims(ContentAccessor content) throws Exception {
    log.debug("extractClaims.enter; got content: {}", content);
    String str = content.getContentAsString();
    Map<String, Object> raw = objectMapper.readValue(str, new TypeReference<>() {
    });

    if (!isVc2Context(raw)) {
      throw new ClientException("Unsupported credential format: VC 1.1 is no longer accepted");
    }
    return extractClaimsVc2(str, raw);
  }

  private boolean isVc2Context(Map<String, Object> raw) {
    Object ctx = raw.get("@context");
    if (ctx instanceof List) {
      return ((List<?>) ctx).contains(VC_20_CONTEXT);
    }
    return VC_20_CONTEXT.equals(ctx);
  }

  private List<RdfClaim> extractClaimsVc2(String str, Map<String, Object> raw) throws Exception {
    List<RdfClaim> claims = new ArrayList<>();
    Object typeObj = raw.get("type");
    boolean isVP = typeObj instanceof List
        ? ((List<?>) typeObj).contains(VP_TYPE)
        : VP_TYPE.equals(typeObj);

    List<CredentialSubject> subjects;
    if (isVP) {
      VerifiablePresentationV2 vp = VerifiablePresentationV2.fromJson(str);
      subjects = new ArrayList<>();
      for (VerifiableCredentialV2 vc2 : vp.getVerifiableCredentialAsList()) {
        subjects.addAll(vc2.getCredentialSubjectAsList());
      }
    } else {
      subjects = VerifiableCredentialV2.fromJson(str).getCredentialSubjectAsList();
    }

    for (CredentialSubject cs : subjects) {
      // var avoids explicit RdfNQuad import; method calls on inferred types require no import
      Model model = ModelFactory.createDefaultModel();
      for (var nquad : cs.toDataset().toList()) {
        log.debug("extractClaims (VC2); got NQuad: {}", nquad);
        var subjectVal = nquad.getSubject();
        Resource subjectRes = subjectVal.isBlankNode()
            ? model.createResource(new AnonId(subjectVal.getValue()))
            : model.createResource(subjectVal.getValue());
        Property property = model.createProperty(nquad.getPredicate().getValue());
        var objectVal = nquad.getObject();
        RDFNode objectNode;
        if (objectVal.isBlankNode()) {
          objectNode = model.createResource(new AnonId(objectVal.getValue()));
        } else if (objectVal.isLiteral()) {
          var literal = objectVal.asLiteral();
          String dtype = literal.getDatatype();
          if (dtype != null) {
            objectNode = model.createTypedLiteral(literal.getValue(),
                TypeMapper.getInstance().getSafeTypeByName(dtype));
          } else {
            String lang = literal.getLanguage().orElse(null);
            objectNode = lang != null
                ? model.createLiteral(literal.getValue(), lang)
                : model.createLiteral(literal.getValue());
          }
        } else {
          objectNode = model.createResource(objectVal.getValue());
        }
        Statement stmt = model.createStatement(subjectRes, property, objectNode);
        model.add(stmt);
        claims.add(new CredentialClaim(stmt, objectMapper));
      }
    }
    log.debug("extractClaims.exit; returning claims: {}", claims);
    return claims;
  }

}
