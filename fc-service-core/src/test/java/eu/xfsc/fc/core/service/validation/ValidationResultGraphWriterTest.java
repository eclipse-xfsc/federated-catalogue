package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ValidationResultGraphWriterTest {

  private static final String FCMETA =
      "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#";

  private ValidationResultGraphWriter writer;
  private GraphStore graphStore;

  @BeforeEach
  void setUp() {
    writer = new ValidationResultGraphWriter(new ProtectedNamespaceProperties());
    graphStore = mock(GraphStore.class);
  }

  // --- helper ---

  private static ValidationResult buildResult(long id, String[] assetIds) {
    ValidationResult r = new ValidationResult();
    r.setAssetIds(assetIds);
    r.setValidatorIds(new String[]{"https://example.org/schema/1"});
    r.setValidatorType(ValidatorType.SCHEMA);
    r.setConforms(true);
    r.setValidatedAt(Instant.parse("2024-06-01T12:00:00Z"));
    r.setId(id);
    return r;
  }

  // ===== write — single asset =====

  @Test
  @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class) produces an unchecked cast
  void write_singleAsset_producesFiveTriples() {
    ValidationResult result = buildResult(42L, new String[]{"https://example.org/asset/1"});

    writer.write(result, graphStore);

    ArgumentCaptor<List<RdfClaim>> claimsCaptor = forClass(List.class);
    ArgumentCaptor<String> subjectCaptor = forClass(String.class);
    verify(graphStore).addClaims(claimsCaptor.capture(), subjectCaptor.capture());

    List<RdfClaim> claims = claimsCaptor.getValue();
    assertEquals(5, claims.size(), "Single asset + 1 validatorId: 1 hasValidationResult + 1 validatorId + 3 property triples = 5");
  }

  @Test
  @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class) produces an unchecked cast
  void write_singleAsset_linksAssetToResultIri() {
    ValidationResult result = buildResult(42L, new String[]{"https://example.org/asset/1"});

    writer.write(result, graphStore);

    ArgumentCaptor<List<RdfClaim>> claimsCaptor = forClass(List.class);
    verify(graphStore).addClaims(claimsCaptor.capture(), org.mockito.ArgumentMatchers.any());

    List<RdfClaim> claims = claimsCaptor.getValue();
    String expectedSubject = "<https://example.org/asset/1>";
    String expectedPredicate = "<" + FCMETA + "hasValidationResult>";
    boolean linkFound = claims.stream()
        .anyMatch(c -> expectedSubject.equals(c.getSubjectString())
            && expectedPredicate.equals(c.getPredicateString()));
    assertTrue(linkFound, "Expected link triple from asset to result IRI");
  }

  // ===== write — multiple assets =====

  @Test
  @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class) produces an unchecked cast
  void write_twoAssets_producesSixTriples() {
    ValidationResult result = buildResult(7L, new String[]{
        "https://example.org/asset/1",
        "https://example.org/asset/2"
    });

    writer.write(result, graphStore);

    ArgumentCaptor<List<RdfClaim>> claimsCaptor = forClass(List.class);
    verify(graphStore).addClaims(claimsCaptor.capture(), org.mockito.ArgumentMatchers.any());

    List<RdfClaim> claims = claimsCaptor.getValue();
    assertEquals(6, claims.size(), "Two assets + 1 validatorId: 2 hasValidationResult + 1 validatorId + 3 property triples = 6");
  }

  @Test
  @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class) produces an unchecked cast
  void write_multipleAssets_usesFirstAssetAsCredentialSubject() {
    ValidationResult result = buildResult(7L, new String[]{
        "https://example.org/asset/FIRST",
        "https://example.org/asset/SECOND"
    });

    writer.write(result, graphStore);

    ArgumentCaptor<String> subjectCaptor = forClass(String.class);
    verify(graphStore).addClaims(org.mockito.ArgumentMatchers.any(), subjectCaptor.capture());

    assertEquals("https://example.org/asset/FIRST", subjectCaptor.getValue());
  }

  // ===== write — validatorId triples =====

  @Test
  @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class) produces an unchecked cast
  void write_singleSchema_producesValidatorIdIriTriple() {
    ValidationResult result = buildResult(42L, new String[]{"https://example.org/asset/1"});

    writer.write(result, graphStore);

    ArgumentCaptor<List<RdfClaim>> claimsCaptor = forClass(List.class);
    verify(graphStore).addClaims(claimsCaptor.capture(), org.mockito.ArgumentMatchers.any());

    String expectedPredicate = "<" + FCMETA + "validatorId>";
    String expectedObject = "<https://example.org/schema/1>";
    boolean found = claimsCaptor.getValue().stream()
        .anyMatch(c -> expectedPredicate.equals(c.getPredicateString())
            && expectedObject.equals(c.getObjectString()));
    assertTrue(found, "Expected validatorId IRI triple for schema/1");
  }

  @Test
  @SuppressWarnings("unchecked") // ArgumentCaptor.forClass(List.class) produces an unchecked cast
  void write_multipleSchemas_producesOneValidatorIdTriplePerSchema() {
    ValidationResult result = buildResult(42L, new String[]{"https://example.org/asset/1"});
    result.setValidatorIds(new String[]{
        "https://example.org/schema/1",
        "https://example.org/schema/2"
    });

    writer.write(result, graphStore);

    ArgumentCaptor<List<RdfClaim>> claimsCaptor = forClass(List.class);
    verify(graphStore).addClaims(claimsCaptor.capture(), org.mockito.ArgumentMatchers.any());

    // 1 hasValidationResult + 2 validatorId + 3 property triples = 6
    assertEquals(6, claimsCaptor.getValue().size());
    String expectedPredicate = "<" + FCMETA + "validatorId>";
    long validatorIdCount = claimsCaptor.getValue().stream()
        .filter(c -> expectedPredicate.equals(c.getPredicateString()))
        .count();
    assertEquals(2, validatorIdCount, "Expected one validatorId triple per schema");
  }

  // ===== write — error handling =====

  @Test
  void write_graphStoreThrowsException_propagatesException() {
    ValidationResult result = buildResult(1L, new String[]{"https://example.org/asset/1"});
    GraphStore failingStore = mock(GraphStore.class);
    org.mockito.Mockito.doThrow(new RuntimeException("Graph DB connection failed"))
        .when(failingStore).addClaims(any(), any());

    try {
      writer.write(result, failingStore);
      org.junit.jupiter.api.Assertions.fail("Expected RuntimeException to be thrown");
    } catch (RuntimeException e) {
      assertEquals("Graph DB connection failed", e.getMessage());
      verify(failingStore).addClaims(any(), any());
    }
  }
}
