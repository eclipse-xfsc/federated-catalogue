package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtContentPreprocessorTest {

  private static final String JWT_BODY = "eyJhbGciOiJFZERTQSJ9.payload.sig";
  private static final String JSON_LD_BODY = "{\"@context\": [\"https://www.w3.org/ns/credentials/v2\"]}";

  private final JwtContentPreprocessor preprocessor = new JwtContentPreprocessor();

  @Test
  void isJwtWrapped_vcJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+ld+json+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vpJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vp+ld+json+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_w3cVcJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_w3cVpJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vp+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithCharsetWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+ld+json+jwt; charset=utf-8");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeUppercaseWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "Application/VC+LD+JSON+JWT");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithNonJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JSON_LD_BODY, "application/vc+ld+json+jwt");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithEmptyBody_throwsClientException() {
    var content = new ContentAccessorDirect("", "application/vc+ld+json+jwt");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcLdJsonContentTypeWithJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+ld+json");

    ClientException ex = assertThrows(ClientException.class,
        () -> preprocessor.isJwtWrapped(content));

    assertTrue(ex.getMessage().contains("application/vc+jwt"), ex.getMessage());
  }

  @Test
  void isJwtWrapped_vpLdJsonContentTypeWithJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vp+ld+json");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_ldJsonContentTypeWithJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/ld+json");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_jsonContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/json");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_nullContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY);

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_malformedContentTypeWithJwtBody_returnsTrue() {
    // A header starting with ';' produces an empty type after split — falls through to body sniff
    var content = new ContentAccessorDirect(JWT_BODY, "; charset=utf-8");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

}
