package eu.xfsc.fc.core.dao;

public interface RevalidatorChunksDao {

	int findChunkForWork(int schemaType);
	void checkChunkTable(int instanceCount);
	void resetChunkTableTimes();
	
}
