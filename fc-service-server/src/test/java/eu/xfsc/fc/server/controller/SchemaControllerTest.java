package eu.xfsc.fc.server.controller;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_READ;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class SchemaControllerTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SchemaStore schemaStore;

  String SCHEMA_REQUEST = "{\"ontologies\":null,\"shapes\":null,\"vocabularies\":null}";

  @BeforeTestClass
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    schemaStore.clear();
  }
  
  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void getSchemaByIdShouldReturnSuccessResponse() throws Exception {
    String id = schemaStore.addSchema(new ContentAccessorDirect(getMockFileDataAsString("test-schema.ttl"))).id();
    String schemaId = URLEncoder.encode(id, Charset.defaultCharset());
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}", schemaId)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    schemaStore.deleteSchema(id);
  }

  @Test
  public void getSchemaByIdShouldReturnUnauthorizedResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/schemaId")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void getSchemasShouldReturnSuccessResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  public void getSchemasShouldReturnUnauthorizedResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas")
            .with(csrf())
            .param("offset", "5")
            .param("limit", "10")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void getLatestSchemaShouldReturnSuccessResponse() throws Exception {
    String id = schemaStore.addSchema(new ContentAccessorDirect(getMockFileDataAsString("test-schema.ttl"))).id();
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/latest?type=SHAPE")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    schemaStore.deleteSchema(id);
  }

  @Test
  public void getLatestSchemaShouldReturnUnauthorizedResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/latest")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void getLatestSchemaWithoutTypeShouldReturnBadRequest() throws Exception {
    String id = schemaStore.addSchema(new ContentAccessorDirect(getMockFileDataAsString("test-schema.ttl"))).id();
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/latest")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    schemaStore.deleteSchema(id);
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void getLatestSchemaWithUncorrectedTypeShouldReturnBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/latest?type=testType")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void addSchemaShouldReturnUnauthorizedResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  public void addSchemaWithoutRoleShouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(SCHEMA_REQUEST)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ASSET_READ})
  public void addSchemaWithoutRoleAccessShouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(SCHEMA_REQUEST)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE})
  public void addSchemaShouldReturnSuccessResponse() throws Exception {
    ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(getMockFileDataAsString("test-schema.ttl"))
            .with(csrf())
            .contentType("application/rdf+xml")
    ).andExpect(status().isCreated());

    MvcResult result = resultActions.andReturn();
    String id = Objects.requireNonNull(result.getResponse().getHeader("location"))
        .replace("/schemas/", "");

    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(getMockFileDataAsString("test-schema.ttl"))
            .with(csrf())
            .contentType("application/rdf+xml")
    ).andExpect(status().isConflict());
    schemaStore.deleteSchema(id);
  }


  @Test
  @WithMockUser(roles = {ASSET_READ})
  public void deleteSchemasWithDifferentRoleReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.delete("/schemas/schemaID")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_DELETE})
  public void deleteSchemasReturnSuccessResponse() throws Exception {
    String id = schemaStore.addSchema(new ContentAccessorDirect(getMockFileDataAsString("test-schema.ttl"))).id();
    String schemaId = URLEncoder.encode(id, Charset.defaultCharset());
    mockMvc.perform(MockMvcRequestBuilders.delete("/schemas/{schemaId}", schemaId)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void addSchema_withReadOnlyRole_shouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(SCHEMA_REQUEST)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  public void getSchema_withoutPermissionRole_shouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  public void updateSchema_withReadRole_shouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/schemas")
            .content(getMockFileDataAsString("test-schema.ttl"))
            .with(csrf())
            .contentType("application/rdf+xml"))
        .andExpect(status().isForbidden());
  }

  @Test
  public void updateSchema_withoutAuth_shouldReturnUnauthorizedResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/schemas")
            .content(getMockFileDataAsString("test-schema.ttl"))
            .with(csrf())
            .contentType("application/rdf+xml"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  public void addSchema_withAdminAllRole_shouldReturnSuccessResponse() throws Exception {
    ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(getMockFileDataAsString("test-schema.ttl"))
            .with(csrf())
            .contentType("application/rdf+xml"))
        .andExpect(status().isCreated());

    String id = Objects.requireNonNull(resultActions.andReturn().getResponse().getHeader("location"))
        .replace("/schemas/", "");
    schemaStore.deleteSchema(id);
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  public void getSchema_withAdminAllRole_shouldReturnSuccessResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE})
  public void addSchema_validJsonSchema_returns201() throws Exception {
    String jsonSchema = getMockFileDataAsString("test-json-schema.json");

    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(jsonSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("https://example.org/schemas/test-person"));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE})
  public void addSchema_invalidJsonSchema_returns422() throws Exception {
    String invalidSchema = "{ this is not valid JSON at all";

    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(invalidSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE})
  public void addSchema_validXmlSchema_returns201() throws Exception {
    String xsd = getMockFileDataAsString("test-xml-schema.xsd");

    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(xsd)
            .with(csrf())
            .contentType("application/xml"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("http://example.org/test-config"));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE})
  public void addSchema_invalidXmlSchema_returns422() throws Exception {
    String invalidXsd = "<?xml version=\"1.0\"?><xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<xs:element name=\"broken\" type=\"xs:nonExistentType\"/></xs:schema>";

    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(invalidXsd)
            .with(csrf())
            .contentType("application/xml"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  public void getSchemas_withNonRdfSchemas_includesJsonAndXml() throws Exception {
    String jsonSchema = getMockFileDataAsString("test-json-schema.json");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(jsonSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isCreated());

    String xsd = getMockFileDataAsString("test-xml-schema.xsd");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(xsd)
            .with(csrf())
            .contentType("application/xml"))
        .andExpect(status().isCreated());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonSchemas").isArray())
        .andExpect(jsonPath("$.jsonSchemas[0]").value("https://example.org/schemas/test-person"))
        .andExpect(jsonPath("$.xmlSchemas").isArray())
        .andExpect(jsonPath("$.xmlSchemas[0]").value("http://example.org/test-config"));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  public void getSchema_jsonSchemaById_returns200() throws Exception {
    String jsonSchema = getMockFileDataAsString("test-json-schema.json");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(jsonSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isCreated());

    String schemaId = URLEncoder.encode("https://example.org/schemas/test-person", Charset.defaultCharset());
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}", schemaId)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/schema+json"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"$schema\"")));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_DELETE})
  public void deleteSchema_jsonSchema_returns200() throws Exception {
    String jsonSchema = getMockFileDataAsString("test-json-schema.json");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(jsonSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isCreated());

    String schemaId = URLEncoder.encode("https://example.org/schemas/test-person", Charset.defaultCharset());
    mockMvc.perform(MockMvcRequestBuilders.delete("/schemas/{schemaId}", schemaId)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  public void getLatestSchema_jsonType_returns200() throws Exception {
    String jsonSchema = getMockFileDataAsString("test-json-schema.json");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(jsonSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isCreated());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/latest")
            .param("type", "json")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"$schema\"")));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  public void getLatestSchema_xmlType_returns200() throws Exception {
    String xsd = getMockFileDataAsString("test-xml-schema.xsd");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(xsd)
            .with(csrf())
            .contentType("application/xml"))
        .andExpect(status().isCreated());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/latest")
            .param("type", "xml")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("xs:schema")));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  public void getSchema_xmlSchemaById_returns200() throws Exception {
    String xsd = getMockFileDataAsString("test-xml-schema.xsd");
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(xsd)
            .with(csrf())
            .contentType("application/xml"))
        .andExpect(status().isCreated());

    String schemaId = URLEncoder.encode("http://example.org/test-config", Charset.defaultCharset());
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}", schemaId)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_XML))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("xs:schema")));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  public void addSchema_invalidJsonSchema_noDbRecordCreated() throws Exception {
    String invalidSchema = "{ this is not valid JSON at all";
    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(invalidSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isUnprocessableEntity());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonSchemas").isArray())
        .andExpect(jsonPath("$.jsonSchemas").isEmpty());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE})
  public void addSchema_validJsonSchema_returnsUploadTime() throws Exception {
    String jsonSchema = getMockFileDataAsString("test-json-schema.json");

    mockMvc.perform(MockMvcRequestBuilders.post("/schemas")
            .content(jsonSchema)
            .with(csrf())
            .contentType("application/schema+json"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uploadTime").isString())
        .andExpect(jsonPath("$.uploadTime", org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));
  }
}
