package eu.xfsc.fc.server.controller;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_DELETE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetUploadControllerTest {

    private static final String TEST_ISSUER = "http://example.org/test-issuer";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssetStore assetStorePublisher;
    @Autowired
    private GraphStore graphStore;

    @BeforeAll
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_plainText_returnsCreated() throws Exception {
        byte[] content = "Hello, this is a plain text template.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "template.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertNotNull(asset.getId());
        assertTrue(asset.getId().startsWith("urn:uuid:"), "Non-RDF asset ID should be UUID URN, got: " + asset.getId());
        assertTrue(asset.getId().matches("urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "UUID URN should match RFC 4122 format, got: " + asset.getId());
        assertEquals("text/plain", asset.getContentType());
        assertEquals(content.length, asset.getFileSize());
        assertNull(asset.getValidatorDids());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_pdf_returnsCreated() throws Exception {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A}; // %PDF-1.4\n
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("application/pdf", asset.getContentType());
        assertEquals(HashUtils.calculateSha256AsHex(content), asset.getAssetHash());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadJson_plainJsonNoContext_returnsCreated() throws Exception {
        byte[] content = "{\"name\": \"contract\", \"version\": 1}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "contract.json", "application/json", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("application/json", asset.getContentType());
        assertNull(asset.getValidatorDids());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadOctetStream_validContent_returnsCreated() throws Exception {
        byte[] content = "raw binary content for testing".getBytes(StandardCharsets.UTF_8);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(content)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals(HashUtils.calculateSha256AsHex(content), asset.getAssetHash());
        assertEquals(content.length, asset.getFileSize());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_duplicateAsset_returnsConflict() throws Exception {
        byte[] content = "duplicate-test-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "dup.txt", "text/plain", content);

        MvcResult firstResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(firstResult.getResponse().getContentAsString(), Asset.class);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_withWrongRole_returnsForbidden() throws Exception {
        byte[] content = "wrong role test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void uploadMultipart_withoutAuth_returnsUnauthorized() throws Exception {
        byte[] content = "unauthorized test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_withAssetAdminRole_returnsCreated() throws Exception {
        byte[] content = "asset-admin upload test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "admin.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_withJsonLd_returnsOkWithEnrichmentResponse() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "plain text document".getBytes(StandardCharsets.UTF_8));
        String assetId = createdAsset.getId();

        try {
            String rdfPayload = """
                {
                  "@context": {"ex": "http://example.org/"},
                  "@id": "%s",
                  "ex:title": "Enriched Document",
                  "ex:creator": "Test User"
                }
                """.formatted(assetId);

            AssetEnrichmentResponse response = enrichAssetMultipart("metadata.jsonld", "application/ld+json",
                rdfPayload.getBytes(StandardCharsets.UTF_8));

            assertNotNull(response);
            assertEquals(assetId, response.getAssetId());
            assertEquals(2, response.getTriplesAdded()); // @id, @type for the object
            assertEquals(0, response.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_withTurtle_returnsOkWithEnrichmentResponse() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("data.bin", "application/octet-stream",
            "binary data".getBytes(StandardCharsets.UTF_8));
        String assetId = createdAsset.getId();

        try {
            String turtlePayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:hasVersion "1.0" .
                <%s> ex:hasStatus "active" .
                """.formatted(assetId, assetId);

            AssetEnrichmentResponse response = enrichAssetMultipart("metadata.ttl", "text/turtle",
                turtlePayload.getBytes(StandardCharsets.UTF_8));

            assertNotNull(response);
            assertEquals(assetId, response.getAssetId());
            assertEquals(2, response.getTriplesAdded()); // two explicit triples
            assertEquals(0, response.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_viaMultipartWithTurtle_returnsOkWithEnrichmentResponse() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "document content".getBytes(StandardCharsets.UTF_8));
        String assetId = createdAsset.getId();

        try {
            String turtlePayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:description "Enriched via multipart" .
                """.formatted(assetId);

            AssetEnrichmentResponse response = enrichAssetMultipart("metadata.ttl", "text/turtle",
                turtlePayload.getBytes(StandardCharsets.UTF_8));

            assertNotNull(response);
            assertEquals(assetId, response.getAssetId());
            assertTrue(response.getTriplesAdded() > 0);
            assertEquals(0, response.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_withDifferentSubject_createsNewRdfAsset() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "document".getBytes(StandardCharsets.UTF_8));

        try {
            // RDF with different subject — creates a new RDF asset, not enrichment
            String rdfPayload = """
                {
                  "@context": {"ex": "http://example.org/"},
                  "@id": "http://different.org/asset/123",
                  "ex:title": "Different Asset"
                }
                """;
            Asset newAsset = createNonRdfAssetMultipart("metadata.jsonld", "application/ld+json",
                rdfPayload.getBytes(StandardCharsets.UTF_8));
            assertNotEquals(createdAsset.getId(), newAsset.getId());
            deleteAssetQuietly(newAsset.getAssetHash());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_withInvalidRdf_returnsBadRequest() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "document".getBytes(StandardCharsets.UTF_8));

        try {
            // 2. Try to enrich with malformed RDF
            byte[] invalidRdf = "{ invalid json-ld }".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "bad.jsonld", "application/ld+json", invalidRdf);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrich_humanReadableAsset_returnsUnprocessableEntity() throws Exception {
        Asset mrAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "machine-readable document".getBytes(StandardCharsets.UTF_8));
        String mrId = mrAsset.getId();

        // Upload a human-readable asset linked to the MR asset
        byte[] hrContent = "<html><body>Human Readable</body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile hrFile = new MockMultipartFile("file", "doc.html", "text/html", hrContent);
        String encodedMrId = java.net.URLEncoder.encode(mrId, StandardCharsets.UTF_8);

        MvcResult hrResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets/" + encodedMrId + "/human-readable")
                .file(hrFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset hrAsset = objectMapper.readValue(hrResult.getResponse().getContentAsString(), Asset.class);
        String hrId = hrAsset.getId();

        try {
            // Attempt to enrich the HR asset with RDF — must return 422 with an explanatory message
            String rdfPayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:title "HR Document" .
                """.formatted(hrId);
            byte[] rdfContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "meta.ttl", "text/turtle", rdfContent);

            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

            Error error = objectMapper.readValue(result.getResponse().getContentAsString(), Error.class);
            assertEquals("verification_error", error.getCode());
            assertTrue(error.getMessage().toLowerCase().contains("human-readable"),
                "422 response should explain why enrichment is rejected; got: " + error.getMessage());
            assertTrue(error.getMessage().toLowerCase().contains("cannot be enriched"),
                "422 response should explain why enrichment is rejected; got: " + error.getMessage());
        } finally {
            deleteAssetQuietly(hrAsset.getAssetHash());
            deleteAssetQuietly(mrAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_reEnriched_priorTriplesReplaced() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "document content".getBytes(StandardCharsets.UTF_8));
        String assetId = createdAsset.getId();

        try {
            String firstPayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:version "1.0" .
                """.formatted(assetId);
            AssetEnrichmentResponse first = enrichAssetMultipart("v1.ttl", "text/turtle",
                firstPayload.getBytes(StandardCharsets.UTF_8));
            assertEquals(1, first.getTriplesAdded());

            // Second enrichment: 2 triples — prior triples must be replaced
            String secondPayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:version "2.0" .
                <%s> ex:status "active" .
                """.formatted(assetId, assetId);
            AssetEnrichmentResponse second = enrichAssetMultipart("v2.ttl", "text/turtle",
                secondPayload.getBytes(StandardCharsets.UTF_8));

            assertEquals(assetId, second.getAssetId());
            assertEquals(2, second.getTriplesAdded());
            assertEquals(0, second.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_withHumanReadableLinked_preservesLinkTriples() throws Exception {
        // Human-readable link triples are stored under the MR asset's credentialSubject.
        // Enrichment does deleteClaims(mrIri) + addClaims(...), which could otherwise remove those link triples.
        // Verify they survive the enrichment cycle.
        final String fcmetaHasHumanReadable =
            "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#hasHumanReadable";
        final String credSubjectUri = "https://www.w3.org/2018/credentials#credentialSubject";

        Asset mrAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "machine-readable document".getBytes(StandardCharsets.UTF_8));
        String mrId = mrAsset.getId();

        byte[] hrContent = "<html><body>HR attachment</body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile hrFile = new MockMultipartFile("file", "doc.html", "text/html", hrContent);
        String encodedMrId = java.net.URLEncoder.encode(mrId, StandardCharsets.UTF_8);

        MvcResult hrResult = mockMvc.perform(MockMvcRequestBuilders
                .multipart("/assets/" + encodedMrId + "/human-readable")
                .file(hrFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();
        Asset hrAsset = objectMapper.readValue(hrResult.getResponse().getContentAsString(), Asset.class);

        String linkTripleQuery = """
            SELECT ?s ?p ?o WHERE {
              <<(?s ?p ?o)>> <%s> <%s> .
              FILTER(?s = <%s> && ?p = <%s>)
            }
            """.formatted(credSubjectUri, mrId, mrId, fcmetaHasHumanReadable);

        try {
            String hrIriBefore = queryHasHumanReadableObject(linkTripleQuery);
            assertEquals(hrAsset.getId(), hrIriBefore,
                "Precondition: hasHumanReadable triple must point at the linked HR IRI before enrichment");

            String rdfPayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:title "Enriched MR with HR link" .
                """.formatted(mrId);
            AssetEnrichmentResponse enrichResponse = enrichAssetMultipart("meta.ttl", "text/turtle",
                rdfPayload.getBytes(StandardCharsets.UTF_8));
            assertEquals(mrId, enrichResponse.getAssetId());

            String hrIriAfter = queryHasHumanReadableObject(linkTripleQuery);
            assertEquals(hrAsset.getId(), hrIriAfter,
                "hasHumanReadable triple must still point at the same HR IRI after enrichment");
        } finally {
            deleteAssetQuietly(hrAsset.getAssetHash());
            deleteAssetQuietly(mrAsset.getAssetHash());
        }
    }

    private String queryHasHumanReadableObject(String linkTripleQuery) {
        PaginatedResults<Map<String, Object>> results = graphStore.queryData(
            new GraphQuery(linkTripleQuery, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false));
        return results.getResults().stream()
            .map(row -> String.valueOf(row.get("o")))
            .findFirst()
            .orElse(null);
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_byNonOwner_returnsForbidden() throws Exception {
        // Seed an asset owned by a different participant (bypassing the controller so we can set the issuer).
        String otherParticipant = "http://example.org/other-issuer";
        byte[] content = "other participant's document".getBytes(StandardCharsets.UTF_8);
        String subjectId = "urn:uuid:11111111-1111-1111-1111-111111111111";
        AssetMetadata seed = new AssetMetadata(HashUtils.calculateSha256AsHex(content), subjectId,
            AssetStatus.ACTIVE, otherParticipant, null, Instant.now(), Instant.now(),
            new ContentAccessorBinary(content));
        seed.setContentType("text/plain");
        seed.setFileSize((long) content.length);
        AssetMetadata stored = assetStorePublisher.storeUnverified(seed, "other.txt");

        try {
            String rdfPayload = """
                @prefix ex: <http://example.org/> .
                <%s> ex:title "Attempted cross-participant enrichment" .
                """.formatted(stored.getId());
            MockMultipartFile rdfFile = new MockMultipartFile("file", "meta.ttl", "text/turtle",
                rdfPayload.getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        } finally {
            deleteAssetQuietly(stored.getAssetHash());
        }
    }

    @Test
    public void enrichNonRdfAsset_withoutAuth_returnsUnauthorized() throws Exception {
        byte[] plainContent = "plain text document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        String rdfPayload = "{"
            + "  \"@context\": {\"ex\": \"http://example.org/\"},"
            + "  \"@id\": \"urn:uuid:12345678-1234-1234-1234-123456789012\","
            + "  \"ex:title\": \"Enriched Document\""
            + "}";
        byte[] rdfContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile rdfFile = new MockMultipartFile("file", "metadata.jsonld", "application/ld+json", rdfContent);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(rdfFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void createHumanReadableAsset_withoutAuth_returnsUnauthorized() throws Exception {
        String assetId = "urn:uuid:12345678-1234-1234-1234-123456789012";
        String encodedId = java.net.URLEncoder.encode(assetId, StandardCharsets.UTF_8);
        byte[] hrContent = "<html><body>Human Readable</body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile hrFile = new MockMultipartFile("file", "doc.html", "text/html", hrContent);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets/" + encodedId + "/human-readable")
                .file(hrFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void updateHumanReadableAsset_withoutAuth_returnsUnauthorized() throws Exception {
        String assetId = "urn:uuid:12345678-1234-1234-1234-123456789012";
        String encodedId = java.net.URLEncoder.encode(assetId, StandardCharsets.UTF_8);
        byte[] hrContent = "<html><body>Updated Human Readable</body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile hrFile = new MockMultipartFile("file", "doc.html", "text/html", hrContent);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets/" + encodedId + "/human-readable")
                .file(hrFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_DELETE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
            @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteNonRdfAsset_withEnrichment_deletesEnrichmentTriples() throws Exception {
        Asset createdAsset = createNonRdfAssetMultipart("doc.txt", "text/plain",
            "plain text document".getBytes(StandardCharsets.UTF_8));
        String assetId = createdAsset.getId();
        String assetHash = createdAsset.getAssetHash();

        try {
            String rdfPayload = """
                {
                  "@context": {"ex": "http://example.org/"},
                  "@id": "%s",
                  "ex:title": "Enriched Document"
                }
                """.formatted(assetId);
            AssetEnrichmentResponse enrichResponse = enrichAssetMultipart("metadata.jsonld", "application/ld+json",
                rdfPayload.getBytes(StandardCharsets.UTF_8));
            assertEquals(assetId, enrichResponse.getAssetId());
            assertTrue(enrichResponse.getTriplesAdded() > 0);

            // Act: delete the asset — this is the path that should also drop enrichment triples from the graph store.
            mockMvc.perform(MockMvcRequestBuilders.delete("/assets/" + assetHash)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // Assert: asset no longer present.
            mockMvc.perform(MockMvcRequestBuilders.get("/assets/" + java.net.URLEncoder.encode(assetId, StandardCharsets.UTF_8))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        } finally {
            deleteAssetQuietly(assetHash);
        }
    }

    private void deleteAssetQuietly(String hash) {
        try {
            assetStorePublisher.deleteAsset(hash);
        } catch (NotFoundException e) {
            // expected
        }
    }

    private Asset createNonRdfAssetMultipart(String filename, String contentType, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                        .file(file)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    }

    private AssetEnrichmentResponse enrichAssetMultipart(String filename, String contentType, byte[] rdfContent) throws Exception {
        MockMultipartFile rdfFile = new MockMultipartFile("file", filename, contentType, rdfContent);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                        .file(rdfFile)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AssetEnrichmentResponse.class);
    }
}