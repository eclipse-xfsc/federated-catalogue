package eu.xfsc.fc.core.pojo;

import java.util.Objects;

import eu.xfsc.fc.core.util.RdfNodeMapper;
import lombok.Getter;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * POJO Class for holding a Claim. A Claim is a triple represented by a subject, predicate, and object.
 */
public class RdfClaim {

  @Getter
  private Statement triple;
  private final String subject;
  private final String predicate;
  private final String object;

  public RdfClaim(Statement triple, ObjectMapper objectMapper) {
    this.triple = triple;
    this.subject = RdfNodeMapper.rdf2String(triple.getSubject(), objectMapper);
    this.predicate = RdfNodeMapper.rdf2String(triple.getPredicate(), objectMapper);
    this.object = RdfNodeMapper.rdf2String(triple.getObject(), objectMapper);
  }

  public RdfClaim(String subject, String predicate, String object) {
    this.subject = subject;
    this.predicate = predicate;
    this.object = object;
  }

    public RDFNode getSubject() {
    return triple == null ? null : triple.getSubject();
  }

  public String getSubjectString() {
    return subject;
  }

  public String getSubjectValue() {
      return triple == null ? extractValue(subject) : nodeValue(triple.getSubject());
  }

  public RDFNode getPredicate() {
    return triple == null ? null : triple.getPredicate();
  }

  public String getPredicateString() {
    return predicate;
  }

  public String getPredicateValue() {
      return triple == null ? extractValue(predicate) : nodeValue(triple.getPredicate());
  }

  public RDFNode getObject() {
    return triple == null ? null : triple.getObject();
  }

  public String getObjectString() {
    return object;
  }

  public String getObjectValue() {
      return triple == null ? extractValue(object) : nodeValue(triple.getObject());
  }

  public String asTriple() {
    return String.format("%s %s %s . \n", getSubjectString(), getPredicateString(), getObjectString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(object, predicate, subject);
  }

  @Override
  public boolean equals(Object obj) {
      if (this == obj)
          return true;
      if (!(obj instanceof RdfClaim other))
          return false;
      return Objects.equals(object, other.object)
              && Objects.equals(predicate, other.predicate)
              && Objects.equals(subject, other.subject);
  }

    @Override
  public String toString() {
    return "Claim[" + subject + " " + predicate + " " + object + "]";
  }

    private static String extractValue(String encoded) {
        if (isIri(encoded)) {
            return encoded.substring(1, encoded.length() - 1);
        }
        if (isLiteral(encoded)) {
            return encoded.substring(1, encoded.length() - 1);
        }
        // is blank node: raw label string, return as is
        return encoded;
    }

    private static boolean isLiteral(String encoded) {
        return encoded.startsWith("\"") && encoded.endsWith("\"");
    }

    private static boolean isIri(String encoded) {
        return encoded.startsWith("<") && encoded.endsWith(">");
    }

    private String nodeValue(RDFNode node) {
    if (node.isAnon()) {
      return node.asResource().getId().getLabelString();
    }
    if (node.isLiteral()) {
      return node.asLiteral().getLexicalForm();
    }
    return node.asResource().getURI();
  }

}
