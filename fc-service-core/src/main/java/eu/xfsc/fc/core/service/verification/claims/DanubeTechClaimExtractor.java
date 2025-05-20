package eu.xfsc.fc.core.service.verification.claims;

import static com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialKeywords.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.apicatalog.rdf.RdfNQuad;
import com.danubetech.verifiablecredentials.CredentialSubject;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DanubeTechClaimExtractor implements ClaimExtractor {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public List<SdClaim> extractClaims(ContentAccessor content) throws Exception {
        log.debug("extractClaims.enter; got content: {}", content);
        List<SdClaim> claims = new ArrayList<>();
        VerifiablePresentation vp = VerifiablePresentation.fromJson(content.getContentAsString());
        Map<String, Object> vpm = vp.getJsonObject();
        List<Map<String, Object>> vcms;
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
        CredentialSubject cs;
        List<Map<String, Object>> csms;
        for (Map<String, Object> vcm: vcms) {
            obj = vcm.get(JSONLD_TERM_CREDENTIALSUBJECT);
            log.trace("extractClaims; got CS: {}", obj);
            if (obj instanceof Map) {
                csms = List.of((Map<String, Object>) obj);
            } else if (obj instanceof List) {
                csms = (List<Map<String, Object>>) obj;
            } else {
                // cs is a String ?
                continue;
            }
            
            for (Map<String, Object> csm: csms) {
                cs = CredentialSubject.fromMap(csm);
                log.trace("extractClaims; CS claims: {}", cs.getClaims());
                for (RdfNQuad nquad: cs.toDataset().toList()) {
                    log.debug("extractClaims; got NQuad: {}", nquad);
                    SdClaim claim = new SdClaim(nquad, objectMapper);
                    claims.add(claim);
                }
            }
        }
        log.debug("extractClaims.exit; returning claims: {}", claims);
        return claims;
    }

     
    
}
