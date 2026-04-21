package eu.xfsc.fc.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;
import eu.xfsc.fc.core.pojo.AssetMetadata;

public class TestUtil {

  public static ContentAccessor getAccessor(String path) {
    return getAccessor(TestUtil.class, path);
  }

  public static ContentAccessor getAccessor(Class<?> testClass, String fileName) {
    URL url = testClass.getClassLoader().getResource(fileName);
    String str = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
    return new ContentAccessorFile(new File(str));
  }

  // Since AssetRecord class extends AssetMetadata class instead of being formed from it, then check
  // in the equals method will always be false. Because we are downcasting AssetRecord to AssetMetadata.
  public static void assertThatAssetHasTheSameData(final AssetMetadata expected, final AssetMetadata actual, final boolean precise) {
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getAssetHash(), actual.getAssetHash());
    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getIssuer(), actual.getIssuer());
    assertEquals(expected.getValidatorDids(), actual.getValidatorDids());
    assertEquals(expected.getContentAccessor().getContentAsString(), actual.getContentAccessor().getContentAsString());
    if (precise) {
      assertEquals(expected.getUploadDatetime(), actual.getUploadDatetime());
      assertEquals(expected.getStatusDatetime(), actual.getStatusDatetime());
    } else {
      assertEquals(expected.getUploadDatetime().truncatedTo(ChronoUnit.MILLIS), actual.getUploadDatetime().truncatedTo(ChronoUnit.MILLIS));
      assertEquals(expected.getStatusDatetime().truncatedTo(ChronoUnit.MILLIS), actual.getStatusDatetime().truncatedTo(ChronoUnit.MILLIS));
    }
  }

    /**
     * Builds a fake Loire JWT (typ=vc+jwt, top-level @context, no vc wrapper).
     * The JwtSignatureVerifier is mocked so the signature is not verified.
     */
    public static String fakeLoireJwt(String iss) {
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"vc+jwt\",\"cty\":\"vc\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = """
        {"iss":"%s","@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiableCredential"],\
        "issuer":"%s",\
        "credentialSubject":{"id":"%s"}}""".formatted(iss, iss, iss);
        String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".AAAA";
    }
  
}
