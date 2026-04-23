package eu.xfsc.fc.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.RDFNode;

/**
 * Utility for serializing RDF nodes to the internal claim string format.
 *
 * <p>Blank nodes are serialized as their raw label; literals as a JSON-quoted string of
 * the lexical form; IRIs are wrapped in angle brackets ({@code <uri>}).</p>
 */
@Slf4j
@UtilityClass
public class RdfNodeMapper {

  /**
   * Serializes an RDF node to a string in the internal claim format:
   * blank nodes as their raw label, literals as a JSON-quoted string of the lexical form,
   * and IRIs wrapped in angle brackets ({@code <uri>}).
   *
   * <p>If JSON serialization of a literal fails, falls back to wrapping the lexical form
   * in double quotes directly.</p>
   *
   * @param node         the RDF node to serialize
   * @param objectMapper the Jackson mapper for JSON-quoting literal values
   * @return the string representation of the node
   */
  public static String rdf2String(RDFNode node, ObjectMapper objectMapper) {
    if (node.isAnon()) {
      return node.asResource().getId().getLabelString();
    } else if (node.isLiteral()) {
      try {
        return objectMapper.writeValueAsString(node.asLiteral().getLexicalForm());
      } catch (JsonProcessingException e) {
        log.error("rdf2String error for node {}", node, e);
        return "\"" + node.asLiteral().getLexicalForm() + "\"";
      }
    } else {
      return "<" + node.asResource().getURI() + ">";
    }
  }
}
