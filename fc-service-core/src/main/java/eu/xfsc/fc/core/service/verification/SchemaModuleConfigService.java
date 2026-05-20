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

  /**
   * Error-body prefix returned by every gate that rejects a request because a schema module
   * is administratively disabled. Concatenated with a {@link SchemaModuleType} constant —
   * e.g. {@code module_disabled:SHACL}. The OpenAPI spec documents this exact prefix for
   * 400 responses on the validation and verification endpoints.
   */
  public static final String MODULE_DISABLED_PREFIX = "module_disabled:";

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
   *
   * <p>The eviction is intentionally coarse ({@code allEntries = true}): a write that
   * touches a single module key invalidates every cached module entry, not just the one
   * that changed. This is the right trade-off here because module writes are admin
   * operations (rare, well-defined latency) while module reads are on every credential
   * verification (the hot path). Per-key eviction would shave a sub-millisecond
   * re-read off the next request for the three unaffected modules, at the cost of a
   * subtle bug class where eviction key derivation drifts from the read key.</p>
   *
   * <p>If module writes ever become frequent enough that re-warming the cache four
   * times per write becomes measurable, narrow the eviction with
   * {@code key = "#moduleType"} on the write methods that pass the type through.</p>
   */
  @CacheEvict(value = CACHE_NAME, allEntries = true)
  public void evictCache() {
    // eviction only
  }
}
