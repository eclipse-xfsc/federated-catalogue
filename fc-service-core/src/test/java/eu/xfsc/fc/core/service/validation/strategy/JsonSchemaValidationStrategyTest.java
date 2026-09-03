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

  // A JSON-LD-serialised RDF asset (has @context) — the JSON Schema part of a combined
  // SHACL + JSON Schema validation request runs against this representation as-is.
  private static final String JSON_LD_CREDENTIAL_CONTENT = """
      {"@context":"https://www.w3.org/ns/credentials/v2",\
      "type":["VerifiableCredential"],"issuer":"did:web:example.org"}""";

  private static final String JSON_SCHEMA_REQUIRING_ISSUER =
      "{\"type\":\"object\",\"required\":[\"issuer\"],\"properties\":{\"issuer\":{\"type\":\"string\"}}}";

  private static final String JSON_SCHEMA_REQUIRING_CREDENTIAL_SUBJECT =
      "{\"type\":\"object\",\"required\":[\"credentialSubject\"]}";

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
  void validate_httpsRefInSchema_throwsClientException() {
    // https:// must be rejected too — it is not a special case exempt from the SSRF check.
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"https://internal.example.org/schemas/v1\"}"))));
  }

  @Test
  void validate_upperCaseHttpRefInSchema_throwsClientException() {
    // The scheme check must be case-insensitive — "HTTP://" is the same scheme as "http://".
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"HTTP://169.254.169.254/latest/meta-data\"}"))));
  }

  @Test
  void validate_protocolRelativeRefInSchema_throwsClientException() {
    // "//evil.example/schema" carries no URI scheme, so it would slip past a scheme-only check,
    // yet it still resolves to an external fetch (RFC 3986 §4.2) once given a scheme by whatever
    // resolves it. The pre-check must reject it directly for a precise client error rather than
    // falling through to the registry's load-time block (proven below by pinning the pre-check's
    // own wording and ruling out the registry's "Schema could not be loaded:" wording — see
    // validate_relativeRefResolvedAgainstExternalIdBase_failsWithoutNetworkAccess for the case
    // that *is* expected to be caught only by the registry).
    ClientException exception = assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$ref\":\"//evil.example/schema\"}"))));

    assertTrue(exception.getMessage().contains("//evil.example/schema")
            && exception.getMessage().contains("resolves outside the schema document"),
        "Expected the pre-check's own error, got: " + exception.getMessage());
    assertFalse(exception.getMessage().startsWith("Schema could not be loaded:"),
        "Expected the pre-check to reject this before the schema is ever loaded by the registry, "
            + "got: " + exception.getMessage());
  }

  @Test
  void validate_absoluteDynamicRefInSchema_throwsClientException() {
    // $dynamicRef is a separate keyword from $ref (2020-12) and must be covered by the same
    // out-of-document check, not just $ref.
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect("{\"$dynamicRef\":\"https://internal.example.org/schemas/v1\"}"))));
  }

  @Test
  void validate_conformingJson_withWellKnownMetaSchemaDeclared_returnsConforming() {
    // A well-known "$schema" IRI resolves to a built-in dialect preset and must not be treated as
    // an external resource by the registry's block-all schema loader.
    String schemaWithMetaSchema =
        "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
        + "\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithMetaSchema)));

    assertTrue(report.getConforms());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_conformingJson_withDefsFragmentRef_returnsConforming() {
    // A fragment-only $ref into the schema's own $defs must still work — the pre-check and the
    // registry-level block must both permit same-document references.
    String schemaWithDefsRef =
        "{\"type\":\"object\",\"required\":[\"id\"],"
        + "\"properties\":{\"id\":{\"$ref\":\"#/$defs/x\"}},"
        + "\"$defs\":{\"x\":{\"type\":\"string\"}}}";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithDefsRef)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_relativeRefResolvedAgainstExternalIdBase_failsWithoutNetworkAccess() {
    // "other.json" alone carries no URI scheme, so the textual pre-check lets it through — it
    // only becomes the external IRI "http://192.0.2.1/base/other.json" once resolved against the
    // schema's own $id. 192.0.2.1 is the TEST-NET-1 documentation range (RFC 5737): it is never
    // routed, so if the registry attempted to actually connect, this call would hang rather than
    // fail fast. Asserting a tight time bound proves no connection was attempted.
    String schemaWithIdBaseBypass =
        "{\"$id\":\"http://192.0.2.1/base/\",\"type\":\"object\","
        + "\"properties\":{\"id\":{\"$ref\":\"other.json\"}}}";

    long startNanos = System.nanoTime();
    ClientException exception = assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithIdBaseBypass))));
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

    assertTrue(elapsedMillis < 5_000,
        "Validation must fail immediately with no network attempt; took " + elapsedMillis + "ms");
    // Pin that this was rejected by the registry-level block (buildRegistry()'s schema loader),
    // not by the textual pre-check — proving the load-time guard, not the pre-check, is what
    // actually stops the bypass. If a future change made the pre-check itself catch this case,
    // this assertion (and the "not allowed to be loaded" wording) would need to change with it.
    assertTrue(exception.getMessage().startsWith("Schema could not be loaded:"),
        "Expected the registry's load-time block to reject this, got: " + exception.getMessage());
    assertTrue(exception.getMessage().contains("http://192.0.2.1/base/other.json")
            && exception.getMessage().contains("not allowed to be loaded"),
        "Expected the blocked, resolved IRI to be named in the failure, got: " + exception.getMessage());
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
  void validate_jsonLdRepresentation_conformingSchema_returnsConforming() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset(JSON_LD_CREDENTIAL_CONTENT)),
        List.of(new ContentAccessorDirect(JSON_SCHEMA_REQUIRING_ISSUER)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_jsonLdRepresentation_nonConformingSchema_returnsViolation() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset(JSON_LD_CREDENTIAL_CONTENT)),
        List.of(new ContentAccessorDirect(JSON_SCHEMA_REQUIRING_CREDENTIAL_SUBJECT)));

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
    assertTrue(report.getViolations().get(0).getMessage().contains("credentialSubject"),
        "Violation should name the missing required property: " + report.getViolations().get(0).getMessage());
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
