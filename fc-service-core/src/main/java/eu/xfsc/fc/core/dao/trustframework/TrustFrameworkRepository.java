package eu.xfsc.fc.core.dao.trustframework;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustFrameworkRepository
    extends JpaRepository<TrustFramework, String> {

  long countByEnabledTrue();
}
