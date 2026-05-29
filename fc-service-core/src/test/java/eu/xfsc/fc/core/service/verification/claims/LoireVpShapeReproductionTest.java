package eu.xfsc.fc.core.service.verification.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import jakarta.json.JsonArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression pin for the VP-JWT extractor input shape: a raw JWT string in
 * {@code verifiableCredential} trips {@link JsonLdErrorCode#INVALID_CONTEXT_NULLIFICATION},
 * while an {@code EnvelopedVerifiableCredential} wrapper expands cleanly.
 */
class LoireVpShapeReproductionTest {

  private static final String RAW_JWT_IN_VC_ARRAY = """
      {
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "id": "urn:uuid:jwt-bdd-vp2-1",
        "type": ["VerifiablePresentation"],
        "verifiableCredential": [
          "eyJhbGciOiJFZERTQSIsImN0eSI6InZjIiwia2lkIjoiZGlkOndlYjpkaWQtc2VydmVyI2p3dC1rZXktMSIsInR5cCI6InZjK2p3dCJ9.eyJpc3MiOiJkaWQ6d2ViOmRpZC1zZXJ2ZXIifQ.sig"
        ]
      }
      """;

  private static final String ENVELOPED_VC_WRAPPER = """
      {
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "id": "urn:uuid:jwt-bdd-vp2-1",
        "type": ["VerifiablePresentation"],
        "verifiableCredential": [
          {
            "@context": "https://www.w3.org/ns/credentials/v2",
            "id": "data:application/vc+ld+json+jwt,eyJhbGciOiJub25lIn0.eyJ0ZXN0IjoiZml4dHVyZSJ9.",
            "type": "EnvelopedVerifiableCredential"
          }
        ]
      }
      """;

  @Test
  @DisplayName("Raw JWT string in verifiableCredential array trips INVALID_CONTEXT_NULLIFICATION")
  void rawJwtInVcArray_tripsInvalidContextNullification() {
    JsonLdError thrown = assertThrows(JsonLdError.class, () -> expand(RAW_JWT_IN_VC_ARRAY));
    assertEquals(JsonLdErrorCode.INVALID_CONTEXT_NULLIFICATION, thrown.getCode());
  }

  @Test
  @DisplayName("EnvelopedVerifiableCredential wrapper expands cleanly")
  void envelopedVcWrapper_expandsSuccessfully() throws JsonLdError {
    JsonArray expanded = expand(ENVELOPED_VC_WRAPPER);
    assertNotNull(expanded);
    assertFalse(expanded.isEmpty());
  }

  /**
   * Same call shape as {@link CredentialSubjectClaimExtractor#extractClaims}.
   */
  private static JsonArray expand(String jsonLd) throws JsonLdError {
    ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
    Document document = JsonDocument.of(content.getContentAsStream());
    return JsonLd.expand(document).get();
  }
}
