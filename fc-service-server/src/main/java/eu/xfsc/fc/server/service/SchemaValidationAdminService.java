package eu.xfsc.fc.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.SchemaValidationModule;
import eu.xfsc.fc.api.generated.model.SchemaValidationStatus;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import eu.xfsc.fc.server.generated.controller.SchemaValidationAdminApiDelegate;
import lombok.RequiredArgsConstructor;

/**
 * Service for schema validation module administration endpoints.
 */
@Service
@RequiredArgsConstructor
public class SchemaValidationAdminService implements SchemaValidationAdminApiDelegate {

  private static final String CONFIG_PREFIX = "schema.module.";
  private static final String CONFIG_SUFFIX = ".enabled";
  private static final Set<String> VALID_MODULE_TYPES = Set.of(
      SchemaModuleType.SHACL, SchemaModuleType.JSON_SCHEMA,
      SchemaModuleType.XML_SCHEMA, SchemaModuleType.OWL);

  private static final Map<String, String> MODULE_NAMES = Map.of(
      SchemaModuleType.SHACL, "SHACL Shapes",
      SchemaModuleType.JSON_SCHEMA, "JSON Schema",
      SchemaModuleType.XML_SCHEMA, "XML Schema",
      SchemaModuleType.OWL, "OWL Ontologies");

  private static final Map<String, String> MODULE_DESCRIPTIONS = Map.of(
      SchemaModuleType.SHACL, "SHACL shape graphs for RDF credential validation",
      SchemaModuleType.JSON_SCHEMA, "JSON Schema for non-RDF JSON asset validation",
      SchemaModuleType.XML_SCHEMA, "XML Schema for non-RDF XML asset validation",
      SchemaModuleType.OWL, "OWL ontologies and SKOS vocabularies");

  /** Maps UI module types to SchemaStore.SchemaType values for counting. */
  private static final Map<String, List<SchemaType>> MODULE_SCHEMA_TYPES = Map.of(
      SchemaModuleType.SHACL, List.of(SchemaType.SHAPE),
      SchemaModuleType.JSON_SCHEMA, List.of(SchemaType.JSON),
      SchemaModuleType.XML_SCHEMA, List.of(SchemaType.XML),
      SchemaModuleType.OWL, List.of(SchemaType.ONTOLOGY, SchemaType.VOCABULARY));

  private final AdminConfigRepository adminConfigRepository;
  private final SchemaStore schemaStore;

  @Override
  public ResponseEntity<SchemaValidationStatus> getSchemaValidationStatus() {
    Map<SchemaType, List<String>> schemas = schemaStore.getSchemaList();
    Map<String, String> moduleConfigs = adminConfigRepository.getByPrefix(CONFIG_PREFIX);

    List<SchemaValidationModule> modules = new ArrayList<>();
    long totalCount = 0;

    for (String type : VALID_MODULE_TYPES) {
      SchemaValidationModule module = new SchemaValidationModule();
      module.setType(type);
      module.setName(MODULE_NAMES.get(type));
      module.setDescription(MODULE_DESCRIPTIONS.get(type));

      long count = MODULE_SCHEMA_TYPES.get(type).stream()
          .mapToLong(st -> schemas.getOrDefault(st, List.of()).size())
          .sum();
      module.setSchemaCount(count);
      totalCount += count;

      String configKey = CONFIG_PREFIX + type + CONFIG_SUFFIX;
      String enabledValue = moduleConfigs.getOrDefault(configKey, "true");
      module.setEnabled(Boolean.parseBoolean(enabledValue));

      modules.add(module);
    }

    SchemaValidationStatus status = new SchemaValidationStatus();
    status.setTotalSchemaCount(totalCount);
    status.setModules(modules);
    return ResponseEntity.ok(status);
  }

  @Override
  @CacheEvict(value = SchemaModuleConfigService.CACHE_NAME, allEntries = true)
  public ResponseEntity<Void> setSchemaModuleEnabled(String type, Boolean enabled) {
    if (!VALID_MODULE_TYPES.contains(type)) {
      throw new ClientException("Invalid module type: " + type
          + ". Valid types: " + String.join(", ", VALID_MODULE_TYPES));
    }
    String key = CONFIG_PREFIX + type + CONFIG_SUFFIX;
    AdminConfigEntry entry = adminConfigRepository.findById(key)
        .orElse(new AdminConfigEntry(key, null, null));
    entry.setConfigValue(String.valueOf(enabled));
    adminConfigRepository.save(entry);
    return ResponseEntity.ok().build();
  }
}
