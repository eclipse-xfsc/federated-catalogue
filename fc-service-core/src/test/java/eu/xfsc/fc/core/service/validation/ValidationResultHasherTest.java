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

  private static final Instant VALIDATED_AT = Instant.parse("2024-06-01T12:00:00Z");

  private static final String ASSET_1 = "https://example.org/asset/1";
  private static final String VALIDATOR_REF_1 = "ref/1";

  private static final String SCHEMA_A = "schema/A";
  private static final String SCHEMA_B = "schema/B";
  private static final String SCHEMA_C = "schema/C";

  private static final String ASSET_ORDER_1 = "asset/1";
  private static final String ASSET_ORDER_2 = "asset/2";

  private static final String SERVICE_UNREACHABLE = "SERVICE_UNREACHABLE";
  private static final String SERVICE_TIMEOUT = "SERVICE_TIMEOUT";

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
        new String[]{ASSET_1},
        new String[]{"https://example.org/schema/1"},
        ValidatorType.SHACL,
        true,
        VALIDATED_AT);

    String hash = hasher.hash(result);

    assertEquals(64, hash.length(), "SHA-256 hex should be 64 characters");
    assertTrue(hash.matches("[0-9a-f]{64}"), "Hash must be lowercase hex");
  }

  @Test
  void hash_sameInput_returnsSameHash() {
    ValidationResult r1 = buildResult(new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true, VALIDATED_AT);
    ValidationResult r2 = buildResult(new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true, VALIDATED_AT);

    assertEquals(hasher.hash(r1), hasher.hash(r2));
  }

  @Test
  void hash_differentConforms_returnsDifferentHash() {
    ValidationResult passing = buildResult(new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true, VALIDATED_AT);
    ValidationResult failing = buildResult(new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, false, VALIDATED_AT);

    assertNotEquals(hasher.hash(passing), hasher.hash(failing));
  }

  @Test
  void hash_differentAssetIds_returnsDifferentHash() {
    ValidationResult r1 = buildResult(new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true, VALIDATED_AT);
    ValidationResult r2 = buildResult(new String[]{"https://example.org/asset/2"},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true, VALIDATED_AT);

    assertNotEquals(hasher.hash(r1), hasher.hash(r2));
  }

  // ===== verify =====

  @Test
  void verify_correctHash_returnsTrue() {
    ValidationResult result = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true,
        VALIDATED_AT);
    result.setContentHash(hasher.hash(result));

    assertTrue(hasher.verify(result));
  }

  @Test
  void verify_tamperedConforms_returnsFalse() {
    ValidationResult result = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true,
        VALIDATED_AT);
    result.setContentHash(hasher.hash(result));

    // Tamper after hash was set
    result.setConforms(false);

    assertFalse(hasher.verify(result));
  }

  @Test
  void verify_nullHash_returnsFalse() {
    ValidationResult result = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true,
        VALIDATED_AT);
    result.setContentHash(null);

    assertFalse(hasher.verify(result));
  }

  @Test
  void verify_hashComputationThrows_returnsFalse() {
    // null validatedAt causes NPE in canonicalize() — caught by verify(), which returns false
    ValidationResult result = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, true,
        null);
    result.setContentHash("anything");

    assertFalse(hasher.verify(result));
  }

  @Test
  void hash_differentReferenceOrderSameContent_returnsSameHash() {
    // Verify array element ordering is normalized (sorted) before hashing
    ValidationResult r1 = buildResult(
        new String[]{ASSET_1},
        new String[]{SCHEMA_A, SCHEMA_B, SCHEMA_C},
        ValidatorType.SHACL, true, VALIDATED_AT);
    ValidationResult r2 = buildResult(
        new String[]{ASSET_1},
        new String[]{SCHEMA_C, SCHEMA_A, SCHEMA_B},  // different order
        ValidatorType.SHACL, true, VALIDATED_AT);

    assertEquals(hasher.hash(r1), hasher.hash(r2),
        "Hash must be stable regardless of validatorIds array element order");
  }

  @Test
  void hash_differentAssetOrderSameContent_returnsSameHash() {
    // Same test for assetIds array
    ValidationResult r1 = buildResult(
        new String[]{ASSET_ORDER_1, ASSET_ORDER_2},
        new String[]{SCHEMA_A},
        ValidatorType.SHACL, true, VALIDATED_AT);
    ValidationResult r2 = buildResult(
        new String[]{ASSET_ORDER_2, ASSET_ORDER_1},  // different order
        new String[]{SCHEMA_A},
        ValidatorType.SHACL, true, VALIDATED_AT);

    assertEquals(hasher.hash(r1), hasher.hash(r2),
        "Hash must be stable regardless of assetIds array element order");
  }

  // ===== failureCategory =====

  @Test
  void hash_legacyShapedRow_matchesPinnedGoldenDigest() {
    // Pinned digest for a fixed legacy-shaped row (failureCategory column absent/null).
    // Guards the "legacy rows keep verifying" promise against silent changes to canonicalize()
    // (field additions, reordering, encoding) that a same-input/same-input comparison can't catch.
    ValidationResult result = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.SHACL, false,
        VALIDATED_AT);

    assertEquals("28f4c40fc2304bbd2ad8bb7d42782275bf681e11ba89aaa2eaf39c8e41a8b583", hasher.hash(result));
  }

  @Test
  void hash_setVsNullFailureCategory_returnsDifferentHash() {
    ValidationResult withCategory = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.TRUST_FRAMEWORK, false,
        VALIDATED_AT);
    withCategory.setFailureCategory(SERVICE_UNREACHABLE);
    ValidationResult withoutCategory = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.TRUST_FRAMEWORK, false,
        VALIDATED_AT);

    assertNotEquals(hasher.hash(withCategory), hasher.hash(withoutCategory));
  }

  @Test
  void hash_differentFailureCategory_returnsDifferentHash() {
    ValidationResult unreachable = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.TRUST_FRAMEWORK, false,
        VALIDATED_AT);
    unreachable.setFailureCategory(SERVICE_UNREACHABLE);
    ValidationResult timeout = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.TRUST_FRAMEWORK, false,
        VALIDATED_AT);
    timeout.setFailureCategory(SERVICE_TIMEOUT);

    assertNotEquals(hasher.hash(unreachable), hasher.hash(timeout));
  }

  @Test
  void verify_tamperedFailureCategory_returnsFalse() {
    ValidationResult result = buildResult(
        new String[]{ASSET_1},
        new String[]{VALIDATOR_REF_1}, ValidatorType.TRUST_FRAMEWORK, false,
        VALIDATED_AT);
    result.setFailureCategory(SERVICE_UNREACHABLE);
    result.setContentHash(hasher.hash(result));

    // Tamper after hash was set — swapping the discriminator must be caught, not just conforms.
    result.setFailureCategory(SERVICE_TIMEOUT);

    assertFalse(hasher.verify(result));
  }
}
