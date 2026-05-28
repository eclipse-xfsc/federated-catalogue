package eu.xfsc.fc.core.dao.trustframework;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for per-bundle external-identifier overrides. Absence of a row means
 * "no override — fall back to the bundle's {@code framework.yaml} values". A present
 * row with a NULL field also falls back for that single field.
 */
public interface TrustFrameworkBundleConfigRepository
    extends JpaRepository<TrustFrameworkBundleConfig, String> {
}
