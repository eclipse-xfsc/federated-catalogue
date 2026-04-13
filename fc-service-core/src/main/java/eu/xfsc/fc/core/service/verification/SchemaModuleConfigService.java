package eu.xfsc.fc.core.service.verification;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import lombok.RequiredArgsConstructor;

/**
 * Reads and caches schema module enabled/disabled state from admin config.
 *
 * <p>This service is on a hot path (called on every credential verification).
 * Results are cached to avoid a DB read per request. Cache is evicted on write
 * via {@link #evictCache()}.</p>
 */
@Service
@RequiredArgsConstructor
public class SchemaModuleConfigService {

  public static final String CACHE_NAME = "schemaModuleConfig";
  private static final String CONFIG_PREFIX = "schema.module.";
  private static final String CONFIG_SUFFIX = ".enabled";

  private final AdminConfigRepository adminConfigRepository;

  /**
   * Returns true if the given schema validation module is enabled.
   * Defaults to {@code true} if no config entry exists.
   *
   * @param moduleType one of the {@link SchemaModuleType} constants.
   * @return true if the module is enabled.
   */
  @Cacheable(value = CACHE_NAME, key = "#moduleType")
  public boolean isModuleEnabled(String moduleType) {
    return adminConfigRepository.getValue(CONFIG_PREFIX + moduleType + CONFIG_SUFFIX)
        .map(Boolean::parseBoolean)
        .orElse(true);
  }

  /**
   * Evicts all cached module state. Call after any schema module config write.
   */
  @CacheEvict(value = CACHE_NAME, allEntries = true)
  public void evictCache() {
    // eviction only
  }
}
