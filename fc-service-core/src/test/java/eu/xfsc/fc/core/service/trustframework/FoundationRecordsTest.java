package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

class FoundationRecordsTest {

  // language=yaml
  private static final String FULL_YAML = """
      id: gaia-x-2511
      family: gaia-x
      namespace: https://w3id.org/gaia-x/
      validation_type: shacl
      roles:
        Participant:
          additional_roots:
            - https://w3id.org/gaia-x/LegalPerson
          types: []
        ServiceOffering:
          additional_roots:
            - https://w3id.org/gaia-x/DigitalServiceOffering
          types: []
      properties:
        version: "2511"
      """;

  // language=yaml
  private static final String MINIMAL_YAML = """
      id: minimal
      family: test
      namespace: https://example.org/
      validation_type: shacl
      """;

  @Test
  void resolvedRole_withNonEmptyFields_isResolved() {
    var role = new ResolvedRole("gaia-x-2511", "Participant");

    assertThat(role.isResolved()).isTrue();
  }

  @Test
  void resolvedRole_unknown_isNotResolved() {
    assertThat(ResolvedRole.UNKNOWN.isResolved()).isFalse();
  }

  @Test
  void resolvedRole_unknown_equalsNewEmptyInstance() {
    assertThat(ResolvedRole.UNKNOWN).isEqualTo(new ResolvedRole("", ""));
  }

  @Test
  void frameworkBundleConfig_fullYaml_allFieldsPopulated() throws Exception {
    var mapper = new YAMLMapper();

    var config = mapper.readValue(FULL_YAML, FrameworkBundleConfig.class);

    assertThat(config.id()).isEqualTo("gaia-x-2511");
    assertThat(config.family()).isEqualTo("gaia-x");
    assertThat(config.namespace()).isEqualTo("https://w3id.org/gaia-x/");
    assertThat(config.validationType()).isEqualTo(ValidationType.SHACL);
    assertThat(config.roles()).containsKeys("Participant", "ServiceOffering");
    assertThat(config.roles().get("Participant").additionalRoots())
        .containsExactly("https://w3id.org/gaia-x/LegalPerson");
    assertThat(config.roles().get("ServiceOffering").additionalRoots())
        .containsExactly("https://w3id.org/gaia-x/DigitalServiceOffering");
    assertThat(config.properties()).containsEntry("version", "2511");
  }

  @Test
  void frameworkBundleConfig_missingProperties_returnsEmptyMap() throws Exception {
    var mapper = new YAMLMapper();

    var config = mapper.readValue(MINIMAL_YAML, FrameworkBundleConfig.class);

    assertThat(config.properties()).isNotNull().isEmpty();
  }

  @Test
  void frameworkBundleConfig_missingRoles_returnsEmptyMap() throws Exception {
    var mapper = new YAMLMapper();

    var config = mapper.readValue(MINIMAL_YAML, FrameworkBundleConfig.class);

    assertThat(config.roles()).isNotNull().isEmpty();
  }

  @Test
  void roleConfig_missingLists_returnsEmptyLists() throws Exception {
    var mapper = new YAMLMapper();

    String yaml = """
        id: x
        family: x
        namespace: https://x/
        validation_type: shacl
        roles:
          TestRole: {}
        """;

    var config = mapper.readValue(yaml, FrameworkBundleConfig.class);
    var roleConfig = config.roles().get("TestRole");

    assertThat(roleConfig.additionalRoots()).isNotNull().isEmpty();
    assertThat(roleConfig.types()).isNotNull().isEmpty();
  }
}
