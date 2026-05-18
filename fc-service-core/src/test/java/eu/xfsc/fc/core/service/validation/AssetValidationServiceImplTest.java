package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.validation.strategy.ValidationStrategy;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;
import eu.xfsc.fc.core.service.validation.strategy.JsonSchemaValidationStrategy;
import eu.xfsc.fc.core.service.validation.strategy.ShaclValidationStrategy;
import eu.xfsc.fc.core.service.validation.strategy.XmlSchemaValidationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class AssetValidationServiceImplTest {

  @Mock
  private AssetStore assetStore;
  @Mock
  private SchemaStore schemaStore;
  @Mock
  private SchemaModuleConfigService moduleConfigService;
  @Mock
  private ShaclValidationStrategy shaclValidationStrategy;
  @Mock
  private JsonSchemaValidationStrategy jsonSchemaValidationStrategy;
  @Mock
  private XmlSchemaValidationStrategy xmlSchemaValidationStrategy;
  @Mock
  private ValidationResultStore validationResultStore;
  @Mock
  private List<ValidationStrategy> strategies;

  @InjectMocks
  private AssetValidationServiceImpl service;


  @BeforeEach
  void setUpStrategies() {
    ReflectionTestUtils.setField(service, "maxAssetsPerRequest", 20);
  }

  @SuppressWarnings("unchecked") // Mockito can only mock raw Class literals; cast is safe for test providers.
  private static <T> ObjectProvider<T> createProviderMock() {
    return (ObjectProvider<T>) mock(ObjectProvider.class);
  }


  private static final String ASSET_ID = "https://example.org/asset/1";
  private static final String ASSET_ID_2 = "https://example.org/asset/2";
  private static final String SCHEMA_ID = "https://example.org/schema/shape/1";
  private static final String JSON_SCHEMA_ID = "https://example.org/schema/json/1";
  private static final String XML_SCHEMA_ID = "https://example.org/schema/xml/1";
  private static final String ISSUER_DID = "did:web:example.org";
  private static final String RDF_HASH = "hash-rdf";
  private static final String RDFXML_HASH = "hash-rdfxml";
  private static final String TURTLE_HASH = "hash-turtle";
  private static final String NONRDF_HASH = "hash-nonrdf";

  private static final ValidationReport CONFORMING_REPORT = new ValidationReport()
      .conforms(true).violations(List.of());

  private static final ValidationReport NON_CONFORMING_REPORT = new ValidationReport()
      .conforms(false)
      .violations(List.of(new ValidationViolation()
          .message("constraint violation")
          .severity(ValidationViolation.SeverityEnum.VIOLATION)))
      .rawReport("constraint violation");

  // === validateAsset — RDF (SHACL) path ===

  @Test
  void validateAsset_rdfAsset_explicitShapeSchema_returnsConformingResponse() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(1L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(List.of(1L), response.getValidationResultIds());
    assertNull(response.getReport());
  }

  @Test
  void validateAsset_rdfAsset_nonConforming_reportIncludedInResponse() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape content");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(NON_CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(2L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertFalse(response.getConforms());
    assertNotNull(response.getReport());
    assertEquals(1, response.getReport().getViolations().size());
    assertEquals("constraint violation", response.getReport().getViolations().get(0).getMessage());
  }

  @Test
  void validateAsset_rdfAsset_validateAll_usesCompositeSchema() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(3L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_rdfAsset_shaclModuleDisabled_throwsVerificationException() {
    registerStrategyList();
    registerShaclStrategy();
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_jsonLdRdfAsset_withExplicitJsonSchema_jsonModuleDisabled_throwsClientException() {
    registerStrategyList();
    registerJsonStrategy();
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.JSON_SCHEMA)).thenReturn(false);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    // RDF assets (JSON-LD) are not applicable to JSON Schema validation - throws ClientException before module check
    ClientException ex = assertThrows(ClientException.class, () -> service.validateAssets(request));
    assertTrue(ex.getMessage().contains("not applicable"), "Exception should explain why: " + ex.getMessage());
  }

  @Test
  void validateAsset_rdfXmlAsset_withExplicitXmlSchema_xmlModuleDisabled_throwsClientException() {
    registerStrategyList();
    registerXmlStrategy();
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfXmlAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.XML_SCHEMA)).thenReturn(false);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    // RDF assets (RDF/XML) are not applicable to XML Schema validation - throws ClientException before module check
    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_rdfAsset_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_rdfAsset_schemaTypeNotShape_throwsClientException() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    // Schema exists but is ONTOLOGY type — not supported for on-demand validation
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(
        new SchemaRecord(SCHEMA_ID, SCHEMA_ID, SchemaType.ONTOLOGY, "ontology content", null));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_rdfAsset_validateAll_shaclEnabledNoShapesAndNoOtherModule_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(null);
    // JSON_SCHEMA not enabled (default false) — nothing runs

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  // === validateAsset — RDF validateAll C-2: skip disabled modules ===

  @Test
  void validateAsset_rdfAsset_validateAll_allModulesDisabled_throwsVerificationException() {
    registerStrategyList();
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    // no module mocked → all return false

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  // === Positive tests for RDF serializations + SHACL ===

  @Test
  void validateAsset_rdfXmlAsset_withShapeSchema_returnsConformingResponse() {
    AssetMetadata asset = buildRdfXmlAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(50L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(List.of(50L), response.getValidationResultIds());
    assertNull(response.getReport());
  }

  @Test
  void validateAsset_rdfXmlAsset_validateAll_runsShaclOnly() {
    AssetMetadata asset = buildRdfXmlAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(51L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(List.of(51L), response.getValidationResultIds());
  }

  @Test
  void validateAsset_jsonLdRdfAsset_withShapeSchema_returnsConformingResponse() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(52L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(List.of(52L), response.getValidationResultIds());
    assertNull(response.getReport());
  }

  @Test
  void validateAsset_jsonLdRdfAsset_validateAll_runsShaclOnly() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(53L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(List.of(53L), response.getValidationResultIds());
  }

  // === Negative tests for RDF serializations + non-SHACL schemas ===

  @Test
  void validateAsset_rdfXmlAsset_withJsonSchema_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfXmlAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_jsonLdRdfAsset_withXmlSchema_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_jsonLdRdfAsset_multipleExplicitJsonSchemas_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchemaRecord("https://example.org/schema/json/2"))
        .thenReturn(buildJsonRecord("https://example.org/schema/json/2"));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID, "https://example.org/schema/json/2"));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_rdfXmlAsset_multipleExplicitXmlSchemas_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfXmlAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchemaRecord("https://example.org/schema/xml/2"))
        .thenReturn(buildXmlRecord("https://example.org/schema/xml/2"));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID, "https://example.org/schema/xml/2"));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_turtleRdfAsset_validateAll_runsShaclOnly() {
    AssetMetadata asset = buildTurtleRdfAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("shape composite");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(34L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(34L), response.getValidationResultIds());
    verify(validationResultStore, times(1)).store(any());
  }

  // === validateAsset — non-RDF JSON path ===

  @Test
  void validateAsset_jsonAsset_explicitJsonSchema_returnsConformingResponse() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/json");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(schemaContent);
    when(jsonSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(4L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_jsonAsset_jsonModuleDisabled_throwsVerificationException() {
    registerStrategyList();
    registerJsonStrategy();
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/json"));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.JSON_SCHEMA)).thenReturn(false);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_jsonAsset_schemaTypeNotJson_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();
    // Caller provided a SHAPE schema for a JSON asset — must be rejected
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_jsonAsset_multipleExplicitSchemas_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchemaRecord("https://example.org/schema/json/2"))
        .thenReturn(buildJsonRecord("https://example.org/schema/json/2"));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID, "https://example.org/schema/json/2"));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_jsonAsset_validateAll_usesLatestSchema() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/json");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(schemaContent);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.JSON, List.of(JSON_SCHEMA_ID)));
    when(jsonSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(5L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_jsonAsset_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();

    assertThrows(NotFoundException.class,
        () -> service.validateAssets(new ValidationRequest().assetIds(List.of(ASSET_ID))));
  }

  @Test
  void validateAsset_jsonAsset_validateAll_noSchemaFound_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(null);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  // === validateAsset — non-RDF XML path ===

  @Test
  void validateAsset_xmlAsset_explicitXmlSchema_returnsConformingResponse() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/xml");
    ContentAccessor schemaContent = new ContentAccessorDirect("<xs:schema/>");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchema(XML_SCHEMA_ID)).thenReturn(schemaContent);
    when(xmlSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(6L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(XML_SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_xmlAsset_xmlModuleDisabled_throwsVerificationException() {
    registerStrategyList();
    registerXmlStrategy();
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/xml"));
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.XML_SCHEMA)).thenReturn(false);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_xmlAsset_schemaTypeNotXml_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_xmlAsset_multipleExplicitSchemas_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchemaRecord("https://example.org/schema/xml/2"))
        .thenReturn(buildXmlRecord("https://example.org/schema/xml/2"));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID, "https://example.org/schema/xml/2"));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_xmlAsset_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();

    assertThrows(NotFoundException.class,
        () -> service.validateAssets(new ValidationRequest().assetIds(List.of(ASSET_ID))));
  }

  @Test
  void validateAsset_xmlAsset_validateAll_noSchemaFound_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.XML)).thenReturn(null);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  // === validateAsset — unsupported asset type path ===

  @Test
  void validateAsset_unsupportedContentType_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAssetWithContentType(ASSET_ID, "application/pdf"));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAsset_nonRdfAsset_nullContentType_throwsVerificationException() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, null);

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  // === validateAssets — multi-asset SHACL ===

  @Test
  void validateAssets_multipleRdfAssets_mergedValidation_returnsConformingResponse() {
    ContentAccessor shapeContent = new ContentAccessorDirect("shape ttl");

    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(7L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(ASSET_ID, ASSET_ID_2), response.getAssetIds());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(List.of(7L), response.getValidationResultIds());

    @SuppressWarnings("unchecked") // ArgumentCaptor raw type for parameterized List
    ArgumentCaptor<List<AssetMetadata>> assetsCaptor = ArgumentCaptor.forClass(List.class);
    verify(shaclValidationStrategy).validate(assetsCaptor.capture(), anyList());
    assertEquals(2, assetsCaptor.getValue().size());
    assertEquals(ASSET_ID, assetsCaptor.getValue().get(0).getId());
    assertEquals(ASSET_ID_2, assetsCaptor.getValue().get(1).getId());
  }

  @Test
  void validateAssets_multipleRdfAssets_nonConformingResult_returnsViolations() {
    ContentAccessor shapeContent = new ContentAccessorDirect("shape ttl");

    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(NON_CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(9L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertFalse(response.getConforms());
    assertEquals(List.of(9L), response.getValidationResultIds());
    assertNotNull(response.getReport());
    assertFalse(response.getReport().getViolations().isEmpty());
  }

  @Test
  void validateAssets_multipleRdfAssets_validateAll_compositeShapeFound_returnsConforming() {
    ContentAccessor composite = new ContentAccessorDirect("composite shape");

    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(10L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(10L), response.getValidationResultIds());
    assertEquals(List.of(ASSET_ID, ASSET_ID_2), response.getAssetIds());
    assertTrue(response.getSchemaIds().contains(SCHEMA_ID));
  }

  @Test
  void validateAssets_emptyAssetIds_throwsClientException() {
    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of());

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_nullAssetIds_throwsClientException() {
    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(null);

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_tooManyAssetIds_throwsClientException() {
    List<String> ids = java.util.stream.IntStream.rangeClosed(1, 21)
        .mapToObj(i -> "https://example.org/asset/" + i)
        .toList();

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(ids);

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_nonRdfAsset_throwsVerificationException() {
    AssetMetadata nonRdf = buildNonRdfAssetWithContentType("https://example.org/asset/pdf", "application/pdf");

    when(assetStore.getById(nonRdf.getId())).thenReturn(nonRdf);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(nonRdf.getId()));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_shaclModuleDisabled_throwsVerificationException() {
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_multipleShapeSchemas_verifyBothShapesParsedAndReturnsConforming() {
    String schemaId2 = "https://example.org/schema/shape/2";
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shape1 = new ContentAccessorDirect("shape1");
    ContentAccessor shape2 = new ContentAccessorDirect("shape2");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchemaRecord(schemaId2)).thenReturn(buildShapeRecord(schemaId2));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shape1);
    when(schemaStore.getSchema(schemaId2)).thenReturn(shape2);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(8L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID, schemaId2));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertNull(response.getReport());
    assertEquals(List.of(SCHEMA_ID, schemaId2), response.getSchemaIds());
    // Both shape ContentAccessors are passed together to validate for merging inside the strategy
    @SuppressWarnings("unchecked") // ArgumentCaptor raw type for parameterized List
    ArgumentCaptor<List<ContentAccessor>> schemasCaptor = ArgumentCaptor.forClass(List.class);
    verify(shaclValidationStrategy).validate(anyList(), schemasCaptor.capture());
    assertEquals(2, schemasCaptor.getValue().size());
  }

  @Test
  void validateAssets_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    givenShaclModuleEnabled();

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_schemaTypeNotShape_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_validateAll_noShapesFound_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(null);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setValidateAgainstAllSchemas(true);

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  // === storeResult — result content ===

  @Test
  void validateAsset_storesResultWithCorrectValidatorIds() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(9L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    service.validateAssets(request);

    ArgumentCaptor<ValidationResultRecord> captor =
        ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertEquals(List.of(ASSET_ID), stored.assetIds());
    assertEquals(List.of(SCHEMA_ID), stored.validatorIds());
    assertEquals(ValidatorType.SHACL, stored.validatorType());
    assertTrue(stored.conforms());
    assertNotNull(stored.validatedAt());
    assertNull(stored.report());
  }

  @Test
  void validateAsset_jsonAsset_storesResultWithJsonSchemaValidatorType() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/json");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(schemaContent);
    when(jsonSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(10L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    service.validateAssets(request);

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertEquals(List.of(ASSET_ID), stored.assetIds());
    assertEquals(List.of(JSON_SCHEMA_ID), stored.validatorIds());
    assertEquals(ValidatorType.JSON_SCHEMA, stored.validatorType());
    assertTrue(stored.conforms());
    assertNotNull(stored.validatedAt());
  }

  @Test
  void validateAsset_xmlAsset_storesResultWithXmlSchemaValidatorType() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/xml");
    ContentAccessor schemaContent = new ContentAccessorDirect("<xs:schema/>");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchema(XML_SCHEMA_ID)).thenReturn(schemaContent);
    when(xmlSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(11L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    service.validateAssets(request);

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertEquals(List.of(ASSET_ID), stored.assetIds());
    assertEquals(List.of(XML_SCHEMA_ID), stored.validatorIds());
    assertEquals(ValidatorType.XML_SCHEMA, stored.validatorType());
    assertTrue(stored.conforms());
    assertNotNull(stored.validatedAt());
  }

  @Test
  void validateAsset_nonConforming_storesRawReport() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(NON_CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(10L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    service.validateAssets(request);

    ArgumentCaptor<ValidationResultRecord> captor =
        ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertFalse(stored.conforms());
    assertNotNull(stored.validatedAt());
    assertEquals("constraint violation", stored.report());
  }

  // === strategy dispatch isolation ===

  @Test
  void validateAssets_turtleRdfAsset_explicitShape_dispatchesToShaclStrategyOnly() {
    AssetMetadata asset = buildTurtleRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(50L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    service.validateAssets(request);

    verify(shaclValidationStrategy).validate(anyList(), anyList());
    verify(jsonSchemaValidationStrategy, never()).validate(anyList(), anyList());
    verify(xmlSchemaValidationStrategy, never()).validate(anyList(), anyList());
  }

  @Test
  void validateAssets_jsonAsset_explicitJsonSchema_dispatchesToJsonStrategyOnly() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/json");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(schemaContent);
    when(jsonSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(51L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    service.validateAssets(request);

    verify(jsonSchemaValidationStrategy).validate(anyList(), anyList());
    verify(shaclValidationStrategy, never()).validate(anyList(), anyList());
    verify(xmlSchemaValidationStrategy, never()).validate(anyList(), anyList());
  }

  @Test
  void validateAssets_xmlAsset_explicitXmlSchema_dispatchesToXmlStrategyOnly() {
    AssetMetadata asset = buildNonRdfAssetWithContentType(ASSET_ID, "application/xml");
    ContentAccessor schemaContent = new ContentAccessorDirect("<xs:schema/>");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchema(XML_SCHEMA_ID)).thenReturn(schemaContent);
    when(xmlSchemaValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(52L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    service.validateAssets(request);

    verify(xmlSchemaValidationStrategy).validate(anyList(), anyList());
    verify(shaclValidationStrategy, never()).validate(anyList(), anyList());
    verify(jsonSchemaValidationStrategy, never()).validate(anyList(), anyList());
  }

  @Test
  void validateAssets_twoRdfAssets_validateAll_onlyShaclStrategyDispatched() {
    ContentAccessor composite = new ContentAccessorDirect("composite shape");

    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(assetStore.getById(ASSET_ID_2)).thenReturn(buildRdfAsset(ASSET_ID_2));
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidationStrategy.validate(anyList(), anyList())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(53L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID, ASSET_ID_2));
    request.setValidateAgainstAllSchemas(true);

    service.validateAssets(request);

    verify(shaclValidationStrategy).validate(anyList(), anyList());
    verify(jsonSchemaValidationStrategy, never()).validate(anyList(), anyList());
    verify(xmlSchemaValidationStrategy, never()).validate(anyList(), anyList());
  }


  private static AssetMetadata buildRdfAsset(String id) {
    return new AssetMetadata(RDF_HASH, id, AssetStatus.ACTIVE,
        ISSUER_DID, List.of(), Instant.now(), Instant.now(),
        new ContentAccessorDirect("{\"@context\":{}}"));
  }

  private static AssetMetadata buildRdfXmlAsset(String id) {
    return new AssetMetadata(RDFXML_HASH, id, AssetStatus.ACTIVE,
        ISSUER_DID, List.of(), Instant.now(), Instant.now(),
        new ContentAccessorDirect("<?xml version=\"1.0\"?><rdf:RDF/>"));
  }

  private static AssetMetadata buildTurtleRdfAsset(String id) {
    return new AssetMetadata(TURTLE_HASH, id, AssetStatus.ACTIVE,
        ISSUER_DID, List.of(), Instant.now(), Instant.now(),
        new ContentAccessorDirect("@prefix ex: <https://example.org/> ."));
  }

  private static AssetMetadata buildNonRdfAssetWithContentType(String id, String contentType) {
    AssetMetadata asset = new AssetMetadata(NONRDF_HASH, id, AssetStatus.ACTIVE,
        ISSUER_DID, List.of(), Instant.now(), Instant.now(), null);
    asset.setContentType(contentType);
    return asset;
  }

  private static SchemaRecord buildShapeRecord(String id) {
    return new SchemaRecord(id, id, SchemaType.SHAPE, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null);
  }

  private static SchemaRecord buildJsonRecord(String id) {
    return new SchemaRecord(id, id, SchemaType.JSON, "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}",
        null);
  }

  private static SchemaRecord buildXmlRecord(String id) {
    return new SchemaRecord(id, id, SchemaType.XML, "<xs:schema/>", null);
  }

  private void registerStrategyList() {
    when(strategies.iterator()).thenAnswer(inv ->
        List.of(shaclValidationStrategy, jsonSchemaValidationStrategy, xmlSchemaValidationStrategy).iterator());
    when(strategies.stream()).thenAnswer(inv ->
        Stream.of(shaclValidationStrategy, jsonSchemaValidationStrategy, xmlSchemaValidationStrategy));
  }

  private void registerShaclStrategy() {
    when(shaclValidationStrategy.type()).thenReturn(ValidatorType.SHACL);
    when(shaclValidationStrategy.moduleType()).thenReturn(SchemaModuleType.SHACL);
    when(shaclValidationStrategy.acceptsSchema(any())).thenAnswer(
        inv -> ((SchemaRecord) inv.getArgument(0)).type() == SchemaType.SHAPE);
    when(shaclValidationStrategy.appliesTo(any())).thenAnswer(
        inv -> ((AssetMetadata) inv.getArgument(0)).getContentAccessor() != null);
  }

  private void registerJsonStrategy() {
    JsonSchemaValidationStrategy jsonApplicabilityDelegate =
        new JsonSchemaValidationStrategy(mock(FileStore.class),
            new ObjectMapper());

    when(jsonSchemaValidationStrategy.type()).thenReturn(ValidatorType.JSON_SCHEMA);
    when(jsonSchemaValidationStrategy.moduleType()).thenReturn(SchemaModuleType.JSON_SCHEMA);
    when(jsonSchemaValidationStrategy.acceptsSchema(any())).thenAnswer(
        inv -> ((SchemaRecord) inv.getArgument(0)).type() == SchemaType.JSON);
    when(jsonSchemaValidationStrategy.appliesTo(any())).thenAnswer(
        inv -> jsonApplicabilityDelegate.appliesTo((AssetMetadata) inv.getArgument(0)));
  }

  private void registerXmlStrategy() {
    ObjectProvider<DocumentBuilderFactory> documentBuilderFactoryProvider = createProviderMock();
    ObjectProvider<SchemaFactory> schemaFactoryProvider = createProviderMock();
    XmlSchemaValidationStrategy xmlApplicabilityDelegate =
        new XmlSchemaValidationStrategy(
            mock(FileStore.class),
            documentBuilderFactoryProvider,
            schemaFactoryProvider);

    when(xmlSchemaValidationStrategy.type()).thenReturn(ValidatorType.XML_SCHEMA);
    when(xmlSchemaValidationStrategy.moduleType()).thenReturn(SchemaModuleType.XML_SCHEMA);
    when(xmlSchemaValidationStrategy.acceptsSchema(any())).thenAnswer(
        inv -> ((SchemaRecord) inv.getArgument(0)).type() == SchemaType.XML);
    when(xmlSchemaValidationStrategy.appliesTo(any())).thenAnswer(
        inv -> xmlApplicabilityDelegate.appliesTo((AssetMetadata) inv.getArgument(0)));
  }

  private void givenShaclModuleEnabled() {
    registerStrategyList();
    registerShaclStrategy();
    when(moduleConfigService.isModuleEnabled(any())).thenReturn(false);
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
  }

  private void givenJsonModuleEnabled() {
    registerStrategyList();
    registerJsonStrategy();
    when(moduleConfigService.isModuleEnabled(any())).thenReturn(false);
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.JSON_SCHEMA)).thenReturn(true);
  }

  private void givenXmlModuleEnabled() {
    registerStrategyList();
    registerXmlStrategy();
    when(moduleConfigService.isModuleEnabled(any())).thenReturn(false);
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.XML_SCHEMA)).thenReturn(true);
  }
}
