package eu.xfsc.fc.core.service.trustframework;

import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_PARTICIPANT;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_RESOURCE;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_SERVICE_OFFERING;
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
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBaseClassStateRepository;
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
    TrustFrameworkServiceBaseClassStateTest.TestConfig.class,
    DatabaseConfig.class,
    SecurityAuditorAware.class,
    TrustFrameworkRegistryConfig.class,
    TrustFrameworkService.class,
})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class TrustFrameworkServiceBaseClassStateTest {

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
  private TrustFrameworkBaseClassStateRepository baseClassStateRepo;

  /** Family ID as stored in the {@code trust_frameworks} table (the DB row key). */
  private static final String FAMILY_GAIA_X = "gaia-x";

  @AfterEach
  void cleanUp() {
    baseClassStateRepo.deleteAll();
    // Restore the family to the Liquibase-seeded default (disabled) so tests are order-independent.
    service.setEnabled(FAMILY_GAIA_X, false);
  }

  @Test
  void isBaseClassEnabled_noRowPersisted_returnsTrue() {
    // Family must be enabled, otherwise bundle-off dominance short-circuits to true
    // and the role-state lookup path is never exercised.
    service.setEnabled(FAMILY_GAIA_X, true);

    // No explicit state row → absence means enabled by default
    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT)).isTrue();
  }

  @Test
  void isBaseClassEnabled_afterSetFalse_returnsFalse() {
    // Role-toggle is only effective when the bundle family is enabled.
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, false);

    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT)).isFalse();
  }

  @Test
  void isBaseClassEnabled_afterSetTrue_returnsTrue() {
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, false);
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, true);

    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT)).isTrue();
  }

  @Test
  void setBaseClassEnabled_oneRole_doesNotAffectOtherRole() {
    // Family must be enabled, otherwise bundle-off dominance short-circuits to true
    // and the role-state lookup path is never exercised.
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, false);

    // ServiceOffering was not touched — must still read as default true
    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_SERVICE_OFFERING)).isTrue();
  }

  @Test
  void setBaseClassEnabled_calledTwice_upserts() {
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, false);
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, true);

    // Exactly one row (upsert, not two inserts)
    assertThat(baseClassStateRepo.findByFrameworkId(BUNDLE_GAIA_X_2511)).hasSize(1);
    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT)).isTrue();
  }

  @Test
  void setBaseClassEnabled_unknownBundle_throwsNotFoundException() {
    assertThatThrownBy(() -> service.setBaseClassEnabled(UNKNOWN_BUNDLE, TFW_BASE_CLASS_PARTICIPANT, false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void setBaseClassEnabled_unknownRole_throwsNotFoundException() {
    assertThatThrownBy(() -> service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, "NoSuchRole", false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getBaseClassStates_noExplicitState_allRolesReturnedAsTrue() {
    Map<String, Boolean> states = service.getBaseClassStates(BUNDLE_GAIA_X_2511);

    // All roles from the registry bundle must appear, all defaulting to true
    assertThat(states).isNotEmpty();
    assertThat(states).containsKeys(TFW_BASE_CLASS_PARTICIPANT, TFW_BASE_CLASS_SERVICE_OFFERING);
    assertThat(states).allSatisfy((role, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void getBaseClassStates_withOneRoleDisabled_reflectsOverride() {
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, false);

    Map<String, Boolean> states = service.getBaseClassStates(BUNDLE_GAIA_X_2511);

    assertThat(states).containsEntry(TFW_BASE_CLASS_PARTICIPANT, false);
    assertThat(states).containsEntry(TFW_BASE_CLASS_SERVICE_OFFERING, true);
  }

  @Test
  void getBaseClassStates_preservesYamlDeclarationOrder() {
    // gaia-x-2511 framework.yaml declares roles in the order:
    //   Participant, ServiceOffering, Resource
    // The returned LinkedHashMap must iterate in that exact order so the UI can render
    // role-toggle rows deterministically.
    Map<String, Boolean> states = service.getBaseClassStates(BUNDLE_GAIA_X_2511);

    assertThat(states).containsExactly(
        Map.entry(TFW_BASE_CLASS_PARTICIPANT, true),
        Map.entry(TFW_BASE_CLASS_SERVICE_OFFERING, true),
        Map.entry(TFW_BASE_CLASS_RESOURCE, true));
  }

  @Test
  void getBaseClassStates_unknownBundle_throwsNotFoundException() {
    assertThatThrownBy(() -> service.getBaseClassStates(UNKNOWN_BUNDLE))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void setBaseClassEnabled_persistsAcrossRepositoryRead() {
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT, false);

    // Verify the row via repository (one row, correct field values)
    var rows = baseClassStateRepo.findByFrameworkId(BUNDLE_GAIA_X_2511);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getFrameworkId()).isEqualTo(BUNDLE_GAIA_X_2511);
    assertThat(rows.get(0).getBaseClassName()).isEqualTo(TFW_BASE_CLASS_PARTICIPANT);
    assertThat(rows.get(0).isEnabled()).isFalse();
  }

  /**
   * When the family bundle is disabled AND the role is explicitly disabled,
   * {@code isBaseClassEnabled} must return {@code true}: the role-toggle layer is bypassed
   * because bundle-off already blocks all resolution through that bundle.
   * The caller (ClaimValidator) will receive a bundle-disabled rejection separately;
   * the role-toggle must not add a second rejection on top.
   */
  @Test
  void isBaseClassEnabled_bundleDisabled_roleExplicitlyDisabled_returnsTrueBypassingRoleCheck() {
    // DB seed has gaia-x enabled=false (Liquibase default) — no need to call setEnabled here
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_SERVICE_OFFERING, false);

    // Bundle-off dominates: role-toggle check is bypassed → returns true
    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_SERVICE_OFFERING)).isTrue();
  }

  /**
   * When the family bundle is disabled and no role row exists (default-true),
   * {@code isBaseClassEnabled} must still return {@code true} (bundle-off dominates).
   */
  @Test
  void isBaseClassEnabled_bundleDisabled_noRoleRow_returnsTrueBypassingRoleCheck() {
    // DB seed has gaia-x enabled=false — bundle is off, no role row set
    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT)).isTrue();
  }

  /**
   * When the family bundle is enabled and the role is explicitly disabled,
   * {@code isBaseClassEnabled} must return {@code false} — normal role-toggle behavior.
   */
  @Test
  void isBaseClassEnabled_bundleEnabled_roleDisabled_returnsFalse() {
    service.setEnabled(FAMILY_GAIA_X, true);
    service.setBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_SERVICE_OFFERING, false);

    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_SERVICE_OFFERING)).isFalse();
  }

  /**
   * When the family bundle is enabled and no role row exists,
   * {@code isBaseClassEnabled} must return {@code true} — default-true semantics apply.
   */
  @Test
  void isBaseClassEnabled_bundleEnabled_noRoleRow_returnsTrue() {
    service.setEnabled(FAMILY_GAIA_X, true);

    assertThat(service.isBaseClassEnabled(BUNDLE_GAIA_X_2511, TFW_BASE_CLASS_PARTICIPANT)).isTrue();
  }

  /**
   * For a framework ID that is not known to the registry,
   * {@code isBaseClassEnabled} must return {@code true} — preserve existing caller semantics.
   */
  @Test
  void isBaseClassEnabled_unknownFrameworkId_returnsTrue() {
    assertThat(service.isBaseClassEnabled(UNKNOWN_BUNDLE, TFW_BASE_CLASS_PARTICIPANT)).isTrue();
  }
}
