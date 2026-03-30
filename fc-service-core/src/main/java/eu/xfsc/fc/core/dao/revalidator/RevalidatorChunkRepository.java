package eu.xfsc.fc.core.dao.revalidator;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevalidatorChunkRepository extends JpaRepository<RevalidatorChunk, Integer>,
            RevalidatorChunkRepositoryCustom {
}
