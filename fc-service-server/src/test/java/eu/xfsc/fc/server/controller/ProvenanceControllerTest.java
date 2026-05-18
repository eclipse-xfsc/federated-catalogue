package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceCredentials;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests for the provenance sub-resource endpoints:
 * POST /assets/{id}/provenance, GET /assets/{id}/provenance,
 * GET /assets/{id}/provenance/{credentialId},
 * POST /assets/{id}/provenance/{credentialId}/verify,
 * POST /assets/{id}/provenance/verify.
 *
 * <p>Uses {@link MockitoSpyBean} to spy on {@link ProvenanceService} and {@link AssetStore}
 * so the controller routing and security rules can be tested without a real VC pipeline.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class ProvenanceControllerTest {

  private static final String PARTICIPANT_ID = "participant_id";
  private static final String TEST_ISSUER = "did:web:example.org:provenance-test";
  private static final String ASSET_IRI = "did:web:example.org:prov-test-asset";
  private static final String CREDENTIAL_ID = "did:vc:prov-cred-001";

  private static final String PROVENANCE_URL = "/assets/%s/provenance";
  private static final String SINGLE_CREDENTIAL_URL = "/assets/%s/provenance/%s";
  private static final String VERIFY_ONE_URL = "/assets/%s/provenance/%s/verify";
  private static final String VERIFY_ALL_URL = "/assets/%s/provenance/verify";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;

  @MockitoSpyBean
  private ProvenanceService provenanceService;
  @MockitoSpyBean
  private AssetStore assetStorePublisher;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @BeforeEach
  void resetSpies() {
    reset(provenanceService, assetStorePublisher);
  }

  // ===== POST /assets/{id}/provenance — happy path =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void addProvenanceCredential_validRequest_returnsCreated() throws Exception {
    final var assetMeta = assetMetaWithIssuer(TEST_ISSUER);
    doReturn(assetMeta).when(assetStorePublisher).getById(ASSET_IRI);
    doReturn(provenanceCredentialStub())
        .when(provenanceService).add(eq(ASSET_IRI), any(), anyString(), anyString());

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"]}")
            .with(csrf()))
        .andExpect(status().isCreated())
        .andReturn();

    final var credential = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceCredential.class);
    assertNotNull(credential.getId());
    assertEquals(ASSET_IRI, credential.getAssetId());
  }

  // ===== POST /assets/{id}/provenance — error cases =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void addProvenanceCredential_wrongCredentialSubjectId_returnsBadRequest() throws Exception {
    final var assetMeta = assetMetaWithIssuer(TEST_ISSUER);
    doReturn(assetMeta).when(assetStorePublisher).getById(ASSET_IRI);
    doThrow(new ClientException("credentialSubject.id mismatch"))
        .when(provenanceService).add(eq(ASSET_IRI), any(), anyString(), anyString());

    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"]}")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void addProvenanceCredential_duplicateCredentialId_returnsConflict() throws Exception {
    final var assetMeta = assetMetaWithIssuer(TEST_ISSUER);
    doReturn(assetMeta).when(assetStorePublisher).getById(ASSET_IRI);
    doThrow(new ConflictException("Provenance credential already exists: credentialId=" + CREDENTIAL_ID))
        .when(provenanceService).add(eq(ASSET_IRI), any(), anyString(), anyString());

    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"]}")
            .with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void addProvenanceCredential_unknownAsset_returnsNotFound() throws Exception {
    doThrow(new NotFoundException("Asset not found")).when(assetStorePublisher).getById(anyString());

    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"]}")
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  // ===== POST /assets/{id}/provenance — security =====

  @Test
  void addProvenanceCredential_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void addProvenanceCredential_wrongRole_returnsForbidden() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  // ===== POST /assets/{id}/provenance — boundary tests =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void addProvenanceCredential_versionZero_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .param("version", "0")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  // ===== GET /assets/{id}/provenance — happy path =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_existingAsset_returnsOk() throws Exception {
    doReturn(new ProvenanceCredentials(1, List.of(provenanceCredentialStub())))
        .when(provenanceService).list(eq(ASSET_IRI), any(), any());

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var credentials = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceCredentials.class);
    assertNotNull(credentials);
    assertEquals(1, credentials.getTotalCount());
    assertEquals(1, credentials.getItems().size());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_unknownAsset_returnsNotFound() throws Exception {
    doThrow(new NotFoundException("Asset not found")).when(provenanceService).list(eq(ASSET_IRI), any(), any());

    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  // ===== GET /assets/{id}/provenance — boundary tests =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_versionZero_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .param("version", "0")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_negativePageIndex_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .param("page", "-1")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_sizeZero_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .param("size", "0")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_sizeAboveMaximum_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .param("size", "101")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listProvenanceCredentials_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void listProvenanceCredentials_wrongRole_returnsForbidden() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(PROVENANCE_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  // ===== GET /assets/{id}/provenance/{credentialId} =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void getProvenanceCredential_existingCredential_returnsOk() throws Exception {
    doReturn(provenanceCredentialStub()).when(provenanceService).get(eq(ASSET_IRI), eq(CREDENTIAL_ID));

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/{id}/provenance/{credentialId}", ASSET_IRI, CREDENTIAL_ID)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var credential = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceCredential.class);
    assertNotNull(credential.getId());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void getProvenanceCredential_unknownCredential_returnsNotFound() throws Exception {
    doThrow(new NotFoundException("Provenance credential not found")).when(provenanceService).get(anyString(), anyString());

    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(SINGLE_CREDENTIAL_URL, encode(ASSET_IRI), encode("unknown-id")))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void getProvenanceCredential_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(SINGLE_CREDENTIAL_URL, encode(ASSET_IRI), encode(CREDENTIAL_ID)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // ===== POST /assets/{id}/provenance/{credentialId}/verify =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyProvenanceCredential_validCredential_returnsOk() throws Exception {
    doReturn(assetMetaWithIssuer(TEST_ISSUER)).when(assetStorePublisher).getById(ASSET_IRI);
    doReturn(verificationResultStub(true)).when(provenanceService).verifyOne(eq(ASSET_IRI), eq(CREDENTIAL_ID));

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .post("/assets/{id}/provenance/{credentialId}/verify", ASSET_IRI, CREDENTIAL_ID)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var verResult = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceVerificationResult.class);
    assertNotNull(verResult);
    assertEquals(Boolean.TRUE, verResult.getIsValid());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyProvenanceCredential_invalidSignature_returnsOkWithIsValidFalse() throws Exception {
    doReturn(assetMetaWithIssuer(TEST_ISSUER)).when(assetStorePublisher).getById(ASSET_IRI);
    doReturn(verificationResultStub(false)).when(provenanceService).verifyOne(eq(ASSET_IRI), eq(CREDENTIAL_ID));

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .post("/assets/{id}/provenance/{credentialId}/verify", ASSET_IRI, CREDENTIAL_ID)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var verResult = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceVerificationResult.class);
    assertEquals(Boolean.FALSE, verResult.getIsValid());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyProvenanceCredential_unknownCredential_returnsNotFound() throws Exception {
    doReturn(assetMetaWithIssuer(TEST_ISSUER)).when(assetStorePublisher).getById(ASSET_IRI);
    doThrow(new NotFoundException("Provenance credential not found")).when(provenanceService).verifyOne(anyString(), anyString());

    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ONE_URL, encode(ASSET_IRI), encode("unknown-id")))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void verifyProvenanceCredential_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ONE_URL, encode(ASSET_IRI), encode(CREDENTIAL_ID)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // ===== POST /assets/{id}/provenance/verify =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyAllProvenanceCredentials_allValid_returnsOkWithIsValidTrue() throws Exception {
    doReturn(assetMetaWithIssuer(TEST_ISSUER)).when(assetStorePublisher).getById(ASSET_IRI);
    doReturn(verificationResultStub(true)).when(provenanceService).verifyAll(eq(ASSET_IRI), any());

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ALL_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var verResult = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceVerificationResult.class);
    assertEquals(Boolean.TRUE, verResult.getIsValid());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyAllProvenanceCredentials_mixedResults_returnsOkWithIsValidFalse() throws Exception {
    doReturn(assetMetaWithIssuer(TEST_ISSUER)).when(assetStorePublisher).getById(ASSET_IRI);
    doReturn(verificationResultStub(false)).when(provenanceService).verifyAll(eq(ASSET_IRI), any());

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ALL_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var verResult = objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvenanceVerificationResult.class);
    assertEquals(Boolean.FALSE, verResult.getIsValid());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyAllProvenanceCredentials_unknownAsset_returnsNotFound() throws Exception {
    doThrow(new NotFoundException("Asset not found")).when(provenanceService).verifyAll(anyString(), any());

    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ALL_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void verifyAllProvenanceCredentials_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ALL_URL, encode(ASSET_IRI)))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // ===== POST /assets/{id}/provenance/verify — boundary tests =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = PARTICIPANT_ID, value = TEST_ISSUER)})))
  void verifyAllProvenanceCredentials_versionZero_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .post(String.format(VERIFY_ALL_URL, encode(ASSET_IRI)))
            .param("version", "0")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  // ===== Helpers =====

  private static AssetMetadata assetMetaWithIssuer(String issuer) {
    AssetMetadata meta = new AssetMetadata();
    meta.setId(ASSET_IRI);
    meta.setIssuer(issuer);
    return meta;
  }

  private static ProvenanceCredential provenanceCredentialStub() {
    ProvenanceCredential cred = new ProvenanceCredential();
    cred.setId(1L);
    cred.setAssetId(ASSET_IRI);
    cred.setAssetVersion(1);
    cred.setCredentialId(CREDENTIAL_ID);
    cred.setIssuer(TEST_ISSUER);
    cred.setIssuedAt(Instant.parse("2024-01-01T00:00:00Z"));
    cred.setProvenanceType(ProvenanceCredential.ProvenanceTypeEnum.CREATION);
    cred.setCredentialFormat(ProvenanceCredential.CredentialFormatEnum.JSONLD);
    cred.setVerified(false);
    return cred;
  }

  private static ProvenanceVerificationResult verificationResultStub(boolean isValid) {
    ProvenanceVerificationResult result = new ProvenanceVerificationResult();
    result.setIsValid(isValid);
    result.setVerificationTimestamp(Instant.now());
    result.setErrors(isValid ? List.of() : List.of("signature verification failed"));
    result.setWarnings(List.of());
    result.setValidatorDids(List.of());
    return result;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
