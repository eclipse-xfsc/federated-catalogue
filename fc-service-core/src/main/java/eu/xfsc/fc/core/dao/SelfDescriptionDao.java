package eu.xfsc.fc.core.dao;

import java.util.List;

import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.service.sdstore.SdMetaRecord;
import eu.xfsc.fc.core.service.sdstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.sdstore.SubjectStatusRecord;

public interface SelfDescriptionDao {
	
	SdMetaRecord select(String hash);
    PaginatedResults<SdMetaRecord> selectByFilter(SdFilter filter, boolean withMeta, boolean withContent);
	List<String> selectHashes(String startHash, int count, int chunks, int chunkId);
	List<String> selectExpiredHashes();
	SubjectHashRecord insert(SdMetaRecord sd);
	SubjectStatusRecord update(String hash, int status);
	SubjectStatusRecord delete(String hash);
	int deleteAll();

}
