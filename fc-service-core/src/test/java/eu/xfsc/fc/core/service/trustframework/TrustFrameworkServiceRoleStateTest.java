package eu.xfsc.fc.core.service.trustframework;

import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_ROLE_PARTICIPANT;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_ROLE_SERVICE_OFFERING;
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
 * Integration tests for per-role enabled state.
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

  /** Unknown bundle — not present in the registry. */
  private static final String UNKNOWN_BUNDLE = "no-such-bundle";

  @Autowired
  private TrustFrameworkService service;

  @Autowired
  private TrustFrameworkRoleStateRepository roleStateRepo;

  /** Family ID as stored in the {@code trust_frameworks} table (the DB row key). */
  private static final String FAMILY_GAIA_X = "gaia-x";

  @AfterEach
  void cleanUp() {
    roleStateRepo.deleteAll();
    // Restore the family to the Liquibase-seeded default (disabled) so tests are order-independent.
    service.setEnabled(FAMILY_GAIA_X, false);
  }

  @Test
  void isRoleEnabled_noRowPersisted_returnsTrue() {
    // No explicit state row → absence means enabled by default
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT)).isTrue();
  }

  @Test
  void isRoleEnabled_afterSetFalse_returnsFalse() {
    // Role-toggle is only effective when the bundle family is enabled.
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, false);

    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT)).isFalse();
  }

  @Test
  void isRoleEnabled_afterSetTrue_returnsTrue() {
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, false);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, true);

    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT)).isTrue();
  }

  @Test
  void setRoleEnabled_oneRole_doesNotAffectOtherRole() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, false);

    // ServiceOffering was not touched — must still read as default true
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_SERVICE_OFFERING)).isTrue();
  }

  @Test
  void setRoleEnabled_calledTwice_upserts() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, false);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, true);

    // Exactly one row (upsert, not two inserts)
    assertThat(roleStateRepo.findByFrameworkId(BUNDLE_GAIA_X_2511)).hasSize(1);
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT)).isTrue();
  }

  @Test
  void setRoleEnabled_unknownBundle_throwsNotFoundException() {
    assertThatThrownBy(() -> service.setRoleEnabled(UNKNOWN_BUNDLE, TFW_ROLE_PARTICIPANT, false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void setRoleEnabled_unknownRole_throwsNotFoundException() {
    assertThatThrownBy(() -> service.setRoleEnabled(BUNDLE_GAIA_X_2511, "NoSuchRole", false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getRoleStates_noExplicitState_allRolesReturnedAsTrue() {
    Map<String, Boolean> states = service.getRoleStates(BUNDLE_GAIA_X_2511);

    // All roles from the registry bundle must appear, all defaulting to true
    assertThat(states).isNotEmpty();
    assertThat(states).containsKeys(TFW_ROLE_PARTICIPANT, TFW_ROLE_SERVICE_OFFERING);
    assertThat(states).allSatisfy((role, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void getRoleStates_withOneRoleDisabled_reflectsOverride() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, false);

    Map<String, Boolean> states = service.getRoleStates(BUNDLE_GAIA_X_2511);

    assertThat(states).containsEntry(TFW_ROLE_PARTICIPANT, false);
    assertThat(states).containsEntry(TFW_ROLE_SERVICE_OFFERING, true);
  }

  @Test
  void getRoleStates_unknownBundle_throwsNotFoundException() {
    assertThatThrownBy(() -> service.getRoleStates(UNKNOWN_BUNDLE))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void setRoleEnabled_persistsAcrossRepositoryRead() {
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT, false);

    // Verify the row via repository (one row, correct field values)
    var rows = roleStateRepo.findByFrameworkId(BUNDLE_GAIA_X_2511);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getFrameworkId()).isEqualTo(BUNDLE_GAIA_X_2511);
    assertThat(rows.get(0).getRoleName()).isEqualTo(TFW_ROLE_PARTICIPANT);
    assertThat(rows.get(0).isEnabled()).isFalse();
  }

  /**
   * When the family bundle is disabled AND the role is explicitly disabled,
   * {@code isRoleEnabled} must return {@code true}: the role-toggle layer is bypassed
   * because bundle-off already blocks all resolution through that bundle.
   * The caller (ClaimValidator) will receive a bundle-disabled rejection separately;
   * the role-toggle must not add a second rejection on top.
   */
  @Test
  void isRoleEnabled_bundleDisabled_roleExplicitlyDisabled_returnsTrueBypassingRoleCheck() {
    // DB seed has gaia-x enabled=false (Liquibase default) — no need to call setEnabled here
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_SERVICE_OFFERING, false);

    // Bundle-off dominates: role-toggle check is bypassed → returns true
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_SERVICE_OFFERING)).isTrue();
  }

  /**
   * When the family bundle is disabled and no role row exists (default-true),
   * {@code isRoleEnabled} must still return {@code true} (bundle-off dominates).
   */
  @Test
  void isRoleEnabled_bundleDisabled_noRoleRow_returnsTrueBypassingRoleCheck() {
    // DB seed has gaia-x enabled=false — bundle is off, no role row set
    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT)).isTrue();
  }

  /**
   * When the family bundle is enabled and the role is explicitly disabled,
   * {@code isRoleEnabled} must return {@code false} — normal role-toggle behavior.
   */
  @Test
  void isRoleEnabled_bundleEnabled_roleDisabled_returnsFalse() {
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_SERVICE_OFFERING, false);

    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_SERVICE_OFFERING)).isFalse();
  }

  /**
   * When the family bundle is enabled and no role row exists,
   * {@code isRoleEnabled} must return {@code true} — default-true semantics apply.
   */
  @Test
  void isRoleEnabled_bundleEnabled_noRoleRow_returnsTrue() {
    service.setEnabled(FAMILY_GAIA_X, true);

    assertThat(service.isRoleEnabled(BUNDLE_GAIA_X_2511, TFW_ROLE_PARTICIPANT)).isTrue();
  }

  /**
   * For a framework ID that is not known to the registry,
   * {@code isRoleEnabled} must return {@code true} — preserve existing caller semantics.
   */
  @Test
  void isRoleEnabled_unknownFrameworkId_returnsTrue() {
    assertThat(service.isRoleEnabled(UNKNOWN_BUNDLE, TFW_ROLE_PARTICIPANT)).isTrue();
  }
}
