package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.TrustFrameworkRegistryConfig;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRoleStateRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Integration tests for per-role enabled state (AC-1).
 *
 * <p>Tests verify behavior through the public service interface only. The embedded Zonky
 * Postgres database runs the full Liquibase migration, so persisted state survives across
 * method calls within a test and is cleaned up in {@link #cleanUp()}.
 *
 * <p>{@code frameworkId} values here are registry profile IDs (e.g. {@code gaia-x-2511}),
 * NOT family IDs — role state is keyed by the bundle's profile ID because roles are
 * declared per bundle, and validation is performed against the registry.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    TrustFrameworkServiceRoleStateTest.TestConfig.class,
    DatabaseConfig.class,
    SecurityAuditorAware.class,
    TrustFrameworkRegistryConfig.class,
    TrustFrameworkService.class,
})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class TrustFrameworkServiceRoleStateTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  /**
   * Registry profile ID of the built-in Gaia-X 2511 bundle.
   * Roles in this bundle: Participant, ServiceOffering, Resource.
   */
  private static final String BUNDLE_GAIA_X_2511 = "gaia-x-2511";

  /** Roles known to be declared in the gaia-x-2511 bundle (from framework.yaml). */
  private static final String ROLE_PARTICIPANT = "Participant";
  private static final String ROLE_SERVICE_OFFERING = "ServiceOffering";

  /** Unknown bundle — not present in the registry. */
  private static final String UNKNOWN_BUNDLE = "no-such-bundle";

  @Autowired
  private TrustFrameworkService service;

  @Autowired
  private TrustFrameworkRoleStateRepository roleStateRepo;

  @AfterEach
  void cleanUp() {
    roleStateRepo.deleteAll();
  }

  // ===== isRoleEnabled — default-true semantics =====

  @Test
  void isRoleEnabled_noRowPersisted_returnsTrue() {
    // No explicit state row → absence means enabled by default
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT)).isTrue();
  }

  @Test
  void isRoleEnabled_afterSetFalse_returnsFalse() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, false);

    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT)).isFalse();
  }

  @Test
  void isRoleEnabled_afterSetTrue_returnsTrue() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, false);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, true);

    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT)).isTrue();
  }

  // ===== setRoleEnabled — isolation between roles =====

  @Test
  void setRoleEnabled_oneRole_doesNotAffectOtherRole() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, false);

    // ServiceOffering was not touched — must still read as default true
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_SERVICE_OFFERING)).isTrue();
  }

  // ===== setRoleEnabled — upsert semantics =====

  @Test
  void setRoleEnabled_calledTwice_upserts() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, false);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, true);

    // Exactly one row (upsert, not two inserts)
    assertThat(roleStateRepo.findByFrameworkId(BUNDLE_GAIA_X_2511)).hasSize(1);
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT)).isTrue();
  }

  // ===== setRoleEnabled — NotFoundException for unknown bundle =====

  @Test
  void setRoleEnabled_unknownBundle_throwsNotFoundException() {
    assertThatThrownBy(() -> service.setRoleEnabled(UNKNOWN_BUNDLE, ROLE_PARTICIPANT, false))
        .isInstanceOf(NotFoundException.class);
  }

  // ===== setRoleEnabled — NotFoundException for unknown role =====

  @Test
  void setRoleEnabled_unknownRole_throwsNotFoundException() {
    assertThatThrownBy(() -> service.setRoleEnabled(BUNDLE_GAIA_X_2511, "NoSuchRole", false))
        .isInstanceOf(NotFoundException.class);
  }

  // ===== getRoleStates — full map with defaults =====

  @Test
  void getRoleStates_noExplicitState_allRolesReturnedAsTrue() {
    Map<String, Boolean> states = service.getRoleStates(BUNDLE_GAIA_X_2511);

    // All roles from the registry bundle must appear, all defaulting to true
    assertThat(states).isNotEmpty();
    assertThat(states).containsKeys(ROLE_PARTICIPANT, ROLE_SERVICE_OFFERING);
    assertThat(states).allSatisfy((role, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void getRoleStates_withOneRoleDisabled_reflectsOverride() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, false);

    Map<String, Boolean> states = service.getRoleStates(BUNDLE_GAIA_X_2511);

    assertThat(states).containsEntry(ROLE_PARTICIPANT, false);
    assertThat(states).containsEntry(ROLE_SERVICE_OFFERING, true);
  }

  // ===== getRoleStates — NotFoundException for unknown bundle =====

  @Test
  void getRoleStates_unknownBundle_throwsNotFoundException() {
    assertThatThrownBy(() -> service.getRoleStates(UNKNOWN_BUNDLE))
        .isInstanceOf(NotFoundException.class);
  }

  // ===== persistence round-trip =====

  @Test
  void setRoleEnabled_persistsAcrossRepositoryRead() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, ROLE_PARTICIPANT, false);

    // Verify the row via repository (one row, correct field values)
    var rows = roleStateRepo.findByFrameworkId(BUNDLE_GAIA_X_2511);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getFrameworkId()).isEqualTo(BUNDLE_GAIA_X_2511);
    assertThat(rows.get(0).getRoleName()).isEqualTo(ROLE_PARTICIPANT);
    assertThat(rows.get(0).isEnabled()).isFalse();
  }
}
