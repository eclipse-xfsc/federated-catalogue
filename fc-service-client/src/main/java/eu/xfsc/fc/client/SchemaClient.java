package eu.xfsc.fc.client;

import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.OntologySchema;

public class SchemaClient extends ServiceClient {

    public SchemaClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public SchemaClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public List<OntologySchema> getSchemas(int offset, int limit) {
        Map<String, Object> queryParams = buildPagingParams(offset, limit);
        return doGet("/schemas", Map.of(), queryParams, List.class);
    }

    public void addSchema(OntologySchema schema) {
        doPost("/schemas", schema, Map.of(), Map.of(), Void.class);
    }

    public OntologySchema getSchema(String schemaId) {
        Map<String, Object> pathParams = Map.of("schemaId", schemaId);
        return doGet("/schemas/{schemaId}", pathParams, Map.of(), OntologySchema.class);
    }

    public void deleteSchema(String schemaId) {
        Map<String, Object> pathParams = Map.of("schemaId", schemaId);
        doDelete("/schemas/{schemaId}", pathParams, Map.of(), Void.class);
    }

    public List<OntologySchema> getLatestSchemas() {
        return doGet("/schemas/latest", Map.of(), Map.of(), List.class);
    }

    public OntologySchema getLatestSchemaOfType(String type) {
        Map<String, Object> pathParams = Map.of("type", type);
        return doGet("/schemas/latest/{type}", pathParams, Map.of(), OntologySchema.class);
    }
}
