package eu.xfsc.fc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.FcMediaTypes;
import eu.xfsc.fc.api.generated.model.SchemaModulePatch;
import eu.xfsc.fc.api.generated.model.TrustFrameworkPatch;
import eu.xfsc.fc.api.generated.model.TrustFrameworkRolePatch;

/**
 * Regression test for the portal WebClient codecs. The admin-API endpoints use the
 * {@code application/merge-patch+json} media type (RFC 7396). The default
 * {@link org.springframework.http.codec.json.Jackson2JsonEncoder} accepts only
 * {@code application/json}, so issuing a PATCH with a merge-patch content-type fails with
 * {@code UnsupportedMediaTypeException} at body serialisation before the request is
 * dispatched. These tests pin {@link ClientConfig#configureFcCodecs} so a future refactor
 * cannot regress the admin portal's PATCH proxy routes.
 *
 * <p>The check is performed directly against the configured codec writers (not through a
 * stubbed {@code WebClient}, which short-circuits before body serialisation runs and so
 * would silently pass even with the wrong codec list).
 */
class ClientConfigCodecsTest {

  @Test
  void configuredEncoder_writesTrustFrameworkPatch_underMergePatchJson() {
    assertWritable(TrustFrameworkPatch.class);
  }

  @Test
  void configuredEncoder_writesTrustFrameworkRolePatch_underMergePatchJson() {
    assertWritable(TrustFrameworkRolePatch.class);
  }

  @Test
  void configuredEncoder_writesSchemaModulePatch_underMergePatchJson() {
    assertWritable(SchemaModulePatch.class);
  }

  @Test
  void configuredEncoder_stillWritesUnderApplicationJson() {
    ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
    ClientConfig.configureFcCodecs(configurer, new ObjectMapper());

    boolean hasJsonWriter = configurer.getWriters().stream().anyMatch(writer ->
        writer.canWrite(ResolvableType.forClass(TrustFrameworkPatch.class),
            MediaType.APPLICATION_JSON));

    assertThat(hasJsonWriter)
        .as("application/json must remain writable after enabling merge-patch+json")
        .isTrue();
  }

  private static void assertWritable(Class<?> bodyType) {
    ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
    ClientConfig.configureFcCodecs(configurer, new ObjectMapper());

    boolean hasMergePatchWriter = configurer.getWriters().stream().anyMatch(writer ->
        writer.canWrite(ResolvableType.forClass(bodyType), FcMediaTypes.MERGE_PATCH_JSON));

    assertThat(hasMergePatchWriter)
        .as("portal WebClient must have a JSON writer accepting "
            + FcMediaTypes.MERGE_PATCH_JSON_VALUE + " for " + bodyType.getSimpleName())
        .isTrue();
  }
}
