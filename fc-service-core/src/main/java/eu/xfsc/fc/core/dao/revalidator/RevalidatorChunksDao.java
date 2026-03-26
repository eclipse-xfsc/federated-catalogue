package eu.xfsc.fc.core.dao.revalidator;

public interface RevalidatorChunksDao {

	int findChunkForWork(String schemaType);
	void checkChunkTable(int instanceCount);
	void resetChunkTableTimes();
	
}
