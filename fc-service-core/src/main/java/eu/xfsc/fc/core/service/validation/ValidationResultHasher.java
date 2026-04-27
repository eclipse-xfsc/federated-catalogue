package eu.xfsc.fc.core.service.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.exception.ServerException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Service;

/**
 * Computes and verifies the tamper-proof SHA-256 hash stored on {@link ValidationResult}.
 *
 * <p>The hash covers: {@code assetIds}, {@code validatorIds}, {@code validatorType},
 * {@code conforms}, {@code validatedAt}. The {@code report} field is excluded — it is
 * large, optional, and may be truncated. Canonicalization uses JCS (RFC 8785) to ensure
 * a deterministic byte representation across JVM instances.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationResultHasher {

  private static final String ALGORITHM = "SHA-256";

  private final ObjectMapper objectMapper;

  /**
   * Computes the SHA-256 hex digest for the given validation result fields.
   *
   * @param result entity with all fields populated except {@code contentHash}
   * @return 64-character lowercase hex string
   * @throws ServerException if canonicalization or digest fails
   */
  public String hash(ValidationResult result) {
    String canonical = canonicalize(result);
    return digest(canonical);
  }

  /**
   * Returns true if the stored {@code contentHash} matches a freshly computed hash.
   * Used for tamper detection — does not throw, returns false on mismatch.
   */
  public boolean verify(ValidationResult result) {
    try {
      String expected = hash(result);
      return expected.equals(result.getContentHash());
    } catch (Exception e) {
      log.warn("verify; hash computation failed for id={}: {}", result.getId(), e.getMessage());
      return false;
    }
  }

  private String canonicalize(ValidationResult result) {
    // LinkedHashMap preserves insertion order; JsonCanonicalizer sorts keys anyway (JCS).
    // Sort array contents for canonical ordering (JCS sorts keys, not array elements).
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("assetIds", Arrays.stream(result.getAssetIds())
        .sorted().toList());
    fields.put("conforms", result.isConforms());
    fields.put("validatorIds", Arrays.stream(result.getValidatorIds())
        .sorted().toList());
    fields.put("validatorType", result.getValidatorType());
    fields.put("validatedAt", result.getValidatedAt().toString());
    try {
      String json = objectMapper.writeValueAsString(fields);
      return new JsonCanonicalizer(json).getEncodedString();
    } catch (JsonProcessingException e) {
      throw new ServerException("Failed to serialize validation result for hashing", e);
    } catch (IOException e) {
      throw new ServerException("Failed to canonicalize validation result for hashing", e);
    }
  }

  private static String digest(String canonical) {
    try {
      MessageDigest md = MessageDigest.getInstance(ALGORITHM);
      byte[] bytes = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new ServerException("SHA-256 not available", e);
    }
  }
}
