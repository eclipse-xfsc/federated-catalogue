package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.NonCredentialVerificationResult;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.riot.RiotException;
import org.springframework.stereotype.Component;

/**
 * Verification strategy for payloads the catalogue accepts as raw RDF assets rather than
 * Verifiable Credentials. Extracts every triple, applies the protected-namespace filter,
 * and returns a {@link NonCredentialVerificationResult}. No semantic, schema, or signature
 * verification — those concepts only apply to credentials.
 *
 * <p>Selected by {@link VerificationServiceImpl} when the format detector does not recognise
 * the payload as a credential (and the body is not an ambiguous JWT, which the credential
 * strategy rejects explicitly).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NonCredentialIngestionStrategy implements RdfIngestionStrategy {

  private final ClaimExtractionService claimExtractionService;
  private final ProtectedNamespaceFilter protectedNamespaceFilter;

  @Override
  public CredentialVerificationResult ingest(ContentAccessor payload,
                                             boolean verifySemantics, boolean verifySchema,
                                             boolean verifyVPSignatures, boolean verifyVCSignatures)
      throws VerificationException {
    try {
      List<RdfClaim> claims = claimExtractionService.extractAllTriples(payload);
      if (claims.isEmpty()) {
        throw new ClientException("Non-credential RDF content contains no triples");
      }
      FilteredClaims filtered =
          protectedNamespaceFilter.filterClaims(claims, "non-credential extraction");
      CredentialVerificationResult result = new NonCredentialVerificationResult(
          Instant.now(), AssetStatus.ACTIVE.getValue(), filtered.claims());
      if (filtered.hasWarning()) {
        result.setWarnings(List.of(filtered.warning()));
      }
      log.debug("ingest.exit; non-credential RDF, claims: {}",
          filtered.claims() == null ? "null" : filtered.claims().size());
      return result;
    } catch (ClientException ex) {
      throw ex;
    } catch (RiotException ex) {
      throw new ClientException("Non-credential RDF parse failed: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new ServerException("Failed to read non-credential RDF content", ex);
    }
  }
}
