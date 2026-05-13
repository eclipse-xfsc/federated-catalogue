package eu.xfsc.fc.core.service.trustframework;

import eu.xfsc.fc.core.dao.trustframework.TrustFramework;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkMapper;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public service for the trust framework bounded context. Mediates all access to the
 * persisted enabled/config state so callers in other contexts (verification, admin API)
 * do not inject the repository directly.
 */
@Service
@RequiredArgsConstructor
public class TrustFrameworkService {

  private final TrustFrameworkRepository trustFrameworkRepository;

  /**
   * Returns true if the trust framework identified by the given family ID has its enabled
   * flag set in persistence. Returns false when the family is not registered.
   */
  public boolean isEnabled(String family) {
    return trustFrameworkRepository.findById(family)
        .map(TrustFramework::isEnabled)
        .orElse(false);
  }

  /**
   * Sets the enabled flag for the trust framework identified by the given family ID.
   * Throws when no record exists for the family.
   */
  @Transactional
  public void setEnabled(String family, boolean enabled) {
    TrustFramework entity = trustFrameworkRepository.findById(family)
        .orElseThrow(() -> new NotFoundException("Trust framework not found: " + family));
    entity.setEnabled(enabled);
    trustFrameworkRepository.save(entity);
  }

  /**
   * Returns the configuration for the trust framework identified by the given family ID,
   * or empty when the family is not registered.
   */
  public Optional<TrustFrameworkConfig> findByFamily(String family) {
    return trustFrameworkRepository.findById(family)
        .map(TrustFrameworkMapper::toConfig);
  }

  /**
   * Returns the configuration for every registered trust framework.
   */
  public List<TrustFrameworkConfig> findAll() {
    return trustFrameworkRepository.findAll().stream()
        .map(TrustFrameworkMapper::toConfig)
        .toList();
  }

  /**
   * Replaces the service URL, API version, and timeout for the trust framework identified
   * by the given family ID. Returns the updated config, or empty when no record exists.
   */
  @Transactional
  public Optional<TrustFrameworkConfig> updateConfig(String family, String serviceUrl, String apiVersion,
                                                     int timeoutSeconds) {
    return trustFrameworkRepository.findById(family).map(entity -> {
      entity.setServiceUrl(serviceUrl);
      entity.setApiVersion(apiVersion);
      entity.setTimeoutSeconds(timeoutSeconds);
      trustFrameworkRepository.save(entity);
      return TrustFrameworkMapper.toConfig(entity);
    });
  }
}
