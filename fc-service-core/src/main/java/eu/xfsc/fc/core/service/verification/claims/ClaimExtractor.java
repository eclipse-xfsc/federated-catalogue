package eu.xfsc.fc.core.service.verification.claims;

import java.util.List;

import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;

public interface ClaimExtractor {

    List<CredentialClaim> extractClaims(ContentAccessor content) throws Exception;
    
}
