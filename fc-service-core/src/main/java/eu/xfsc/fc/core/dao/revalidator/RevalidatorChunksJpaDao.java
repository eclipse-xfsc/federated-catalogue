package eu.xfsc.fc.core.dao.revalidator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RevalidatorChunksJpaDao implements RevalidatorChunksDao {

  private final RevalidatorChunkRepository repository;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public int findChunkForWork(String schemaType) {
    return repository.findChunkForWork(schemaType);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void checkChunkTable(int instanceCount) {
    repository.checkChunkTable(instanceCount);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void resetChunkTableTimes() {
    repository.resetChunkTableTimes();
  }
}
