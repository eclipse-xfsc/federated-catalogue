package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.OntologySchema;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.server.generated.controller.SchemasApiDelegate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.SchemasApiDelegate} interface.
 */
@Slf4j
@Service
public class SchemasService implements SchemasApiDelegate {
  @Autowired
  private SchemaStore schemaStore;
  
  /**
   * Service method for GET /schemas/{schemaId} : Get a specific schema.
   *
   * @param id Identifier of the Schema. (required)
   * @return The schema for the given identifier. Depending on the type of the schema, either an ontology,
   *         shape graph or controlled vocabulary is returned. (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<String> getSchema(String id) {
    String schemaId = URLDecoder.decode(id, Charset.defaultCharset());
    log.debug("getSchema.enter; got schemaId: {}; decode: {}", id, schemaId);
    ContentAccessor accessor = schemaStore.getSchema(schemaId);
    if (accessor == null) {
      throw new NotFoundException("There is no Schema with id " + schemaId);
    }
    String schema = accessor.getContentAsString();
    log.debug("getSchema.exit; returning schema of length: {}", schema.length());
    return ResponseEntity.ok(schema);
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
    log.debug("getSchemas.enter");
    Map<SchemaType, List<String>> schemaListMap = schemaStore.getSchemaList();

    OntologySchema schema = new OntologySchema();
    schema.setOntologies(schemaListMap.get(SchemaType.ONTOLOGY));
    schema.setShapes(schemaListMap.get(SchemaType.SHAPE));
    schema.setVocabularies(schemaListMap.get(SchemaType.VOCABULARY));
    log.debug("getSchema.exit, returning {}", schema);
    return ResponseEntity.ok(schema);
  }

  /**
   * Service method for GET /schemas/latest : Get the latest schema for a given type.
   *
   * @param type Type of the schema. (optional)
   * @param term The URI of the term of the requested Self-Description schema e.g.
   *             &#x60;<a href="http://w3id.org/gaia-x/service#ServiceOffering&#x60">...</a>; (optional)
   * @return The latest schemas for the given type or term. (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<String> getLatestSchema(String type, String term) {
    log.debug("getLatestSchemas.enter; got type: {}, term: {}", type, term);
    if (type == null || Arrays.stream(SchemaType.values()).noneMatch(x -> x.name().equalsIgnoreCase(type))) {
      throw new ClientException("Please check the value of the type query parameter!");
    }
    // TODO: 31.08.2022 Why is the term parameter used here (not passed anywhere, not specified in the doс)?
    String schema = schemaStore.getCompositeSchema(SchemaType.valueOf(type.toUpperCase())).getContentAsString();
    log.debug("getLatestSchemas.exit; returning schema by type: {}", schema);
    return ResponseEntity.ok(schema);
  }

  /**
   * Service method for POST /schemas : Add a new Schema to the catalogue.
   *
   * @param schema The file of the new schema. either an ontology (OWL file), shape (SHACL file)
   *               or controlled vocabulary (SKOS file). (required)
   * @return Created (status code 201)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<Void> addSchema(String schema) {
    log.debug("addSchema.enter; got schema of length: {}", schema == null ? "null" : schema.length());
    String id = schemaStore.addSchema(new ContentAccessorDirect(schema));
    log.debug("addSchema.exit; returning schema id {}", id);
    return ResponseEntity.created(URI.create("/schemas/" + id)).body(null);
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
    log.debug("deleteSchema.enter; got id: {}, schemaId: {}", id, schemaId);
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
  public ResponseEntity<Void> updateSchema(String id, String schema) {
    String schemaId = URLDecoder.decode(id, Charset.defaultCharset());
    log.debug("updateSchema.enter; got id: {}, schemaId: {}, schema of length: {}", id, schemaId, schema == null ? "null" : schema.length());
    schemaStore.updateSchema(schemaId, new ContentAccessorDirect(schema));
    return ResponseEntity.ok(null);
  }
}