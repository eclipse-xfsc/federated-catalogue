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
      base_classes:
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
    var role = new ResolvedBaseClass("gaia-x-2511", "Participant");

    assertThat(role.isResolved()).isTrue();
  }

  @Test
  void resolvedRole_unknown_isNotResolved() {
    assertThat(ResolvedBaseClass.UNKNOWN.isResolved()).isFalse();
  }

  @Test
  void resolvedRole_unknown_equalsNewEmptyInstance() {
    assertThat(ResolvedBaseClass.UNKNOWN).isEqualTo(new ResolvedBaseClass("", ""));
  }

  @Test
  void frameworkBundleConfig_fullYaml_allFieldsPopulated() throws Exception {
    var mapper = new YAMLMapper();

    var config = mapper.readValue(FULL_YAML, FrameworkBundleConfig.class);

    assertThat(config.id()).isEqualTo("gaia-x-2511");
    assertThat(config.family()).isEqualTo("gaia-x");
    assertThat(config.namespace()).isEqualTo("https://w3id.org/gaia-x/");
    assertThat(config.validationType()).isEqualTo(ValidationType.SHACL);
    assertThat(config.baseClasses()).containsKeys("Participant", "ServiceOffering");
    assertThat(config.baseClasses().get("Participant").additionalRoots())
        .containsExactly("https://w3id.org/gaia-x/LegalPerson");
    assertThat(config.baseClasses().get("ServiceOffering").additionalRoots())
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

    assertThat(config.baseClasses()).isNotNull().isEmpty();
  }

  @Test
  void roleConfig_missingLists_returnsEmptyLists() throws Exception {
    var mapper = new YAMLMapper();

    String yaml = """
        id: x
        family: x
        namespace: https://x/
        validation_type: shacl
        base_classes:
          TestBaseClass: {}
        """;

    var config = mapper.readValue(yaml, FrameworkBundleConfig.class);
    var baseClassConfig = config.baseClasses().get("TestBaseClass");

    assertThat(baseClassConfig.additionalRoots()).isNotNull().isEmpty();
    assertThat(baseClassConfig.types()).isNotNull().isEmpty();
  }
}
