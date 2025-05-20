package eu.xfsc.fc.core.dao;

import java.util.Collection;
import java.util.Map;

import eu.xfsc.fc.core.service.schemastore.SchemaRecord;

public interface SchemaDao {

	int getSchemaCount();
	SchemaRecord select(String schemaId);
	Map<Integer, Collection<String>> selectSchemas();
	Map<Integer, Collection<String>> selectSchemasByTerm(String term);
	boolean insert(SchemaRecord schema);
	int update(String id, String content, Collection<String> terms);
	Integer delete(String schemaId);
	int deleteAll();

}
