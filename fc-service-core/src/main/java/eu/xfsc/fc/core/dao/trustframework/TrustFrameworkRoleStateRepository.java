package eu.xfsc.fc.core.dao.trustframework;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for per-role enabled state of trust framework bundles.
 *
 * <p>Absence of a row means the role is enabled by default (interpreted in the service layer).
 * This repository stores only rows that have been explicitly written via
 * {@link eu.xfsc.fc.core.service.trustframework.TrustFrameworkService#setRoleEnabled}.
 */
public interface TrustFrameworkRoleStateRepository
    extends JpaRepository<TrustFrameworkRoleState, TrustFrameworkRoleStateId> {

  /**
   * Finds the persisted state row for a specific bundle profile ID and role name.
   * Returns empty when no explicit state has been written (default = enabled).
   *
   * @param frameworkId registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param roleName    role name from the bundle (e.g. {@code Participant})
   * @return the state row, or empty if absent
   */
  Optional<TrustFrameworkRoleState> findByIdFrameworkIdAndIdRoleName(
      String frameworkId, String roleName);

  /**
   * Returns all explicitly persisted state rows for the given bundle profile ID.
   * Roles absent from this list are enabled by default.
   *
   * @param frameworkId registry bundle profile ID
   * @return list of state rows; never null
   */
  List<TrustFrameworkRoleState> findByIdFrameworkId(String frameworkId);

  /**
   * Convenience alias for {@link #findByIdFrameworkId(String)}.
   */
  default List<TrustFrameworkRoleState> findByFrameworkId(String frameworkId) {
    return findByIdFrameworkId(frameworkId);
  }

  /**
   * Convenience alias for looking up a single role state by bundle profile ID and role name.
   */
  default Optional<TrustFrameworkRoleState> findByFrameworkIdAndRoleName(
      String frameworkId, String roleName) {
    return findByIdFrameworkIdAndIdRoleName(frameworkId, roleName);
  }
}
