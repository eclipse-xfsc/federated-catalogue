package eu.xfsc.fc.core.service.verification.claims;

import static com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialKeywords.*;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VP_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.apicatalog.rdf.RdfNQuad;
import com.danubetech.verifiablecredentials.CredentialSubject;
import com.danubetech.verifiablecredentials.VerifiableCredentialV2;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.danubetech.verifiablecredentials.VerifiablePresentationV2;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DanubeTechClaimExtractor implements ClaimExtractor {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public List<CredentialClaim> extractClaims(ContentAccessor content) throws Exception {
    log.debug("extractClaims.enter; got content: {}", content);
    String str = content.getContentAsString();
    Map<String, Object> raw = objectMapper.readValue(str, new TypeReference<>() {
    });

    if (isVc2Context(raw)) {
      return extractClaimsVc2(str, raw);
    }
    return extractClaimsVc1(str, raw);
  }

  private boolean isVc2Context(Map<String, Object> raw) {
    Object ctx = raw.get("@context");
    if (ctx instanceof List) {
      return ((List<?>) ctx).contains(VC_20_CONTEXT);
    }
    return VC_20_CONTEXT.equals(ctx);
  }

  private List<CredentialClaim> extractClaimsVc2(String str, Map<String, Object> raw) throws Exception {
    List<CredentialClaim> claims = new ArrayList<>();
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
      for (RdfNQuad nquad : cs.toDataset().toList()) {
        log.debug("extractClaims (VC2); got NQuad: {}", nquad);
        claims.add(new CredentialClaim(nquad, objectMapper));
      }
    }
    log.debug("extractClaims.exit; returning claims: {}", claims);
    return claims;
  }

  @SuppressWarnings("unchecked")
  private List<CredentialClaim> extractClaimsVc1(String str, Map<String, Object> raw) throws Exception {
    List<CredentialClaim> claims = new ArrayList<>();
    Object typeObj = raw.get("type");
    boolean isVP = typeObj instanceof List
        ? ((List<?>) typeObj).contains(VP_TYPE)
        : VP_TYPE.equals(typeObj);

    List<Map<String, Object>> vcms;
    if (isVP) {
      VerifiablePresentation vp = VerifiablePresentation.fromJson(str);
      Map<String, Object> vpm = vp.getJsonObject();
      Object obj = vp.getJsonObject().get(JSONLD_TERM_VERIFIABLECREDENTIAL);
      log.trace("extractClaims; got VC: {}", obj);
      if (obj instanceof Map) {
        vcms = List.of((Map<String, Object>) obj);
      } else if (obj instanceof List) {
        vcms = (List<Map<String, Object>>) obj;
      } else {
        // vc is a String ?
        vcms = List.of(vpm);
      }
    } else {
      // standalone VC 1.1 (not VP-wrapped)
      vcms = List.of(raw);
    }
    CredentialSubject cs;
    List<Map<String, Object>> csms;
    for (Map<String, Object> vcm : vcms) {
      Object obj = vcm.get(JSONLD_TERM_CREDENTIALSUBJECT);
      log.trace("extractClaims; got CS: {}", obj);
      if (obj instanceof Map) {
        csms = List.of((Map<String, Object>) obj);
      } else if (obj instanceof List) {
        csms = (List<Map<String, Object>>) obj;
      } else {
        // cs is a String ?
        continue;
      }

      for (Map<String, Object> csm : csms) {
        cs = CredentialSubject.fromMap(csm);
        log.trace("extractClaims; CS claims: {}", cs.getClaims());
        for (RdfNQuad nquad : cs.toDataset().toList()) {
          log.debug("extractClaims; got NQuad: {}", nquad);
          CredentialClaim claim = new CredentialClaim(nquad, objectMapper);
          claims.add(claim);
        }
      }
    }
    log.debug("extractClaims.exit; returning claims: {}", claims);
    return claims;
  }
}
