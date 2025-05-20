package eu.xfsc.fc.core.service.schemastore;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Strings;

import eu.xfsc.fc.core.dao.SchemaDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.util.HashUtils;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Component
@Transactional
public class SchemaStoreImpl implements SchemaStore {

  @Autowired
  @Qualifier("schemaFileStore")
  private FileStore fileStore;

  @Autowired
  private SchemaDao dao;

  private static final Map<SchemaType, ContentAccessor> COMPOSITE_SCHEMAS = new ConcurrentHashMap<>();


  @Override
  public int initializeDefaultSchemas() {
    log.debug("initializeDefaultSchemas.enter");
    int count = 0;
    int found = dao.getSchemaCount();
    if (found == 0) {
      try {	
        count += addSchemasFromDirectory("defaultschema/ontology");
        count += addSchemasFromDirectory("defaultschema/shacl");
        log.info("initializeDefaultSchemas; added {} default schemas", count);
        found = dao.getSchemaCount();// it returns 0 for some reason
      } catch (IOException ex) {
    	log.error("initializeDfaultSchemas.error", ex);
    	throw new ServerException(ex);
      }
    }
    log.info("initializeDefaultSchemas.exit; {} schemas found in Schema DB", found);
    return count;
  }  
  
  private int addSchemasFromDirectory(String path) throws IOException {
    PathMatchingResourcePatternResolver scanner = new PathMatchingResourcePatternResolver();
    org.springframework.core.io.Resource[] resources = scanner.getResources(path + "/*");
    int cnt = 0;
    for (org.springframework.core.io.Resource resource: resources) {
      log.debug("addSchemasFromDirectory; Adding schema: {}", resource.getFilename());
      String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      addSchema(new ContentAccessorDirect(content));
      cnt++;
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

  private SchemaRecord analyzeSchemaRecord(ContentAccessor schema) {
    SchemaAnalysisResult result = analyzeSchema(schema);
	if (!result.isValid()) {
	  throw new VerificationException("Schema is not valid: " + result.getErrorMessage());
	}
		    
    String nameHash;
	String schemaId = result.getExtractedId();
	if (Strings.isNullOrEmpty(schemaId)) {
	  nameHash = HashUtils.calculateSha256AsHex(schema.getContentAsString());
	} else {
	  nameHash = HashUtils.calculateSha256AsHex(schemaId);
	}
    return new SchemaRecord(schemaId, nameHash, result.getSchemaType(), schema.getContentAsString(), result.getExtractedUrls());  
  }

  @Override
  public String addSchema(ContentAccessor schema) {
    SchemaRecord newRecord = analyzeSchemaRecord(schema);
    try {
      if (!dao.insert(newRecord)) {
        throw new ServerException("DB error, schema not inserted");
      }
    } catch (DuplicateKeyException ex) {
      if (ex.getMessage().contains("schematerms_pkey")) {
        throw new ConflictException("Schema redefines existing terms");
      }
      if (ex.getMessage().contains("schemafiles_pkey")) {
        throw new ConflictException("A schema with id " + newRecord.getId() + " already exists.");
      }
      log.info("addSchema; conflict: {}", ex.getMessage());
      throw new ServerException(ex);
    }
    
    COMPOSITE_SCHEMAS.remove(newRecord.type());
    return newRecord.getId();
  }
  
  @Override
  public void updateSchema(String identifier, ContentAccessor schema) {
    SchemaRecord newRecord = analyzeSchemaRecord(schema);
    if (newRecord.schemaId() != null && !identifier.equals(newRecord.schemaId())) {
      throw new ClientException("Given schema does not have the same Identifier as the old schema: " + identifier + " <> " + newRecord.schemaId());
    }
    
    try {
      if (dao.update(identifier, newRecord.content(), newRecord.terms()) == 0) {
        throw new NotFoundException("Schema with id " + identifier + " was not found");
      }
    } catch (DuplicateKeyException ex) {
      //try {	
      //  ((SchemaDaoImpl) dao).getDataSource().getConnection().rollback();
      //} catch (SQLException se) {
      //  log.debug("updateSchema; error at rollback", se);	  
      //}
      if (ex.getMessage().contains("schematerms_pkey")) {
        throw new ConflictException("Schema redefines existing terms");
      }
      log.info("updateSchema; conflict: {}", ex.getMessage());
      throw new ServerException(ex);
    }

    COMPOSITE_SCHEMAS.remove(newRecord.type());
    // SDs will be revalidated in a separate thread.
  }

  @Override
  public void deleteSchema(String identifier) {
	Integer type = dao.delete(identifier);
    log.debug("deleteSchema; delete result: {} for id: {}", type, identifier);
    if (type == null) {
      throw new NotFoundException("Schema with id " + identifier + " was not found");
    }
    COMPOSITE_SCHEMAS.remove(SchemaType.values()[type]);
  }

  @Override
  public Map<SchemaType, List<String>> getSchemaList() {
    Map<Integer, Collection<String>> res = dao.selectSchemas();
    //return res.entrySet().stream().map(e -> Pair.of(SchemaType.values()[e.getKey()], e.getValue()).collect(Collectors.toMap(p.getLeft(), p.getRight())));
    Map<SchemaType, List<String>> result = new HashMap<>();
    for (Map.Entry<Integer, Collection<String>> e: res.entrySet()) {
      result.put(SchemaType.values()[e.getKey()], new ArrayList<>(e.getValue()));	
    }
    return result;
  }

  @Override
  public ContentAccessor getSchema(String identifier) {
	SchemaRecord existing = dao.select(identifier);  
    if (existing == null) {
      throw new NotFoundException("Schema with id " + identifier + " was not found");
    }
    return new ContentAccessorDirect(existing.content());
  }

  @Override
  public Map<SchemaType, List<String>> getSchemasForTerm(String entity) {
    Map<Integer, Collection<String>> res = dao.selectSchemasByTerm(entity);
    //return result.entrySet().stream().map(e -> SchemaType.values()[e.getKey()]).collect(Collectors.toMap(e.null, null))
    Map<SchemaType, List<String>> result = new HashMap<>();
    for (Map.Entry<Integer, Collection<String>> e: res.entrySet()) {
      result.put(SchemaType.values()[e.getKey()], new ArrayList<>(e.getValue()));	
    }
    return result;
  }

  @Override
  public ContentAccessor getCompositeSchema(SchemaType type) {
    return COMPOSITE_SCHEMAS.computeIfAbsent(type, t -> createCompositeSchema(t));
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
