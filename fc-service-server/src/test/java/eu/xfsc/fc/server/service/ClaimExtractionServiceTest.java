package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.service.verification.claims.JenaAllTriplesExtractor;

/**
 * Unit tests for {@link ClaimExtractionService}.
 */
@ExtendWith(MockitoExtension.class)
class ClaimExtractionServiceTest {

  @Mock
  private JenaAllTriplesExtractor jenaExtractor;

  @InjectMocks
  private ClaimExtractionService claimExtractionService;

  /**
   * extractAllTriples delegates to the injected JenaAllTriplesExtractor.
   */
  @Test
  void extractAllTriples_delegatesToJenaExtractor() throws Exception {
    ContentAccessorDirect content = new ContentAccessorDirect("<> <> <> .", "text/turtle");
    List<RdfClaim> expected = List.of(new RdfClaim("<s>", "<p>", "\"o\""));
    when(jenaExtractor.extractClaims(any(ContentAccessor.class))).thenReturn(expected);

    List<RdfClaim> result = claimExtractionService.extractAllTriples(content);

    verify(jenaExtractor).extractClaims(content);
    assertEquals(expected, result);
  }

  /**
   * extractCredentialClaims returns empty list when all credential extractors fail.
   *
   * <p>Non-VC JSON-LD without a VC 2.0 context: {@link eu.xfsc.fc.core.service.verification.claims.CredentialSubjectClaimExtractor}
   * throws NPE (no {@code credentialSubject} key after expansion),
   * {@link eu.xfsc.fc.core.service.verification.claims.DanubeTechClaimExtractor}
   * throws {@link eu.xfsc.fc.core.exception.ClientException} (no VC 2.0 context).
   * Both exceptions are caught; the method returns an empty list.</p>
   */
  @Test
  void extractCredentialClaims_withNonVcContent_allExtractorsFail_returnsEmpty() {
    ContentAccessorDirect nonVcPayload = new ContentAccessorDirect(
        "{\"@id\": \"http://example.org/thing\"}");

    List<RdfClaim> result = claimExtractionService.extractCredentialClaims(nonVcPayload);

    assertTrue(result.isEmpty(), "All credential extractors fail on non-VC content → empty list");
  }
}
