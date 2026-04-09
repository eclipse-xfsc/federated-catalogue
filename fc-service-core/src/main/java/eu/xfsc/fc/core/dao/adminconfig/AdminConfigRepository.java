package eu.xfsc.fc.core.dao.adminconfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminConfigRepository
    extends JpaRepository<AdminConfigEntry, String> {

  List<AdminConfigEntry> findByConfigKeyStartingWith(String prefix);

  /**
   * Get a config value by key.
   *
   * @param key config key.
   * @return the value, or empty if not found.
   */
  default Optional<String> getValue(String key) {
    return findById(key).map(AdminConfigEntry::getConfigValue);
  }

  /**
   * Get all config entries whose key starts with the given prefix.
   * The prefix must not contain SQL wildcards (%, _).
   *
   * @param prefix key prefix (e.g. "schema.module.").
   * @return map of key → value.
   */
  default Map<String, String> getByPrefix(String prefix) {
    return findByConfigKeyStartingWith(prefix).stream()
        .collect(Collectors.toMap(
            AdminConfigEntry::getConfigKey,
            AdminConfigEntry::getConfigValue));
  }
}
