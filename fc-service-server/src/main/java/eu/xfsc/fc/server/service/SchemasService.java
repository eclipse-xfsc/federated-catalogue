package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.OntologySchema;
import eu.xfsc.fc.api.generated.model.SchemaResult;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreResult;
import eu.xfsc.fc.server.generated.controller.SchemasApiDelegate;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.SchemasApiDelegate} interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemasService implements SchemasApiDelegate {

  private final SchemaStore schemaStore;
  private final HttpServletRequest httpServletRequest;
  
  /**
   * Service method for GET /schemas/{schemaId} : Get a specific schema.
   *
   * <p>Sets the Content-Type header based on schema type: {@code application/schema+json} for
   * JSON schemas, {@code application/xml} for XML schemas, and default content negotiation for
   * RDF types (ontologies, shapes, vocabularies).
   *
   * @param id Identifier of the Schema. (required)
   * @return The schema content with appropriate Content-Type for the given identifier. (status code 200)
   * @throws NotFoundException if no schema exists with the given id (status code 404)
   */
  @Override
  public ResponseEntity<String> getSchema(String id) {
    String schemaId = URLDecoder.decode(id, Charset.defaultCharset());
    SchemaRecord record = schemaStore.getSchemaRecord(schemaId);
    var responseBuilder = ResponseEntity.ok();
    switch (record.type()) {
      case JSON -> responseBuilder.contentType(MediaType.parseMediaType(SchemaStore.MEDIA_TYPE_JSON_SCHEMA));
      case XML -> responseBuilder.contentType(MediaType.APPLICATION_XML);
      default -> {} // RDF types: preserve original content negotiation behavior
    }
    return responseBuilder.body(record.content());
  }

  /**
   * Service method for GET /schemas : Get the full list of ontologies, shapes and vocabularies.
   *
   * @return References to ontologies, shapes and vocabularies. (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<OntologySchema> getSchemas() {
     Map<SchemaType, List<String>> schemaListMap = schemaStore.getSchemaList();

    OntologySchema schema = new OntologySchema();
    schema.setOntologies(schemaListMap.get(SchemaType.ONTOLOGY));
    schema.setShapes(schemaListMap.get(SchemaType.SHAPE));
    schema.setVocabularies(schemaListMap.get(SchemaType.VOCABULARY));
    schema.setJsonSchemas(schemaListMap.getOrDefault(SchemaType.JSON, Collections.emptyList()));
    schema.setXmlSchemas(schemaListMap.getOrDefault(SchemaType.XML, Collections.emptyList()));
     return ResponseEntity.ok(schema);
  }

  /**
   * Service method for GET /schemas/latest : Get the latest schema for a given type.
   *
   * @param type Type of the schema. (optional)
   * @param term The URI of the term of the requested asset schema e.g.
   *             &#x60;<a href="http://w3id.org/gaia-x/service#ServiceOffering&#x60">...</a>; (optional)
   * @return The latest schemas for the given type or term. (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<String> getLatestSchema(String type, String term) {
    if (type == null || Arrays.stream(SchemaType.values()).noneMatch(x -> x.name().equalsIgnoreCase(type))) {
      throw new ClientException("Please check the value of the type query parameter!");
    }
    // TODO: 31.08.2022 Why is the term parameter used here (not passed anywhere, not specified in the doс)?
    SchemaType schemaType = SchemaType.valueOf(type.toUpperCase());
    ContentAccessor content = (schemaType == SchemaType.JSON || schemaType == SchemaType.XML)
        ? schemaStore.getLatestSchemaByType(schemaType)
        : schemaStore.getCompositeSchema(schemaType);
    return ResponseEntity.ok(content.getContentAsString());
  }

  /**
   * Service method for POST /schemas : Add a new Schema to the catalogue.
   *
   * <p>Routes to the appropriate handler based on the request's Content-Type header:
   * {@code application/schema+json} for JSON Schema, {@code application/xml} for XSD,
   * and {@code text/turtle}, {@code application/rdf+xml}, {@code application/ld+json}
   * for RDF schemas (OWL, SHACL, SKOS). Unsupported Content-Types are rejected with 400.
   *
   * @param schema The schema content. (required)
   * @return Created (status code 201)
   * @throws ClientException if the Content-Type is not supported (status code 400)
   */
  @Override
  public ResponseEntity<SchemaResult> addSchema(String schema) {
    ContentAccessor content = new ContentAccessorDirect(schema);
    String contentType = httpServletRequest.getContentType();
    SchemaStoreResult storeResult;
    if (SchemaType.isRdfContentType(contentType)) {
      storeResult = schemaStore.addSchema(content);
    } else {
      SchemaType nonRdfType = SchemaType.fromContentType(contentType)
          .orElseThrow(() -> new ClientException(
              "Unsupported Content-Type: %s. Supported: %s"
                  .formatted(contentType, SchemaType.getSupportedContentTypes())));
      storeResult = schemaStore.addSchema(content, nonRdfType);
    }
    SchemaResult result = toSchemaResult(storeResult);
    return ResponseEntity.created(URI.create("/schemas/" + result.getId())).body(result);
  }

  /**
   * Service method for DELETE /schemas/{schemaId} : Delete a Schema.
   *
   * @param schemaId Identifier of the Schema (required)
   * @return Deleted Schema (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<Void> deleteSchema(String id)  {
    String schemaId = URLDecoder.decode(id, Charset.defaultCharset());
    schemaStore.deleteSchema(schemaId);
    return ResponseEntity.ok(null);
  }

  /**
   * Service method for PUT /schemas/{schemaId} : Replace a schema.
   *
   * @param schemaId Identifier of the Schema. (required)
   * @param schema The new ontology (OWL file). (required)
   * @return Updated. (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or HTTP Conflict 409 (status code 409)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<SchemaResult> updateSchema(String id, String schema) {
    String schemaId = URLDecoder.decode(id, Charset.defaultCharset());
    SchemaStoreResult storeResult = schemaStore.updateSchema(schemaId, new ContentAccessorDirect(schema));
    return ResponseEntity.ok(toSchemaResult(storeResult));
  }

  private SchemaResult toSchemaResult(SchemaStoreResult storeResult) {
    SchemaResult result = new SchemaResult();
    result.setId(storeResult.id());
    result.setWarnings(storeResult.warning() != null ? List.of(storeResult.warning()) : Collections.emptyList());
    result.setUploadTime(storeResult.uploadTime());
    return result;
  }
}
