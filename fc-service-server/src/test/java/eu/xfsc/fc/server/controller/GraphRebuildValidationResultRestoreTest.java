package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.validation.GraphSyncStatus;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidationResultRepository;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.validation.ValidationResultHasher;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import eu.xfsc.fc.server.model.GraphRebuildRequest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test for validation result graph rebuild.
 *
 * <p>Verifies that {@code GraphRebuilder.rebuildValidationResults()} correctly
 * restores {@code fcmeta:} triples for validation results after a graph backend
 * reset or switch.</p>
 *
 * <p>Test flow:
 * <ol>
 *   <li>Create {@link ValidationResult} entities in PostgreSQL with FAILED graph sync status</li>
 *   <li>Trigger graph rebuild via REST endpoint</li>
 *   <li>Wait for the async rebuild to complete via {@link GraphRebuildService#isRunning()}</li>
 *   <li>Verify graph_sync_status is updated to SYNCED</li>
 * </ol>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "graphstore.impl=neo4j",
    "federated-catalogue.verification.protected-namespace.namespace=https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
class GraphRebuildValidationResultRestoreTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper jsonMapper;

  @Autowired
  private ValidationResultRepository validationResultRepository;

  @Autowired
  private ValidationResultHasher validationResultHasher;

  @Autowired
  private GraphRebuildService graphRebuildService;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @AfterEach
  void cleanup() {
    validationResultRepository.deleteAll();
  }

  /**
   * Tests that a validation result with FAILED graph sync status is restored after rebuild.
   *
   * <p>Creates one result with FAILED status, triggers rebuild, waits for async completion,
   * and verifies the status transitions to SYNCED.</p>
   */
  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void testValidationResultGraphSyncStatusUpdatedAfterRebuild() throws Exception {
    Long validationResultId = transactionTemplate.execute(txStatus -> {
      ValidationResult result = new ValidationResult();
      result.setAssetIds(new String[]{"did:example:asset1", "did:example:asset2"});
      result.setValidatorIds(new String[]{"https://example.org/schema/v1"});
      result.setValidatorType(ValidatorType.SCHEMA);
      result.setConforms(true);
      result.setValidatedAt(Instant.now());
      result.setReport(null);
      result.setContentHash(validationResultHasher.hash(result));
      result.setGraphSyncStatus(GraphSyncStatus.FAILED);

      return validationResultRepository.saveAndFlush(result).getId();
    });

    transactionTemplate.executeWithoutResult(txStatus -> {
      ValidationResult before = validationResultRepository.findById(validationResultId).orElseThrow();
      assertEquals(GraphSyncStatus.FAILED, before.getGraphSyncStatus(),
          "Initial graph_sync_status should be FAILED");
    });

    GraphRebuildRequest rebuildRequest = new GraphRebuildRequest(1, 0, 2, 100);
    mockMvc.perform(MockMvcRequestBuilders.post("/actuator/graph-rebuild")
            .content(jsonMapper.writeValueAsString(rebuildRequest))
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf()))
        .andExpect(status().isOk());

    await().atMost(10, SECONDS).until(() -> !graphRebuildService.isRunning());
    assertFalse(graphRebuildService.getStatus().isFailed(),
        "Rebuild should not have failed: " + graphRebuildService.getStatus().getErrorMessage());

    transactionTemplate.executeWithoutResult(txStatus -> {
      ValidationResult after = validationResultRepository.findById(validationResultId).orElseThrow();
      assertEquals(GraphSyncStatus.SYNCED, after.getGraphSyncStatus(),
          "graph_sync_status should be SYNCED after rebuild completes");
    });
  }

  /**
   * Tests that multiple validation results with mixed statuses are all restored in batch.
   *
   * <p>Creates 5 results with mixed FAILED/SYNCED statuses, triggers rebuild,
   * waits for async completion, and verifies all have SYNCED status.</p>
   */
  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void testMultipleValidationResultsRestoredInBatch() throws Exception {
    transactionTemplate.executeWithoutResult(txStatus -> {
      for (int i = 1; i <= 5; i++) {
        ValidationResult result = new ValidationResult();
        result.setAssetIds(new String[]{"did:example:asset" + i});
        result.setValidatorIds(new String[]{"https://example.org/schema/v" + i});
        result.setValidatorType(ValidatorType.SCHEMA);
        result.setConforms(i % 2 == 0);
        result.setValidatedAt(Instant.now());
        result.setReport(i % 2 == 0 ? null : "{\"violations\": []}");
        result.setContentHash(validationResultHasher.hash(result));
        result.setGraphSyncStatus(i % 3 == 0 ? GraphSyncStatus.FAILED : GraphSyncStatus.SYNCED);
        validationResultRepository.saveAndFlush(result);
      }
    });

    transactionTemplate.executeWithoutResult(txStatus -> {
      List<ValidationResult> beforeRebuild = validationResultRepository.findAll();
      assertEquals(5, beforeRebuild.size());
      long failedCount = beforeRebuild.stream()
          .filter(r -> r.getGraphSyncStatus() == GraphSyncStatus.FAILED)
          .count();
      assertTrue(failedCount > 0, "Should have at least one FAILED result before rebuild");
    });

    GraphRebuildRequest rebuildRequest = new GraphRebuildRequest(1, 0, 2, 100);
    mockMvc.perform(MockMvcRequestBuilders.post("/actuator/graph-rebuild")
            .content(jsonMapper.writeValueAsString(rebuildRequest))
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf()))
        .andExpect(status().isOk());

    await().atMost(10, SECONDS).until(() -> !graphRebuildService.isRunning());
    assertFalse(graphRebuildService.getStatus().isFailed(),
        "Rebuild should not have failed: " + graphRebuildService.getStatus().getErrorMessage());

    transactionTemplate.executeWithoutResult(txStatus -> {
      List<ValidationResult> afterRebuild = validationResultRepository.findAll();
      assertEquals(5, afterRebuild.size());
      assertTrue(afterRebuild.stream().allMatch(r -> r.getGraphSyncStatus() == GraphSyncStatus.SYNCED),
          "All validation results should have SYNCED status after rebuild");
    });
  }

  /**
   * Tests rebuild behavior with no validation results in the database.
   *
   * <p>Ensures rebuild completes successfully even when there are no validation
   * results to restore (edge case).</p>
   */
  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void testRebuildWithNoValidationResults() throws Exception {
    validationResultRepository.deleteAll();
    assertEquals(0, validationResultRepository.count());

    GraphRebuildRequest rebuildRequest = new GraphRebuildRequest(1, 0, 2, 100);
    mockMvc.perform(MockMvcRequestBuilders.post("/actuator/graph-rebuild")
            .content(jsonMapper.writeValueAsString(rebuildRequest))
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf()))
        .andExpect(status().isOk());

    await().atMost(10, SECONDS).until(() -> !graphRebuildService.isRunning());
    assertFalse(graphRebuildService.getStatus().isFailed(),
        "Rebuild should not have failed: " + graphRebuildService.getStatus().getErrorMessage());

    assertEquals(0, validationResultRepository.count());
  }
}
