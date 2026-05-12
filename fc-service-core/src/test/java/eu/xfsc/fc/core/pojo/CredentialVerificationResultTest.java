package eu.xfsc.fc.core.pojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;


class CredentialVerificationResultTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final String ISSUER = "did:web:example.com";
  private static final String SUBJECT_ID = "https://example.com/subject1";
  private static final String PROFILE_ID = "gaia-x-2511";

  private CredentialVerificationResult minimal(String role) {
    // Constructor signature changes as part of this story:
    // graphClaims (List<RdfClaim>) replaces the old claims param; role and frameworkProfileId are new.
    return new CredentialVerificationResult(
        NOW, "active", ISSUER, NOW, SUBJECT_ID,
        List.of(), List.of(),
        role, PROFILE_ID);
  }

  @Test
  void getRole_constructedWithRole_returnsRole() {
    CredentialVerificationResult result = minimal("Participant");

    assertEquals("Participant", result.getRole());
  }

  @Test
  void getFrameworkProfileId_constructedWithProfileId_returnsProfileId() {
    CredentialVerificationResult result = minimal("ServiceOffering");

    assertEquals(PROFILE_ID, result.getFrameworkProfileId());
  }

  @Test
  void getClaims_withRawClaimsSet_returnsMapEntries() {
    CredentialVerificationResult result = minimal("Participant");
    result.setClaims(Map.of("gx:legalName", "ACME Corp"));

    Map<String, Object> claims = result.getClaims();

    assertNotNull(claims);
    assertEquals("ACME Corp", claims.get("gx:legalName"));
  }

  @Test
  void getClaims_notSet_returnsEmptyMap() {
    CredentialVerificationResult result = minimal("Resource");

    assertNotNull(result.getClaims());
    assertEquals(0, result.getClaims().size());
  }

  @Test
  void getName_setForParticipant_returnsName() {
    CredentialVerificationResult result = minimal("Participant");
    result.setName("ACME Corp");

    assertEquals("ACME Corp", result.getName());
  }

  @Test
  void getPublicKey_setForParticipant_returnsValidatorDidUri() {
    CredentialVerificationResult result = minimal("Participant");
    result.setPublicKey("did:web:validator.example.com");

    assertEquals("did:web:validator.example.com", result.getPublicKey());
  }

  @Test
  void getName_notSet_returnsNull() {
    CredentialVerificationResult result = minimal("ServiceOffering");

    assertNull(result.getName());
  }

  @Test
  void getPublicKey_notSet_returnsNull() {
    CredentialVerificationResult result = minimal("Resource");

    assertNull(result.getPublicKey());
  }

  @Test
  void getName_setOnBaseClass_isReturned() {
    CredentialVerificationResult result = minimal("Participant");
    result.setName("ACME Corp");
    result.setPublicKey("did:web:validator.example.com");

    assertEquals("ACME Corp", result.getName());
    assertEquals("did:web:validator.example.com", result.getPublicKey());
  }

  @Test
  void getRole_nonCredentialResult_returnsNull() {
    NonCredentialVerificationResult result = new NonCredentialVerificationResult(
        NOW, "active", List.of());

    assertNull(result.getRole());
    assertNull(result.getFrameworkProfileId());
  }

  @Test
  void getGraphClaims_setToNull_returnsEmptyList() {
    CredentialVerificationResult result = minimal("Participant");
    result.setGraphClaims(null);

    List<RdfClaim> claims = result.getGraphClaims();

    assertNotNull(claims);
    assertTrue(claims.isEmpty());
  }

  @Test
  void getClaims_constructedWithGraphClaims_populatedFromPredicateObjectValues() {
    List<RdfClaim> graphClaims = List.of(
        new RdfClaim("<https://example.com/subject>", "<https://schema.org/name>", "\"ACME Corp\""),
        new RdfClaim("<https://example.com/subject>", "<https://schema.org/description>", "\"A description\"")
    );

    CredentialVerificationResult result = new CredentialVerificationResult(
        NOW, "active", ISSUER, NOW, SUBJECT_ID, graphClaims, List.of(), "Participant", PROFILE_ID);

    Map<String, Object> claims = result.getClaims();
    assertEquals("ACME Corp", claims.get("https://schema.org/name"));
    assertEquals("A description", claims.get("https://schema.org/description"));
  }
}
