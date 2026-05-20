package eu.xfsc.fc.core.dao.trustframework;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persists the enabled/disabled state of a single role within a trust framework bundle.
 *
 * <p>Absence of a row for a given {@code (frameworkId, roleName)} pair means the role is enabled
 * by default. Only rows with {@code enabled = false} (or explicit {@code true} overrides written
 * by the service) are stored here.
 *
 * <p>{@code frameworkId} is the registry bundle profile ID (e.g. {@code gaia-x-2511}).
 * {@code roleName} is the role name declared in the bundle's {@code framework.yaml}
 * (e.g. {@code Participant}).
 */
@Entity
@Table(name = "trust_framework_role_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrustFrameworkRoleState {

  @EmbeddedId
  private TrustFrameworkRoleStateId id;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  /**
   * Convenience constructor.
   *
   * @param frameworkId registry bundle profile ID
   * @param roleName    role name from the bundle's roles map
   * @param enabled     whether this role is enabled
   */
  public TrustFrameworkRoleState(String frameworkId, String roleName, boolean enabled) {
    this.id = new TrustFrameworkRoleStateId(frameworkId, roleName);
    this.enabled = enabled;
  }

  /**
   * Returns the bundle profile ID component of the composite key.
   */
  public String getFrameworkId() {
    return id.getFrameworkId();
  }

  /**
   * Returns the role name component of the composite key.
   */
  public String getRoleName() {
    return id.getRoleName();
  }
}
