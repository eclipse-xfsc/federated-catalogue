package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationResultHasherTest {

  private ValidationResultHasher hasher;

  @BeforeEach
  void setUp() {
    hasher = new ValidationResultHasher(new ObjectMapper());
  }


  private static ValidationResult buildResult(String[] assetIds, String[] validatorIds,
      ValidatorType validatorType, boolean conforms, Instant validatedAt) {
    ValidationResult r = new ValidationResult();
    r.setAssetIds(assetIds);
    r.setValidatorIds(validatorIds);
    r.setValidatorType(validatorType);
    r.setConforms(conforms);
    r.setValidatedAt(validatedAt);
    return r;
  }

  // ===== hash =====

  @Test
  void hash_basicInput_returns64CharHexString() {
    ValidationResult result = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"https://example.org/schema/1"},
        ValidatorType.SHACL,
        true,
        Instant.parse("2024-06-01T12:00:00Z"));

    String hash = hasher.hash(result);

    assertEquals(64, hash.length(), "SHA-256 hex should be 64 characters");
    assertTrue(hash.matches("[0-9a-f]{64}"), "Hash must be lowercase hex");
  }

  @Test
  void hash_sameInput_returnsSameHash() {
    Instant ts = Instant.parse("2024-06-01T12:00:00Z");
    ValidationResult r1 = buildResult(new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true, ts);
    ValidationResult r2 = buildResult(new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true, ts);

    assertEquals(hasher.hash(r1), hasher.hash(r2));
  }

  @Test
  void hash_differentConforms_returnsDifferentHash() {
    Instant ts = Instant.parse("2024-06-01T12:00:00Z");
    ValidationResult passing = buildResult(new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true, ts);
    ValidationResult failing = buildResult(new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, false, ts);

    assertNotEquals(hasher.hash(passing), hasher.hash(failing));
  }

  @Test
  void hash_differentAssetIds_returnsDifferentHash() {
    Instant ts = Instant.parse("2024-06-01T12:00:00Z");
    ValidationResult r1 = buildResult(new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true, ts);
    ValidationResult r2 = buildResult(new String[]{"https://example.org/asset/2"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true, ts);

    assertNotEquals(hasher.hash(r1), hasher.hash(r2));
  }

  // ===== verify =====

  @Test
  void verify_correctHash_returnsTrue() {
    ValidationResult result = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true,
        Instant.parse("2024-06-01T12:00:00Z"));
    result.setContentHash(hasher.hash(result));

    assertTrue(hasher.verify(result));
  }

  @Test
  void verify_tamperedConforms_returnsFalse() {
    ValidationResult result = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true,
        Instant.parse("2024-06-01T12:00:00Z"));
    result.setContentHash(hasher.hash(result));

    // Tamper after hash was set
    result.setConforms(false);

    assertFalse(hasher.verify(result));
  }

  @Test
  void verify_nullHash_returnsFalse() {
    ValidationResult result = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true,
        Instant.parse("2024-06-01T12:00:00Z"));
    result.setContentHash(null);

    assertFalse(hasher.verify(result));
  }

  @Test
  void verify_hashComputationThrows_returnsFalse() {
    // null validatedAt causes NPE in canonicalize() — caught by verify(), which returns false
    ValidationResult result = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, ValidatorType.SHACL, true,
        null);
    result.setContentHash("anything");

    assertFalse(hasher.verify(result));
  }

  @Test
  void hash_differentReferenceOrderSameContent_returnsSameHash() {
    // Verify array element ordering is normalized (sorted) before hashing
    Instant ts = Instant.parse("2024-06-01T12:00:00Z");
    ValidationResult r1 = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"schema/A", "schema/B", "schema/C"},
        ValidatorType.SHACL, true, ts);
    ValidationResult r2 = buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"schema/C", "schema/A", "schema/B"},  // different order
        ValidatorType.SHACL, true, ts);

    assertEquals(hasher.hash(r1), hasher.hash(r2),
        "Hash must be stable regardless of validatorIds array element order");
  }

  @Test
  void hash_differentAssetOrderSameContent_returnsSameHash() {
    // Same test for assetIds array
    Instant ts = Instant.parse("2024-06-01T12:00:00Z");
    ValidationResult r1 = buildResult(
        new String[]{"asset/1", "asset/2"},
        new String[]{"schema/A"},
        ValidatorType.SHACL, true, ts);
    ValidationResult r2 = buildResult(
        new String[]{"asset/2", "asset/1"},  // different order
        new String[]{"schema/A"},
        ValidatorType.SHACL, true, ts);

    assertEquals(hasher.hash(r1), hasher.hash(r2),
        "Hash must be stable regardless of assetIds array element order");
  }
}
