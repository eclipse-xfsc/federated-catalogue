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
 * Persists the enabled/disabled state of a single base class within a trust framework bundle.
 *
 * <p>Absence of a row for a given {@code (frameworkId, baseClassName)} pair means the base class is
 * enabled by default. Only rows with {@code enabled = false} (or explicit {@code true} overrides
 * written by the service) are stored here.
 *
 * <p>{@code frameworkId} is the registry bundle profile ID (e.g. {@code gaia-x-2511}).
 * {@code baseClassName} is the base-class name declared in the bundle's {@code framework.yaml}
 * (e.g. {@code Participant}).
 */
@Entity
@Table(name = "trust_framework_base_class_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrustFrameworkBaseClassState {

  @EmbeddedId
  private TrustFrameworkBaseClassStateId id;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  /**
   * Convenience constructor.
   *
   * @param frameworkId   registry bundle profile ID
   * @param baseClassName base-class name from the bundle's base_classes map
   * @param enabled       whether this base class is enabled
   */
  public TrustFrameworkBaseClassState(String frameworkId, String baseClassName, boolean enabled) {
    this.id = new TrustFrameworkBaseClassStateId(frameworkId, baseClassName);
    this.enabled = enabled;
  }

  /**
   * Returns the bundle profile ID component of the composite key.
   */
  public String getFrameworkId() {
    return id.getFrameworkId();
  }

  /**
   * Returns the base-class name component of the composite key.
   */
  public String getBaseClassName() {
    return id.getBaseClassName();
  }
}
