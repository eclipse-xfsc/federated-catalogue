package eu.xfsc.fc.core.service.validation.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSchemaValidationStrategyTest {

  private static final String SIMPLE_SCHEMA =
      "{\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}";

  private static final String CONFORMING_JSON = "{\"id\":\"abc-123\"}";

  private static final String NON_CONFORMING_JSON = "{\"value\":\"missing-id-field\"}";

  // FileStore is not called in these tests — assets have contentAccessor pre-loaded.
  private final JsonSchemaValidationStrategy strategy =
      new JsonSchemaValidationStrategy(mock(FileStore.class), new ObjectMapper());

  @Test
  void validate_conformingJson_returnsConforming() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(SIMPLE_SCHEMA)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_nonConformingJson_returnsViolation() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset(NON_CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(SIMPLE_SCHEMA)));

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
    assertNotNull(report.getViolations().get(0).getMessage());
  }

  @Test
  void validate_malformedJson_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset("not json")),
        List.of(new ContentAccessorDirect(SIMPLE_SCHEMA))));
  }

  @Test
  void validate_fileRefInSchema_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"file:///etc/passwd\"}"))));
  }

  @Test
  void validate_httpRefInSchema_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"http://169.254.169.254/latest/meta-data\"}"))));
  }

  @Test
  void validate_gopherRefInSchema_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"gopher://internal/resource\"}"))));
  }

  @Test
  void validate_ftpRefInSchema_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"ftp://internal.example.org/schemas/v1\"}"))));
  }

  @Test
  void validate_conformingJson_withLocalSchemaRef_returnsConforming() {
    String schemaWithLocalRef =
        "{\"type\":\"object\",\"required\":[\"id\"],"
        + "\"properties\":{\"id\":{\"$ref\":\"#/definitions/IdType\"}},"
        + "\"definitions\":{\"IdType\":{\"type\":\"string\"}}}";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithLocalRef)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_multipleAssets_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON), buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(SIMPLE_SCHEMA))));
  }

  @Test
  void validate_multipleSchemas_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(SIMPLE_SCHEMA),
            new ContentAccessorDirect(SIMPLE_SCHEMA))));
  }


  private static AssetMetadata buildAsset(String content) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId("http://example.org/asset/1");
    asset.setContentAccessor(new ContentAccessorDirect(content));
    return asset;
  }
}
