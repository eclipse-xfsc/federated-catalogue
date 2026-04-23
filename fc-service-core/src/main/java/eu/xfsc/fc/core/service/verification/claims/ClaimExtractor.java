package eu.xfsc.fc.core.service.verification.claims;

import java.util.List;

import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;

public interface ClaimExtractor {

    List<RdfClaim> extractClaims(ContentAccessor content) throws Exception;
    
}
