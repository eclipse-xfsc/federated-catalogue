package eu.xfsc.fc.core.service.verification.claims;

import java.util.Collections;
import java.util.List;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.RdfClaim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Consolidates RDF claim extraction logic for both credential and non-credential payloads.
 *
 * <p>Credential extraction tries {@link CredentialSubjectClaimExtractor} first (Titanium JSON-LD),
 * falling back to {@link DanubeTechClaimExtractor} (Danube Tech LD library). Non-credential
 * extraction delegates to {@link JenaAllTriplesExtractor} for generic RDF formats.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimExtractionService {

  private static final ClaimExtractor[] CREDENTIAL_EXTRACTORS = {
      new CredentialSubjectClaimExtractor(), new DanubeTechClaimExtractor()
  };

  private final JenaAllTriplesExtractor jenaExtractor;

  /**
   * Extracts claims from W3C Verifiable Credential payloads (JSON-LD).
   * Tries each credential-aware extractor in order until one succeeds.
   *
   * @param payload the credential content to extract claims from
   * @return extracted claims, or an empty list if all extractors fail
   */
  public List<RdfClaim> extractCredentialClaims(ContentAccessor payload) {
    List<RdfClaim> claims = Collections.emptyList();
    for (ClaimExtractor extractor : CREDENTIAL_EXTRACTORS) {
      try {
        List<RdfClaim> result = extractor.extractClaims(payload);
        if (result != null && !result.isEmpty()) {
          claims = result;
          break;
        }
      } catch (Exception ex) {
        log.debug("extractCredentialClaims; {} did not extract claims: {}", extractor.getClass().getSimpleName(),
            ex.getMessage());
      }
    }
    return claims;
  }

  /**
   * Extracts all RDF triples from non-credential RDF content (JSON-LD, Turtle, N-Triples, RDF/XML).
   *
   * @param payload the RDF content to extract triples from
   * @return extracted claims
   */
  public List<RdfClaim> extractAllTriples(ContentAccessor payload) {
    return jenaExtractor.extractClaims(payload);
  }
}
