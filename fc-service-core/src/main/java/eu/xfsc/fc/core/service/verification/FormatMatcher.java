package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Strategy interface for credential format detection.
 *
 * <p>Implementations are Spring beans collected by {@link FormatDetector}. Each matcher
 * inspects a {@link DetectionContext} and returns the format it recognises, or
 * {@link Optional#empty()} to pass to the next matcher.
 *
 * <p>To add support for a new trust framework, implement this interface and annotate the
 * class with {@code @Component} and {@code @Order} to control evaluation priority.
 */
public interface FormatMatcher {

  /**
   * Attempts to identify the format of the credential in the given context.
   *
   * @return the detected format, or {@link Optional#empty()} if this matcher does not
   *         recognise the credential — in which case the next matcher is tried
   */
  Optional<CredentialFormat> match(DetectionContext ctx);

  /**
   * Returns {@code true} if the given JSON-LD {@code @context} node contains {@code value}.
   * Handles both single-string and array forms of the context.
   */
  static boolean contextContains(JsonNode node, String value) {
    //noinspection SwitchStatementWithTooFewBranches
    return switch (node) {
      case ArrayNode array -> StreamSupport.stream(array.spliterator(), false)
          .anyMatch(n -> value.equals(n.asText()));
      default -> value.equals(node.asText());
    };
  }
}
