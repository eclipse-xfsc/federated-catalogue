package eu.xfsc.fc.api;

import org.springframework.http.MediaType;

/**
 * Wire-format media types specific to the Federated Catalogue API.
 *
 * <p>Mirrors Spring's pattern of exposing both the {@link MediaType} instance and its
 * {@link String} value, so callers can pick the form their API needs (annotations and
 * MockMvc {@code .contentType(String)} use the string; WebClient and similar use the
 * {@code MediaType}).
 */
public final class FcMediaTypes {

  /**
   * Merge-patch content type per RFC 7396.
   */
  public static final String MERGE_PATCH_JSON_VALUE = "application/merge-patch+json";

  /**
   * {@link MediaType} form of {@link #MERGE_PATCH_JSON_VALUE}.
   */
  public static final MediaType MERGE_PATCH_JSON = MediaType.valueOf(MERGE_PATCH_JSON_VALUE);

  private FcMediaTypes() {
  }
}
