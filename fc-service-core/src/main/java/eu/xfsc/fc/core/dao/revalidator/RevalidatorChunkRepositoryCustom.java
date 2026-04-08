package eu.xfsc.fc.core.dao.revalidator;

interface RevalidatorChunkRepositoryCustom {

  int findChunkForWork(String schemaType);

  void checkChunkTable(int instanceCount);

  void resetChunkTableTimes();
}
