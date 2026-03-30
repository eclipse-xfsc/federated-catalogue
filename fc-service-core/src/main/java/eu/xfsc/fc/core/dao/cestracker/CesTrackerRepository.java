package eu.xfsc.fc.core.dao.cestracker;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CesTrackerRepository
    extends JpaRepository<CesTracker, String> {

  Optional<CesTracker> findFirstByOrderByCreatedAtDesc();
}
