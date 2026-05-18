package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.xfsc.fc.core.pojo.ContentAccessor;

import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Strategy interface for a credential format family. Implementations own the entire
 * lifecycle for one format: detection, JWT determination, JSON-LD unwrap, and any
 * format-specific policy enforcement.
 *
 * <p>Spring collects all implementations into the ordered list used by
 * {@link CredentialFormatDetector}; the verification strategy then dispatches polymorphically
 * by looking up the processor whose {@link #getFormat()} matches the detected format.
 *
 * <p>Adding a new credential family means adding one {@code @Component @Order(N)} class
 * that implements this interface — no edits to the strategy or the detector.
 */
public interface CredentialFormatProcessor {

  /**
   * The credential format produced by this processor. Used by the verification strategy to
   * map a detection result back to the processor that should handle it.
   */
  CredentialFormat getFormat();

  /**
   * Attempts to identify the credential as belonging to this processor's format.
   *
   * @return {@code Optional.of(getFormat())} on a positive match, {@code Optional.of(UNKNOWN)}
   * when this processor recognises the credential as belonging to its family but
   * the payload is malformed (stops evaluation), or {@code Optional.empty()} to
   * pass to the next processor in the chain
   */
  Optional<CredentialFormat> match(DetectionContext ctx);

  /**
   * Processes the outer envelope for this format: verify the JWT signature (when applicable
   * and requested), apply any format-specific policies, and unwrap the payload to JSON-LD.
   * The strategy calls this exactly once per credential, after format detection.
   *
   * @param body       the original raw body (compact JWT or JSON-LD)
   * @param payload    the {@link ContentAccessor} for the same body
   * @param verifySigs whether the caller requested signature verification at all
   * @return the unwrapped JSON-LD payload, the resulting JWT validator (or {@code null}),
   * and whether this envelope was a compact JWT
   */
  ProcessedEnvelope process(String body, ContentAccessor payload, boolean verifySigs);

  /**
   * Converts a payload that appears <em>nested inside another VP or EVP wrapper</em> into
   * JSON-LD. Implementations <strong>must not</strong> verify signatures or apply
   * format-specific policies: the outer envelope's {@link #process} call already cleared
   * those, and the nested credential's signature (when present) is verified separately by
   * the inner-VC verification path. Pure unwrap — JWT → JSON-LD if applicable, otherwise
   * pass-through.
   *
   * <p>Called when extracting:
   * <ul>
   *   <li>a compact JWT VC inside a VP's {@code verifiableCredential} array</li>
   *   <li>an {@code EnvelopedVerifiableCredential} entry inside a VP or EVP</li>
   * </ul>
   */
  ContentAccessor unwrapNested(ContentAccessor payload);

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
