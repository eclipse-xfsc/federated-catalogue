package eu.xfsc.fc.core.dao.trustframework;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for per-base-class enabled state of trust framework bundles.
 *
 * <p>Absence of a row means the base class is enabled by default (interpreted in the service layer).
 * This repository stores only rows that have been explicitly written via
 * {@link eu.xfsc.fc.core.service.trustframework.TrustFrameworkService#setBaseClassEnabled}.
 */
public interface TrustFrameworkBaseClassStateRepository
    extends JpaRepository<TrustFrameworkBaseClassState, TrustFrameworkBaseClassStateId> {

  /**
   * Finds the persisted state row for a specific bundle profile ID and base-class name.
   * Returns empty when no explicit state has been written (default = enabled).
   *
   * @param frameworkId   registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param baseClassName base-class name from the bundle (e.g. {@code Participant})
   * @return the state row, or empty if absent
   */
  Optional<TrustFrameworkBaseClassState> findByIdFrameworkIdAndIdBaseClassName(
      String frameworkId, String baseClassName);

  /**
   * Returns all explicitly persisted state rows for the given bundle profile ID.
   * Base classes absent from this list are enabled by default.
   *
   * @param frameworkId registry bundle profile ID
   * @return list of state rows; never null
   */
  List<TrustFrameworkBaseClassState> findByIdFrameworkId(String frameworkId);

  /**
   * Convenience alias for {@link #findByIdFrameworkId(String)}.
   */
  default List<TrustFrameworkBaseClassState> findByFrameworkId(String frameworkId) {
    return findByIdFrameworkId(frameworkId);
  }

  /**
   * Convenience alias for looking up a single base-class state by bundle profile ID and base-class name.
   */
  default Optional<TrustFrameworkBaseClassState> findByFrameworkIdAndBaseClassName(
      String frameworkId, String baseClassName) {
    return findByIdFrameworkIdAndIdBaseClassName(frameworkId, baseClassName);
  }
}
