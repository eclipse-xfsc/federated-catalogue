package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.NonCredentialVerificationResult;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;

import java.util.List;

import org.apache.jena.riot.RiotException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonCredentialIngestionStrategyTest {

  private static final String FILTER_CONTEXT = "non-credential extraction";

  @Mock
  private ClaimExtractionService claimExtractionService;

  @Mock
  private ProtectedNamespaceFilter protectedNamespaceFilter;

  @InjectMocks
  private NonCredentialIngestionStrategy strategy;

  @Test
  void ingest_noTriples_throwsClientException() {
    var payload = new ContentAccessorDirect("<turtle content>");
    when(claimExtractionService.extractAllTriples(payload)).thenReturn(List.of());

    assertThrows(ClientException.class,
        () -> strategy.ingest(payload, false, false, false, false));
  }

  @Test
  void ingest_validClaims_returnsNonCredentialResult() {
    var payload = new ContentAccessorDirect("<turtle content>");
    var claims = List.of(new RdfClaim("s", "p", "o"));
    when(claimExtractionService.extractAllTriples(payload)).thenReturn(claims);
    when(protectedNamespaceFilter.filterClaims(claims, FILTER_CONTEXT))
        .thenReturn(new FilteredClaims(claims, null));

    CredentialVerificationResult result = strategy.ingest(payload, false, false, false, false);

    assertInstanceOf(NonCredentialVerificationResult.class, result);
    assertNotNull(result.getGraphClaims());
    assertFalse(result.getGraphClaims().isEmpty());
  }

  @Test
  void ingest_filteredClaims_resultHasWarning() {
    var payload = new ContentAccessorDirect("<turtle content>");
    var rawClaims = List.of(new RdfClaim("s", "p", "o"), new RdfClaim("bad", "p", "o"));
    var safeClaims = List.of(new RdfClaim("s", "p", "o"));
    when(claimExtractionService.extractAllTriples(payload)).thenReturn(rawClaims);
    when(protectedNamespaceFilter.filterClaims(rawClaims, FILTER_CONTEXT))
        .thenReturn(new FilteredClaims(safeClaims, "1 triple(s) removed"));

    CredentialVerificationResult result = strategy.ingest(payload, false, false, false, false);

    assertInstanceOf(NonCredentialVerificationResult.class, result);
    assertNotNull(result.getWarnings());
    assertFalse(result.getWarnings().isEmpty());
  }

  @Test
  void ingest_riotParseError_throwsClientException() {
    var payload = new ContentAccessorDirect("<bad rdf>");
    when(claimExtractionService.extractAllTriples(payload))
        .thenThrow(new RiotException("parse error"));

    assertThrows(ClientException.class,
        () -> strategy.ingest(payload, false, false, false, false));
  }

  @Test
  void ingest_unexpectedError_throwsServerException() {
    var payload = new ContentAccessorDirect("<content>");
    when(claimExtractionService.extractAllTriples(payload))
        .thenThrow(new RuntimeException("unexpected"));

    assertThrows(ServerException.class,
        () -> strategy.ingest(payload, false, false, false, false));
  }

  @Test
  void ingest_verificationFlagsIgnored_resultIsNonCredential() {
    var payload = new ContentAccessorDirect("<content>");
    var claims = List.of(new RdfClaim("s", "p", "o"));
    when(claimExtractionService.extractAllTriples(payload)).thenReturn(claims);
    when(protectedNamespaceFilter.filterClaims(claims, FILTER_CONTEXT))
        .thenReturn(new FilteredClaims(claims, null));

    CredentialVerificationResult r1 = strategy.ingest(payload, true, true, true, true);
    CredentialVerificationResult r2 = strategy.ingest(payload, false, false, false, false);

    assertInstanceOf(NonCredentialVerificationResult.class, r1);
    assertInstanceOf(NonCredentialVerificationResult.class, r2);
  }
}
