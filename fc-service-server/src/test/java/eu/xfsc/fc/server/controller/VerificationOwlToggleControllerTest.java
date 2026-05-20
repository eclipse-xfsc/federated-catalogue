package eu.xfsc.fc.server.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * End-to-end MockMvc proof that the OWL module toggle takes effect at the HTTP boundary:
 * a credential whose type is only reachable via a runtime-uploaded ontology resolves with
 * OWL enabled and is rejected with HTTP 400 when OWL is disabled. Registry-direct types
 * resolve regardless.
 *
 * <p>Companion to {@code CredentialVerificationStrategyOwlToggleTest} in fc-service-core,
 * which pins the same behaviour at the strategy layer (composite-schema spy assertions
 * plus role-resolution outcome assertions). This class adds the HTTP-status mapping
 * piece — that a {@code ResolvedRole.UNKNOWN} from the OWL-off path surfaces as
 * {@code 400 Bad Request} via {@code VerificationServiceImpl} +
 * {@code RestExceptionHandler}.
 *
 * <p><b>Fixture note:</b> the inline Turtle and JSON-LD here are a snapshot of the
 * current default bundle (Gaia-X 2511) — they assume {@code gx:LegalPerson} is a
 * registry-resolvable type via {@code initializeDefaultSchemas()}. If the default
 * bundle changes, swap {@code gx:LegalPerson} for whatever registry-direct type the
 * new bundle declares (and the prefix accordingly). The OWL toggle behaviour being
 * tested here is bundle-agnostic; only the fixtures are tied to the snapshot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class VerificationOwlToggleControllerTest {

  private static final String CUSTOM_PARTICIPANT_ONTOLOGY = """
      @prefix ext: <https://example.org/ext#> .
      @prefix gx: <https://w3id.org/gaia-x/2511#> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      <https://example.org/ext> a owl:Ontology .

      ext:CustomParticipant a owl:Class ;
          rdfs:subClassOf gx:LegalPerson .
      """;

  // Credential whose `credentialSubject.@type` is reachable only via the ontology above
  // (gx:LegalPerson is itself a subclass of gx:Participant in the gx-2511 bundle). With
  // OWL on this resolves to the Participant role; with OWL off it falls through to
  // ResolvedRole.UNKNOWN and the verification entry point rejects it.
  private static final String CUSTOM_PARTICIPANT_VP = """
      {
        "@context": "https://www.w3.org/ns/credentials/v2",
        "type": "VerifiablePresentation",
        "verifiableCredential": [
          {
            "@context": [
              "https://www.w3.org/ns/credentials/v2",
              {"ext": "https://example.org/ext#"}
            ],
            "type": ["VerifiableCredential"],
            "id": "https://example.org/credentials/custom-participant-1",
            "issuer": "did:web:example.org",
            "validFrom": "2024-06-01T00:00:00Z",
            "credentialSubject": {
              "id": "did:web:example.org:participant",
              "@type": "ext:CustomParticipant",
              "ext:name": "Example Custom Participant"
            }
          }
        ]
      }
      """;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SchemaStore schemaStore;

  @MockitoBean
  private SchemaModuleConfigService schemaModuleConfigService;

  @BeforeEach
  void seedRuntimeOntology() {
    // Production gx-2511 ontology + shapes are loaded by initializeDefaultSchemas;
    // re-running it is idempotent. Then the custom-subclass ontology is added on top.
    schemaStore.initializeDefaultSchemas();
    schemaStore.addSchema(new ContentAccessorDirect(CUSTOM_PARTICIPANT_ONTOLOGY));
    // Default toggles: SHACL and OWL on. Each test flips OWL independently.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);
  }

  @AfterEach
  void resetSchemaStore() {
    schemaStore.clear();
    schemaStore.initializeDefaultSchemas();
  }

  @Test
  void verify_customSubclassWithOwlDisabled_returns400() throws Exception {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    String response = mockMvc.perform(MockMvcRequestBuilders.post("/verification")
            .queryParam("verifySemantics", "false")
            .queryParam("verifyVPSignature", "false")
            .queryParam("verifyVCSignature", "false")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(CUSTOM_PARTICIPANT_VP)
            .with(csrf()))
        .andExpect(status().isBadRequest())
        .andReturn()
        .getResponse()
        .getContentAsString();

    Error error = objectMapper.readValue(response, Error.class);
    assertTrue(error.getMessage().toLowerCase().contains("not resolvable")
            || error.getMessage().toLowerCase().contains("unknown"),
        "Expected an unresolvable-type message but got: " + error.getMessage());
  }

  @Test
  void verify_customSubclassWithOwlEnabled_returns200() throws Exception {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);

    mockMvc.perform(MockMvcRequestBuilders.post("/verification")
            .queryParam("verifySemantics", "false")
            .queryParam("verifyVPSignature", "false")
            .queryParam("verifyVCSignature", "false")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(CUSTOM_PARTICIPANT_VP)
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
