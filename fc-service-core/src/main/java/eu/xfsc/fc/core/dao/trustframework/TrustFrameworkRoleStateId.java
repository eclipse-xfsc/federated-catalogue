package eu.xfsc.fc.core.dao.trustframework;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link TrustFrameworkRoleState}.
 *
 * <p>{@code frameworkId} is the registry bundle profile ID (e.g. {@code gaia-x-2511});
 * {@code roleName} is the role name as declared in the bundle's {@code framework.yaml}
 * (e.g. {@code Participant}).
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TrustFrameworkRoleStateId implements Serializable {

  @Column(name = "framework_id", length = 255, nullable = false)
  private String frameworkId;

  @Column(name = "role_name", length = 255, nullable = false)
  private String roleName;
}
