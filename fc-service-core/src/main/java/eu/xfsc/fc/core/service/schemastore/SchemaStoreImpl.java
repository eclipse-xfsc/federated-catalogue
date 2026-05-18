package eu.xfsc.fc.core.service.schemastore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import eu.xfsc.fc.core.dao.schemas.SchemaDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.FilteredModel;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.ValidationType;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaStoreImpl implements SchemaStore {

  @Qualifier("schemaFileStore")
  private final FileStore fileStore;
  private final SchemaDao dao;
  private final ProtectedNamespaceFilter protectedNamespaceFilter;
  private final TrustFrameworkRegistry trustFrameworkRegistry;

  @Autowired
  private ObjectProvider<DocumentBuilderFactory> secureDocumentBuilderFactoryProvider;

  @Autowired
  private ObjectProvider<SchemaFactory> secureXmlSchemaFactoryProvider;

  private static final Map<SchemaType, ContentAccessor> COMPOSITE_SCHEMAS = new ConcurrentHashMap<>();
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  @Override
  public int initializeDefaultSchemas() {
    log.debug("initializeDefaultSchemas.enter");
    int count = 0;
    int found = dao.getSchemaCount();
    if (found == 0) {
      try {
        count += addSchemasFromBundles();
        count += addSchemasFromDirectory("defaultschema/ontology");
        count += addSchemasFromDirectory("defaultschema/shacl");
        log.info("initializeDefaultSchemas; added {} default schemas", count);
        found = dao.getSchemaCount();
      } catch (IOException ex) {
        log.error("initializeDefaultSchemas.error", ex);
        throw new ServerException(ex);
      }
    }
    log.info("initializeDefaultSchemas.exit; {} schemas found in Schema DB", found);
    return count;
  }

  private int addSchemasFromBundles() {
    int cnt = 0;
    for (TrustFrameworkBundle bundle : trustFrameworkRegistry.getBundles()) {
      if (bundle.config().validationType() != ValidationType.SHACL) {
        continue;
      }
      if (bundle.ontology() != null) {
        addSchema(bundle.ontology());
        cnt++;
        log.debug("addSchemasFromBundles; added ontology for bundle '{}'", bundle.config().id());
      }
      if (bundle.shapes() != null) {
        addSchema(bundle.shapes());
        cnt++;
        log.debug("addSchemasFromBundles; added shapes for bundle '{}'", bundle.config().id());
      }
    }
    return cnt;
  }

  private int addSchemasFromDirectory(String path) throws IOException {
    PathMatchingResourcePatternResolver scanner = new PathMatchingResourcePatternResolver();
    org.springframework.core.io.Resource[] resources;
    try {
      resources = scanner.getResources(path + "/*");
    } catch (java.io.FileNotFoundException ex) {
      log.debug("addSchemasFromDirectory; overlay path '{}' not found, skipping", path);
      return 0;
    }
    int cnt = 0;
    for (org.springframework.core.io.Resource resource : resources) {
      log.debug("addSchemasFromDirectory; Adding schema: {}", resource.getFilename());
      String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      try {
        addSchema(new ContentAccessorDirect(content));
        cnt++;
      } catch (ConflictException ex) {
        log.debug("addSchemasFromDirectory; Skipping duplicate schema: {}", resource.getFilename());
      }
    }
    return cnt;
  }

  /**
   * Analyze the given schema character content.
   *
   * @param schema The schema to analyse.
   * @return The analysis results.
   */
  public SchemaAnalysisResult analyzeSchema(ContentAccessor schema) {
    SchemaAnalysisResult result = new SchemaAnalysisResult();
    Set<String> extractedUrlsSet = new HashSet<>();
    Model model = ModelFactory.createDefaultModel();

    List<String> schemaType = Arrays.asList("JSON-LD", "RDF/XML", "TTL");
    for (String type : schemaType) {
      try {
        model.read(schema.getContentAsStream(), null, type);
        result.setValid(true);
        break;
      } catch (Exception exc) {
        result.setValid(false);
        result.setErrorMessage(exc.getMessage());
      }
    }
    if (result.isValid()) {
      FilteredModel filteredModel = protectedNamespaceFilter.filterModel(model, "schema import");
      model = filteredModel.model();
      if (filteredModel.hasWarning()) {
        result.setWarning(filteredModel.warning());
      }
    }
    if (model.contains(null, RDF.type, SHACLM.NodeShape)
        || model.contains(null, RDF.type, SHACLM.PropertyShape)) {
      result.setSchemaType(SchemaType.SHAPE);
      result.setExtractedId(null);
    } else {
      ResIterator resIteratorProperty = model.listResourcesWithProperty(RDF.type, OWL.Ontology);
      if (resIteratorProperty.hasNext()) {
        Resource resource = resIteratorProperty.nextResource();
        result.setSchemaType(SchemaType.ONTOLOGY);
        result.setExtractedId(resource.getURI());
        if (resIteratorProperty.hasNext()) {
          result.setErrorMessage("Ontology Schema has multiple Ontology IRIs");
          result.setExtractedId(null);
          result.setValid(false);
        }
      } else {
        resIteratorProperty = model.listResourcesWithProperty(RDF.type, SKOS.ConceptScheme);
        if (resIteratorProperty.hasNext()) {
          Resource resource = resIteratorProperty.nextResource();
          result.setSchemaType(SchemaType.VOCABULARY);
          result.setExtractedId(resource.getURI());
          if (resIteratorProperty.hasNext()) {
            result.setErrorMessage("Vocabulary contains multiple concept schemes");
            result.setExtractedId(null);
            result.setValid(false);
          }
        } else {
          result.setValid(false);
          result.setErrorMessage("Schema type not supported");
        }
      }
    }
    if (result.isValid()) {
      switch (result.getSchemaType()) {
        case SHAPE:
          addExtractedUrls(model, SHACLM.NodeShape, extractedUrlsSet);
          addExtractedUrls(model, SHACLM.PropertyShape, extractedUrlsSet);
          break;

        case ONTOLOGY:
          addExtractedUrls(model, OWL2.NamedIndividual, extractedUrlsSet);
          addExtractedUrls(model, RDF.Property, extractedUrlsSet);
          addExtractedUrls(model, OWL2.DatatypeProperty, extractedUrlsSet);
          addExtractedUrls(model, OWL2.ObjectProperty, extractedUrlsSet);
          addExtractedUrls(model, RDFS.Class, extractedUrlsSet);
          addExtractedUrls(model, OWL2.Class, extractedUrlsSet);
          break;

        case VOCABULARY:
          addExtractedUrls(model, SKOS.Concept, extractedUrlsSet);
          break;
        default:
        // this will not happen
      }
    }
    result.setExtractedUrls(extractedUrlsSet);
    return result;
  }

  public void addExtractedUrls(Model model, RDFNode node, Set<String> extractedSet) {
    ResIterator resIteratorNode = model.listResourcesWithProperty(RDF.type, node);
    while (resIteratorNode.hasNext()) {
      Resource rs = resIteratorNode.nextResource();
      extractedSet.add(rs.getURI());
    }
  }

  public boolean isSchemaType(ContentAccessor schema, SchemaType type) {
    SchemaAnalysisResult result = analyzeSchema(schema);
    return result.getSchemaType().equals(type);
  }

  private ContentAccessor createCompositeSchema(SchemaType type) {
    log.debug("createCompositeSchema.enter; got type: {}", type);
    StringWriter out = new StringWriter();
    Map<SchemaType, List<String>> schemaList = getSchemaList();

    List<String> schemaListForType = schemaList.get(type);
    if (schemaListForType == null) {
      log.debug("createCompositeSchema.exit; returning empty content for unknown type");
      return new ContentAccessorDirect("");
    }
    
    Model model = ModelFactory.createDefaultModel();
    Model unionModel = ModelFactory.createDefaultModel();
    for (String schemaId : schemaListForType) {
      ContentAccessor schemaContent = getSchema(schemaId);
      StringReader schemaContentReader = new StringReader(schemaContent.getContentAsString());
      model.read(schemaContentReader, null, "TURTLE");
      unionModel.add(model);
    }
    RDFDataMgr.write(out, unionModel, Lang.TURTLE);
    ContentAccessor content = new ContentAccessorDirect(out.toString());

    log.debug("createCompositeSchema.exit; returning: {}", content.getContentAsString().length());
    try {
      final String compositeSchemaName = "CompositeSchema" + type.name();
      fileStore.replaceFile(compositeSchemaName, content);
      // the ContentAccessor returned from this function is cached until this instance gets a schema change. 
      // That's why it is important that the file-based ContentAccessor is returned, and not the String-based one. 
      // Otherwise, if another instance changes the file because that other instance received a schema change, this instance will 
      // never serve the new content, since it cached the String-based content.
      // By returning the file-based ContentAccessor, a change of the file will automatically update the content that all instances serve.
      content = fileStore.readFile(compositeSchemaName);
    } catch (IOException ex) {
      log.error("createCompositeSchema.error: Failed to store composite schema", ex);
    }
    return content;
  }

  @Override
  public boolean verifySchema(ContentAccessor schema) {
    SchemaAnalysisResult result = analyzeSchema(schema);
    return result.isValid();
  }

  private SchemaAnalysisResult analyzeAndValidate(ContentAccessor schema) {
    SchemaAnalysisResult result = analyzeSchema(schema);
    if (!result.isValid()) {
      throw new VerificationException("Schema is not valid: " + result.getErrorMessage());
    }
    return result;
  }

  private SchemaAnalysisResult analyzeNonRdfSchema(ContentAccessor schema, SchemaType type) {
    SchemaAnalysisResult result = switch (type) {
      case JSON -> analyzeJsonSchema(schema);
      case XML -> analyzeXmlSchema(schema);
      default -> new SchemaAnalysisResult()
              .setValid(false)
              .setErrorMessage("Unsupported non-RDF schema type: " + type + ". Supported types: JSON, XML");
    };
    result.setSchemaType(type);
    result.setExtractedUrls(Collections.emptySet());
    return result;
  }

  private SchemaAnalysisResult analyzeJsonSchema(ContentAccessor schema) {
    try {
      JsonNode schemaNode = JSON_MAPPER.readTree(schema.getContentAsString());
      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
      registry.getSchema(schemaNode);
      String extractedId = schemaNode.has("$id")
          ? schemaNode.get("$id").asText()
          : "urn:uuid:" + UUID.randomUUID();
      return new SchemaAnalysisResult()
              .setValid(true)
              .setExtractedId(extractedId);
    } catch (Exception e) {
      return new SchemaAnalysisResult()
              .setValid(false)
              .setErrorMessage("Invalid JSON Schema: " + e.getMessage());
    }
  }

  private SchemaAnalysisResult analyzeXmlSchema(ContentAccessor schema) {
    try {
      byte[] content = schema.getContentAsString().getBytes(StandardCharsets.UTF_8);
      DocumentBuilderFactory dbf = secureDocumentBuilderFactoryProvider.getObject();
      Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(content));

      SchemaFactory factory = secureXmlSchemaFactoryProvider.getObject();
      factory.newSchema(new DOMSource(doc));

      String targetNs = doc.getDocumentElement().getAttribute("targetNamespace");
      String extractedId = !targetNs.isEmpty() ? targetNs : "urn:uuid:" + UUID.randomUUID();
      return new SchemaAnalysisResult()
              .setValid(true)
              .setExtractedId(extractedId);
    } catch (Exception e) {
      return new SchemaAnalysisResult()
              .setValid(false)
              .setErrorMessage("Invalid XML Schema: " + e.getMessage());
    }
  }

  private SchemaAnalysisResult analyzeAndValidateNonRdf(ContentAccessor schema, SchemaType type) {
    SchemaAnalysisResult result = analyzeNonRdfSchema(schema, type);
    if (!result.isValid()) {
      throw new VerificationException("Schema is not valid: " + result.getErrorMessage());
    }
    return result;
  }

  private SchemaRecord buildSchemaRecord(SchemaAnalysisResult result, ContentAccessor schema) {
    String schemaId = result.getExtractedId();
    String nameHash = Strings.isNullOrEmpty(schemaId)
        ? HashUtils.calculateSha256AsHex(schema.getContentAsString())
        : HashUtils.calculateSha256AsHex(schemaId);
    return new SchemaRecord(schemaId, nameHash, result.getSchemaType(),
        schema.getContentAsString(), result.getExtractedUrls());
  }

  @Override
  public SchemaStoreResult addSchema(ContentAccessor schema) {
    SchemaAnalysisResult analysis = analyzeAndValidate(schema);
    return insertSchema(analysis, schema);
  }

  @Override
  public SchemaStoreResult addSchema(ContentAccessor schema, SchemaType type) {
    SchemaAnalysisResult analysis = analyzeAndValidateNonRdf(schema, type);
    return insertSchema(analysis, schema);
  }

  private SchemaStoreResult insertSchema(SchemaAnalysisResult analysis, ContentAccessor schema) {
    SchemaRecord newRecord = buildSchemaRecord(analysis, schema);
    try {
      if (!dao.insert(newRecord)) {
        throw new ServerException("DB error, schema not inserted");
      }
    } catch (DataIntegrityViolationException ex) {
      String msg = ex.getMessage();
      if (msg.contains("schematerms_pkey") || msg.contains("SchemaTerm")) {
        throw new ConflictException("Schema redefines existing terms");
      }
      if (msg.contains("uq_schemafiles_schemaid") || msg.contains("SchemaFile")) {
        throw new ConflictException("A schema with id " + newRecord.getId() + " already exists.");
      }
      log.info("addSchema; conflict: {}", ex.getMessage());
      throw new ServerException(ex);
    }

    COMPOSITE_SCHEMAS.remove(newRecord.type());
    return new SchemaStoreResult(newRecord.getId(), analysis.getWarning(), newRecord.createdAt());
  }

  @Override
  @Transactional
  public SchemaStoreResult updateSchema(String identifier, ContentAccessor schema) {
    SchemaRecord existing = dao.select(identifier)
        .orElseThrow(() -> new NotFoundException("Schema with id " + identifier + " was not found"));
    SchemaAnalysisResult analysis = existing.type() == SchemaType.JSON || existing.type() == SchemaType.XML
        ? analyzeAndValidateNonRdf(schema, existing.type())
        : analyzeAndValidate(schema);
    SchemaRecord newRecord = buildSchemaRecord(analysis, schema);
    if (newRecord.schemaId() != null && !identifier.equals(newRecord.schemaId())) {
      throw new ClientException("Given schema does not have the same Identifier as the old schema: " + identifier + " <> " + newRecord.schemaId());
    }

    try {
      dao.update(identifier, newRecord.content(), newRecord.terms());
    } catch (DataIntegrityViolationException ex) {
      if (ex.getMessage().contains("schematerms_pkey")) {
        throw new ConflictException("Schema redefines existing terms");
      }
      log.info("updateSchema; conflict: {}", ex.getMessage());
      throw new ServerException(ex);
    }

    COMPOSITE_SCHEMAS.remove(newRecord.type());

    // Envers writes audit entries in beforeTransactionCompletion — after all application code
    // in this transaction — so getVersionCount returns N (committed prior revisions only).
    // currentVersion = N+1 is the revision that will be written when this transaction commits.
    // Note: under true concurrent updates, two threads could read the same N and produce
    // duplicate version numbers. This race is accepted because schema updates are infrequent
    // administrative operations and the cost of pessimistic locking outweighs the risk.
    int previousVersion = dao.getVersionCount(identifier);
    int currentVersion = previousVersion + 1;
    return new SchemaStoreResult(identifier, analysis.getWarning(), null,
        currentVersion, previousVersion > 0 ? previousVersion : null);
  }

  @Override
  public void deleteSchema(String identifier) {
	String typeName = dao.delete(identifier);
    log.debug("deleteSchema; delete result: {} for id: {}", typeName, identifier);
    if (typeName == null) {
      throw new NotFoundException("Schema with id " + identifier + " was not found");
    }
    COMPOSITE_SCHEMAS.remove(SchemaType.valueOf(typeName));
  }

  @Override
  public Map<SchemaType, List<String>> getSchemaList() {
    return toSchemaTypeMap(dao.selectSchemas());
  }

  @Override
  public ContentAccessor getSchema(String identifier) {
    return new ContentAccessorDirect(getSchemaRecord(identifier).content());
  }

  @Override
  public SchemaRecord getSchemaRecord(String identifier) {
      return dao.select(identifier)
              .orElseThrow(() -> new NotFoundException("Schema with id " + identifier + " was not found"));
  }

  @Override
  public Map<SchemaType, List<String>> getSchemasForTerm(String entity) {
    return toSchemaTypeMap(dao.selectSchemasByTerm(entity));
  }

  private Map<SchemaType, List<String>> toSchemaTypeMap(Map<String, Collection<String>> input) {
    Map<SchemaType, List<String>> result = new EnumMap<>(SchemaType.class);
    for (SchemaType type : SchemaType.values()) {
      result.put(type, new ArrayList<>());
    }
    for (Map.Entry<String, Collection<String>> e : input.entrySet()) {
      result.put(SchemaType.valueOf(e.getKey()), new ArrayList<>(e.getValue()));
    }
    return result;
  }

  @Override
  public ContentAccessor getCompositeSchema(SchemaType type) {
    return COMPOSITE_SCHEMAS.computeIfAbsent(type, this::createCompositeSchema);
  }

  @Override
  public ContentAccessor getLatestSchemaByType(SchemaType type) {
    String content = dao.selectLatestContentByType(type.name())
        .orElseThrow(() -> new NotFoundException("No " + type + " schemas found"));
    return new ContentAccessorDirect(content);
  }

  @Override
  public SchemaRecord getSchemaVersion(String identifier, int version) {
    return dao.selectVersion(identifier, version)
        .orElseThrow(() -> new NotFoundException(
            "Schema with id " + identifier + " version " + version + " was not found"));
  }

  @Override
  public List<SchemaRecord> getSchemaVersions(String identifier) {
    List<SchemaRecord> versions = dao.selectVersions(identifier);
    if (versions.isEmpty()) {
      throw new NotFoundException("Schema with id " + identifier + " was not found");
    }
    return versions;
  }

  @Override
  public void clear() {
	int cnt = dao.deleteAll();
    log.debug("clear; deleted {} schemas", cnt);
    try {
      fileStore.clearStorage();
    } catch (IOException ex) {
      log.error("SchemaStoreImpl: Exception while clearing FileStore: {}.", ex.getMessage());
    }
    COMPOSITE_SCHEMAS.clear();
  }

}
