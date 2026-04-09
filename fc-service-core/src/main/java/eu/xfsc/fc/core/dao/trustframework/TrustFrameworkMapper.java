package eu.xfsc.fc.core.dao.trustframework;

import java.time.ZoneOffset;

import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

public final class TrustFrameworkMapper {

  private TrustFrameworkMapper() {
  }

  public static TrustFrameworkConfig toConfig(TrustFramework entity) {
    if (entity == null) {
      return null;
    }
    return new TrustFrameworkConfig(
        entity.getId(),
        entity.getName(),
        entity.getServiceUrl(),
        entity.getApiVersion(),
        entity.getTimeoutSeconds(),
        entity.isEnabled(),
        entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
        entity.getUpdatedAt() != null ? entity.getUpdatedAt().toInstant(ZoneOffset.UTC) : null
    );
  }
}
