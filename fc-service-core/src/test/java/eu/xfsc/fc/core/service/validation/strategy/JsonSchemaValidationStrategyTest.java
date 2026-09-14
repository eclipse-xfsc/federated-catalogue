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
  void validate_absoluteRefMatchingDeclaredIdInDocument_returnsConforming() {
    // Standard bundling: an absolute-scheme $ref pointing at a $id declared elsewhere in the same
    // document resolves entirely against the document's own already-loaded schema resources and
    // never reaches the blocked loader — the pre-check must not reject it (regression: PR #154
    // review comment https://github.com/eclipse-xfsc/federated-catalogue/pull/154#issuecomment-5571684071).
    String bundledSchemaWithAbsoluteRef = """
        {"$id":"https://ex.org/main",\
        "$defs":{"x":{"$id":"https://ex.org/x"}},\
        "properties":{"id":{"$ref":"https://ex.org/x"}}}""";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(bundledSchemaWithAbsoluteRef)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_absoluteRefMatchingDeclaredIdWithFragment_returnsConforming() {
    // Same shape as above, but the $ref also carries a fragment into the declared sub-schema
    // ("https://ex.org/x#/definitions/y") — the fragment must be stripped before matching against
    // the declared-$id set, not treated as part of the base URI.
    String bundledSchemaWithFragmentRef = """
        {"$id":"https://ex.org/main",\
        "$defs":{"x":{"$id":"https://ex.org/x","definitions":{"y":{"type":"string"}}}},\
        "properties":{"id":{"$ref":"https://ex.org/x#/definitions/y"}}}""";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(bundledSchemaWithFragmentRef)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_absoluteRefNotMatchingAnyDeclaredId_throwsClientException() {
    // Same bundled-document shape, but the $ref points at an absolute URI that is not declared by
    // any $id in the document — this must still be rejected with the existing precise message,
    // proving the fix does not over-relax the check into accepting any absolute $ref.
    String schemaWithUndeclaredAbsoluteRef = """
        {"$id":"https://ex.org/main",\
        "$defs":{"x":{"$id":"https://ex.org/x"}},\
        "properties":{"id":{"$ref":"https://ex.org/not-declared"}}}""";

    ClientException exception = assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithUndeclaredAbsoluteRef))));

    assertTrue(exception.getMessage().contains("https://ex.org/not-declared")
            && exception.getMessage().contains("resolves outside the schema document"),
        "Expected the pre-check's own error, got: " + exception.getMessage());
  }

  @Test
  void validate_refUnderDefaultKeyword_isIgnoredAsData_returnsConforming() {
    // "default" holds a literal example value, never a nested schema — a "$ref"-shaped value there
    // is data, not a real reference, and must not be walked into at all (previously this threw,
    // since the old blind walk checked every object's "$ref" field regardless of position).
    String schemaWithRefShapedDefault = """
        {"type":"object",\
        "properties":{"id":{"type":"string","default":{"$ref":"https://not-a-real-ref.example/x"}}}}""";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithRefShapedDefault)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_idUnderExamplesKeyword_notTreatedAsDeclaredAnchor_throwsClientException() {
    // "examples" holds illustrative data, never a nested schema — a "$id" placed there is a decoy,
    // not a real same-document anchor, and must not be added to the declared-$id set. Otherwise an
    // absolute $ref matching it would incorrectly bypass the pre-check.
    String schemaWithDecoyIdInExamples = """
        {"$id":"https://ex.org/main",\
        "examples":[{"$id":"https://ex.org/decoy"}],\
        "properties":{"id":{"$ref":"https://ex.org/decoy"}}}""";

    ClientException exception = assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithDecoyIdInExamples))));

    assertTrue(exception.getMessage().contains("https://ex.org/decoy")
            && exception.getMessage().contains("resolves outside the schema document"),
        "Expected the pre-check's own error, got: " + exception.getMessage());
  }

  @Test
  void validate_externalRefInDefsEntryNamedConst_throwsClientException() {
    // "const" here is not the NON_SCHEMA_DATA_KEYWORDS "const" keyword — it is the NAME of a
    // $defs map entry (map keys in $defs/properties/patternProperties/dependentSchemas are
    // user-chosen identifiers, not JSON-Schema keywords). That entry's value is a genuine nested
    // schema carrying a real external $ref. Because collectIdsAndRefs matches on the key string
    // alone, it currently skips recursing into this entry entirely, so the $ref is never checked —
    // an SSRF pre-check bypass. This must throw exactly like the top-level $ref tests above.
    String schemaWithExternalRefInDefsEntryNamedConst = """
        {"$defs":{"const":{"$ref":"https://evil.example/x"}}}""";

    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithExternalRefInDefsEntryNamedConst))));
  }

  @Test
  void validate_absoluteRefMatchingIdDeclaredInDefsEntryNamedConst_returnsConforming() {
    // Symmetric case: the $defs entry NAMED "const" declares a genuine $id, referenced elsewhere
    // in the document by a matching absolute $ref — legitimate same-document bundling, same shape
    // as validate_absoluteRefMatchingDeclaredIdInDocument_returnsConforming above, except the
    // $defs entry's name happens to collide with the NON_SCHEMA_DATA_KEYWORDS string "const". The
    // key-string-only check skips recursing into this entry, so its $id is never collected into
    // declaredIds, and the matching $ref is wrongly rejected as external.
    String schemaWithIdInDefsEntryNamedConst = """
        {"$id":"https://ex.org/main",\
        "$defs":{"const":{"$id":"https://ex.org/x"}},\
        "properties":{"id":{"$ref":"https://ex.org/x"}}}""";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithIdInDefsEntryNamedConst)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_externalRefInPropertiesEntryNamedDefault_throwsClientException() {
    // Same ambiguity as validate_externalRefInDefsEntryNamedConst_throwsClientException, but for a
    // different name-keyed-map keyword ("properties" instead of "$defs") colliding with a different
    // NON_SCHEMA_DATA_KEYWORDS string ("default" instead of "const"). The JSON property that this
    // schema entry describes is itself named "default" — a legitimate, if unusual, property name —
    // and its schema value carries a real external $ref that must still be rejected.
    String schemaWithExternalRefInPropertiesEntryNamedDefault = """
        {"properties":{"default":{"$ref":"https://evil.example/x"}}}""";

    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithExternalRefInPropertiesEntryNamedDefault))));
  }

  @Test
  void validate_idShapedEntryInPropertiesMap_notTreatedAsDeclaredAnchor_throwsClientException() {
    // "properties" is a name-keyed map: the map node itself is not a schema, so its own top-level
    // field named "$id" must never be harvested as a declared anchor — only a "$id" belonging to
    // one of the map's ENTRY VALUES (a real nested schema) counts. If the map node's own fields were
    // harvested like a schema's, an attacker could plant a decoy anchor directly on the map node
    // ("properties":{"$id":"<attacker-chosen absolute URI>"}) that a matching absolute $ref
    // elsewhere in the document would then hide behind, bypassing the pre-check entirely.
    String schemaWithDecoyIdAsPropertiesMapNodeField = """
        {"properties":{"$id":"https://ex.org/decoy-via-properties-map-node"},\
        "$ref":"https://ex.org/decoy-via-properties-map-node"}""";

    ClientException exception = assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithDecoyIdAsPropertiesMapNodeField))));

    assertTrue(exception.getMessage().contains("https://ex.org/decoy-via-properties-map-node")
            && exception.getMessage().contains("resolves outside the schema document"),
        "Expected the pre-check's own error, got: " + exception.getMessage());
  }

  @Test
  void validate_absoluteRefMatchingIdDeclaredInsideAllOf_returnsConforming() {
    // The document walk must also traverse array-valued applicator keywords (allOf/anyOf/oneOf),
    // not just plain object properties: a legitimate $id declared on a schema bundled inside an
    // "allOf" array element must still be collected, so a matching absolute $ref elsewhere in the
    // document is recognised as same-document bundling rather than rejected as external.
    String schemaWithIdInsideAllOf = """
        {"$id":"https://ex.org/main",\
        "allOf":[{"$id":"https://ex.org/x"}],\
        "properties":{"id":{"$ref":"https://ex.org/x"}}}""";

    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_JSON)),
        List.of(new ContentAccessorDirect(schemaWithIdInsideAllOf)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
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
