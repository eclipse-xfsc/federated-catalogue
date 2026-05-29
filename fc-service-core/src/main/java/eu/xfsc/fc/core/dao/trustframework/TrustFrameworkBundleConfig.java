package eu.xfsc.fc.core.dao.trustframework;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Per-bundle runtime overrides for external client identifiers (compliance service URL,
 * API version, timeout, client implementation key, trust-anchor list URL). Each non-null
 * field overrides the corresponding value from the bundle's {@code framework.yaml};
 * null fields fall back to the YAML value.
 *
 * <p>Absence of a row for a given bundle means "use YAML for every field". Rows for
 * bundle IDs that are no longer registered are ignored at runtime with a warning.
 */
@Entity
@Table(name = "trust_framework_bundle_config")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustFrameworkBundleConfig {

  @Id
  @Column(name = "bundle_id", length = 255, nullable = false)
  private String bundleId;

  @Column(name = "client_type", length = 255)
  private String clientType;

  @Column(name = "service_url", length = 1024)
  private String serviceUrl;

  @Column(name = "compliance_path", length = 1024)
  private String compliancePath;

  @Column(name = "api_version", length = 64)
  private String apiVersion;

  @Column(name = "timeout_seconds")
  private Integer timeoutSeconds;

  @Column(name = "trust_anchor_url", length = 1024)
  private String trustAnchorUrl;

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private ZonedDateTime updatedAt;
}
