package eu.xfsc.fc.server.service;

/*-
 * ---license-start
 * fc-service-server
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.AfterEach;
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

/**
 * Integration tests for AC-3 of the read-content fix: a metadata enrichment's RDF triples must
 * remain genuinely retrievable through the graph-query path, not merely forwarded to a mock.
 *
 * <p>Unlike {@link AssetEnrichmentReadContentTest}, which replaces the graph store with a Mockito
 * mock to exercise the write path without a backend, this class runs against the real embedded
 * Fuseki adapter ({@code graphstore.impl=fuseki}) so the enrichment claims are actually persisted
 * into and queried back out of a live SPARQL store — the guard the read-content fix must not defeat
 * by silently discarding the enrichment.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.EMBEDDED)
class AssetEnrichmentGraphStoreTest {

  private static final String TEST_ISSUER = "http://example.org/enrichment-graph-test-issuer";
  private static final String NON_RDF_CONTENT_TYPE = "text/plain";
  private static final String ENRICHMENT_CONTENT_TYPE = "application/ld+json";
  private static final String ENRICHMENT_TITLE_PREDICATE = "http://example.org/title";
  private static final String CRED_SUBJECT_URI = "https://www.w3.org/2018/credentials#credentialSubject";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;
  @Autowired
  private GraphStore graphStore;

  private MockMvc mockMvc;

  // Subjects enriched during a test, so the real (shared, in-memory-for-the-class) Fuseki
  // dataset can be scrubbed of this test's triples afterwards even though asset ids are
  // already unique per upload (UUID-based) and would not collide across test methods anyway.
  private final List<String> enrichedSubjects = new ArrayList<>();

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    assetStore.clear();
    enrichedSubjects.forEach(graphStore::deleteClaims);
    enrichedSubjects.clear();
  }

  // ===== Positive proof this class exercises the real backend, not the dummy store =====

  @Test
  void graphStoreBean_fusekiConfigured_reportsFusekiBackend() {
    assertEquals(GraphBackendType.FUSEKI, graphStore.getBackendType(),
        "this test class must be wired to the real Fuseki adapter (graphstore.impl=fuseki);"
            + " GraphBackendType.NONE here would mean assertions below are silently exercising"
            + " the disabled/dummy store instead");
  }

  // ===== AC-3: enrichment triples are genuinely queryable, and content read is unaffected =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void enrichAsset_singleEnrichment_triplesQueryableInRealGraphAndContentReadReturnsOriginalPayload()
      throws Exception {
    final String originalContent = "graph-backed original content " + UUID.randomUUID();
    final Asset created = uploadNonRdfAsset(originalContent);
    final String titleValue = "Queryable enrichment title";

    final AssetEnrichmentResponse response = enrichAsset(created.getId(),
        enrichmentPayload(created.getId(), titleValue));
    assertTrue(response.getTriplesAdded() > 0, "enrichment must report at least one triple added");

    // Half 1: the enrichment triple is genuinely retrievable via SPARQL against the real graph —
    // not merely "was addClaims() called", but "can the triple be queried back out afterwards".
    assertTrue(titleTripleQueryable(created.getId(), titleValue),
        "the enrichment triple (subject=" + created.getId() + ", predicate="
            + ENRICHMENT_TITLE_PREDICATE + ", object=" + titleValue + ") must be queryable in the"
            + " real graph store after enrichment; rows returned: " + queryClaimsBySubject(created.getId()));

    // Half 2: the read path must still return the original asset body, not the enrichment
    // document — proven simultaneously with the graph-side assertion above, against the same
    // enrichment call, against a real backend.
    final Map<String, Object> returned = readAssetById(created.getId());
    assertEquals(originalContent, returned.get("rawContent"),
        "GET /assets/{id} must keep returning the original asset content after enrichment,"
            + " even when the enrichment triples are genuinely persisted in a real graph store");
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void enrichAsset_severalSuccessiveEnrichments_onlyLatestQueryableContentUnchanged() throws Exception {
    final String originalContent = "graph-backed original content, multi-enrichment "
        + UUID.randomUUID();
    final Asset created = uploadNonRdfAsset(originalContent);

    // The write path (AssetUploadService#enrichAsset) calls graphStore.deleteClaims(subjectId)
    // before graphStore.addClaims(...) on every enrichment of the same subject. That is existing,
    // deliberate production behaviour (a re-enrichment fully replaces the subject's RDF node), not
    // something this fix touched — so the correct expectation for several successive enrichments
    // is that only the LAST enrichment's triples remain queryable; earlier ones are gone from the
    // graph. This mirrors, at the graph layer, the same "only the latest survives" semantics the
    // read-content fix restores at the content layer.
    //
    // Round one is asserted to have genuinely landed before it is overwritten: without that
    // intermediate check, a graph store that silently discarded round one and round two (writing
    // nothing) would look identical, at the final assertion below, to one that wrote and then
    // correctly replaced each in turn — exactly the "enrichment silently discarded" failure this
    // AC exists to catch.
    final AssetEnrichmentResponse roundOne = enrichAsset(created.getId(),
        enrichmentPayload(created.getId(), "Enrichment round one"));
    assertTrue(roundOne.getTriplesAdded() > 0, "round one must report at least one triple added");
    assertTrue(titleTripleQueryable(created.getId(), "Enrichment round one"),
        "round one's triple must be queryable before it is superseded by a later enrichment");

    final AssetEnrichmentResponse roundTwo = enrichAsset(created.getId(),
        enrichmentPayload(created.getId(), "Enrichment round two"));
    assertTrue(roundTwo.getTriplesAdded() > 0, "round two must report at least one triple added");

    final AssetEnrichmentResponse roundThree = enrichAsset(created.getId(),
        enrichmentPayload(created.getId(), "Enrichment round three"));
    assertTrue(roundThree.getTriplesAdded() > 0, "round three must report at least one triple added");

    final List<Map<String, Object>> rows = queryClaimsBySubject(created.getId());
    final List<Object> titleValues = rows.stream()
        .filter(row -> ENRICHMENT_TITLE_PREDICATE.equals(row.get("p")))
        .map(row -> row.get("o"))
        .toList();

    // A single-element list; direct equality is safe even though queryData() shuffles multi-row
    // results, since there is nothing here for a shuffle to reorder.
    assertEquals(List.of("Enrichment round three"), titleValues,
        "only the most recent enrichment's triples must remain queryable in the graph after"
            + " several successive enrichments of the same subject — deleteClaims() wipes the"
            + " prior enrichment's triples on every re-enrichment by design; rows were: " + rows);
    assertFalse(titleValues.contains("Enrichment round one")
            || titleValues.contains("Enrichment round two"),
        "earlier enrichments' triples must not still be queryable; rows were: " + rows);

    final Map<String, Object> returned = readAssetById(created.getId());
    assertEquals(originalContent, returned.get("rawContent"),
        "GET /assets/{id} must return the original content after several successive enrichments,"
            + " even though only the latest enrichment's triples remain in the real graph store");
  }

  // ===== Security =====

  @Test
  void readAssetById_noAuth_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/{id}", "urn:uuid:00000000-0000-0000-0000-000000000002")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // ===== helpers =====

  private Asset uploadNonRdfAsset(String text) throws Exception {
    final byte[] fileContent = text.getBytes(StandardCharsets.UTF_8);
    final MockMultipartFile file = new MockMultipartFile("file", "standalone.txt",
        NON_RDF_CONTENT_TYPE, fileContent);

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  private AssetEnrichmentResponse enrichAsset(String assetId, String rdfPayload) throws Exception {
    final byte[] fileContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
    final MockMultipartFile file = new MockMultipartFile("file", "metadata.jsonld",
        ENRICHMENT_CONTENT_TYPE, fileContent);

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    enrichedSubjects.add(assetId);
    return objectMapper.readValue(result.getResponse().getContentAsString(), AssetEnrichmentResponse.class);
  }

  @SuppressWarnings("unchecked") // generic Map deserialization is the only way to read rawContent,
  // a field present on the runtime AssetMetadata but absent from the generated Asset model
  private Map<String, Object> readAssetById(String assetId) throws Exception {
    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(assetId))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
  }

  /**
   * Queries the real graph store for every RDF-star-wrapped triple recorded under the given
   * enrichment subject, using the same SPARQL-star annotation pattern the production
   * {@code SparqlGraphStore} write path uses to tag each triple with its credential subject.
   *
   * @param subjectId the enrichment subject IRI (the enriched asset's id)
   * @return each matching triple as a row with {@code s}/{@code p}/{@code o} bindings
   */
  private List<Map<String, Object>> queryClaimsBySubject(String subjectId) {
    final String sparql = "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <" + CRED_SUBJECT_URI + "> <"
        + subjectId + "> }";
    return graphStore.queryData(
        new GraphQuery(sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false)
    ).getResults();
  }

  /**
   * Checks whether the enrichment title triple (subject, {@link #ENRICHMENT_TITLE_PREDICATE},
   * titleValue) is currently queryable in the real graph store for the given subject.
   */
  private boolean titleTripleQueryable(String subjectId, String titleValue) {
    return queryClaimsBySubject(subjectId).stream().anyMatch(row ->
        subjectId.equals(row.get("s"))
            && ENRICHMENT_TITLE_PREDICATE.equals(row.get("p"))
            && titleValue.equals(row.get("o")));
  }

  private String enrichmentPayload(String assetId, String titleValue) {
    return """
        {
          "@context": {"ex": "http://example.org/"},
          "@id": "%s",
          "ex:title": "%s"
        }
        """.formatted(assetId, titleValue);
  }

  private static String encode(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }
}
