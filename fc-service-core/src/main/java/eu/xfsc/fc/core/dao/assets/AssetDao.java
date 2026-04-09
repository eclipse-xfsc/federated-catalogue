package eu.xfsc.fc.core.dao.assets;

import java.util.List;
import java.util.Optional;

import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectStatusRecord;

public interface AssetDao {

	AssetRecord select(String hash);
	Optional<AssetRecord> selectBySubjectId(String subjectId);
    PaginatedResults<AssetRecord> selectByFilter(AssetFilter filter, boolean withMeta, boolean withContent);
	List<String> selectHashes(String startHash, int count, int chunks, int chunkId);
	List<String> selectExpiredHashes();
	SubjectHashRecord insert(AssetRecord assetRecord);
	SubjectStatusRecord update(String hash, int status);
	SubjectStatusRecord delete(String hash);
	int deleteAll();

	List<AssetRecord> selectVersions(String subjectId);
	PaginatedResults<AssetRecord> selectVersionsPageWithTotal(String subjectId, int page, int size);
	Optional<AssetRecord> selectVersion(String subjectId, int version);
	int getVersionCount(String subjectId);
}
