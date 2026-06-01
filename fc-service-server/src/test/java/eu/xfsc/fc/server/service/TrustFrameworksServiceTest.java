package eu.xfsc.fc.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import eu.xfsc.fc.api.generated.model.TrustFrameworkPublicEntry;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.trustframework.ValidationType;

/**
 * Unit tests for {@link TrustFrameworksService} — the public discovery endpoint
 * that lists enabled trust frameworks and their active profile IDs.
 */
@ExtendWith(MockitoExtension.class)
class TrustFrameworksServiceTest {

  @Mock
  private TrustFrameworkService trustFrameworkService;

  @Mock
  private TrustFrameworkRegistry registry;

  @InjectMocks
  private TrustFrameworksService service;

  @Test
  void getTrustFrameworksPublic_disabledFrameworkExcluded() {
    var enabled = tfConfig("gaia-x", "GAIA-X", true);
    var disabled = tfConfig("untp", "UNTP", false);
    when(trustFrameworkService.findAll()).thenReturn(List.of(enabled, disabled));
    when(registry.getActiveBundles()).thenReturn(List.of());

    var response = service.getTrustFrameworksPublic();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).extracting(TrustFrameworkPublicEntry::getId)
        .containsExactly("gaia-x")
        .doesNotContain("untp");
  }

  @Test
  void getTrustFrameworksPublic_profilesMatchedByFamilyId() {
    var tfCfg = tfConfig("gaia-x", "GAIA-X", true);
    when(trustFrameworkService.findAll()).thenReturn(List.of(tfCfg));
    var matchingBundle = bundle("gaia-x-2511", "gaia-x");
    var otherBundle = bundle("untp-v1", "untp");
    when(registry.getActiveBundles()).thenReturn(List.of(matchingBundle, otherBundle));

    var response = service.getTrustFrameworksPublic();

    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().getFirst().getProfiles())
        .containsExactly("gaia-x-2511")
        .doesNotContain("untp-v1");
  }

  @Test
  void getTrustFrameworksPublic_noActiveBundles_emptyProfilesList() {
    when(trustFrameworkService.findAll()).thenReturn(List.of(tfConfig("gaia-x", "GAIA-X", true)));
    when(registry.getActiveBundles()).thenReturn(List.of());

    var response = service.getTrustFrameworksPublic();

    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().getFirst().getProfiles()).isEmpty();
  }

  private static TrustFrameworkConfig tfConfig(String id, String name, boolean enabled) {
    return new TrustFrameworkConfig(id, name, enabled, null, null);
  }

  private static TrustFrameworkBundle bundle(String profileId, String familyId) {
    var config = new FrameworkBundleConfig(profileId, familyId, "https://example.org/",
        ValidationType.SHACL, Map.of(), Map.of());
    return new TrustFrameworkBundle(config, null, null);
  }
}
