package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.core.service.schemastore.SchemaStore.MEDIA_TYPE_RDF_XML;
import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_READ;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_UPDATE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class SchemaVersioningControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SchemaStore schemaStore;

  @AfterEach
  void cleanUp() {
    schemaStore.clear();
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_UPDATE, SCHEMA_READ})
  void updateSchema_returnsVersionAndPreviousVersion() throws Exception {
    String content = getMockFileDataAsString("test-schema.ttl");
    String id = schemaStore.addSchema(new ContentAccessorDirect(content)).id();
    String encodedId = URLEncoder.encode(id, Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.put("/schemas/{schemaId}", encodedId)
            .content(content)
            .with(csrf())
            .contentType(MEDIA_TYPE_RDF_XML)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.previousVersion").value(1));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  void getSchema_withVersionParam_returnsOriginalContent() throws Exception {
    String contentV1 = getMockFileDataAsString("test-schema.ttl");
    String id = schemaStore.addSchema(new ContentAccessorDirect(contentV1)).id();

    // Update the schema with different content to create version 2
    String contentV2 = getMockFileDataAsString("gax-test-ontology.ttl");
    schemaStore.updateSchema(id, new ContentAccessorDirect(contentV2));

    String encodedId = URLEncoder.encode(id, Charset.defaultCharset());

    // Requesting version 1 must return v1 content, not the current (v2) content
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}", encodedId)
            .param("version", "1")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(contentV1));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  void getSchema_withoutVersion_returnsCurrentVersion() throws Exception {
    String content = getMockFileDataAsString("test-schema.ttl");
    String id = schemaStore.addSchema(new ContentAccessorDirect(content)).id();
    String encodedId = URLEncoder.encode(id, Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}", encodedId)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(content));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  void getSchema_nonExistentVersion_returns404() throws Exception {
    String content = getMockFileDataAsString("test-schema.ttl");
    String id = schemaStore.addSchema(new ContentAccessorDirect(content)).id();
    String encodedId = URLEncoder.encode(id, Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}", encodedId)
            .param("version", "999")
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_READ})
  void getSchemaVersions_returnsOrderedList() throws Exception {
    String content = getMockFileDataAsString("test-schema.ttl");
    String id = schemaStore.addSchema(new ContentAccessorDirect(content)).id();
    schemaStore.updateSchema(id, new ContentAccessorDirect(content));
    String encodedId = URLEncoder.encode(id, Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}/versions", encodedId)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaId").value(id))
        .andExpect(jsonPath("$.versions").isArray())
        .andExpect(jsonPath("$.versions.length()").value(2))
        .andExpect(jsonPath("$.versions[0].version").value(1))
        .andExpect(jsonPath("$.versions[0].isCurrent").value(false))
        .andExpect(jsonPath("$.versions[1].version").value(2))
        .andExpect(jsonPath("$.versions[1].isCurrent").value(true));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_CREATE, SCHEMA_UPDATE, SCHEMA_READ})
  void getSchemaVersions_afterThreeUpdates_returnsThreeVersionsWithCurrentMarked() throws Exception {
    String content = getMockFileDataAsString("test-schema.ttl");
    String id = schemaStore.addSchema(new ContentAccessorDirect(content)).id();
    schemaStore.updateSchema(id, new ContentAccessorDirect(getMockFileDataAsString("gax-test-ontology.ttl")));
    schemaStore.updateSchema(id, new ContentAccessorDirect(getMockFileDataAsString("legal-personShape.ttl")));
    String encodedId = URLEncoder.encode(id, Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}/versions", encodedId)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versions.length()").value(3))
        .andExpect(jsonPath("$.versions[0].version").value(1))
        .andExpect(jsonPath("$.versions[0].isCurrent").value(false))
        .andExpect(jsonPath("$.versions[1].version").value(2))
        .andExpect(jsonPath("$.versions[1].isCurrent").value(false))
        .andExpect(jsonPath("$.versions[2].version").value(3))
        .andExpect(jsonPath("$.versions[2].isCurrent").value(true));
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  void getSchemaVersions_nonExistentSchema_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/schemas/{schemaId}/versions", "nonexistent")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {SCHEMA_READ})
  void updateSchema_withoutUpdateRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/schemas/{schemaId}", "some-id")
            .content("content")
            .with(csrf())
            .contentType(MEDIA_TYPE_RDF_XML))
        .andExpect(status().isForbidden());
  }
}
