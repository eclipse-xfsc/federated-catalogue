package eu.xfsc.fc.core.pojo;

import java.util.Objects;

import lombok.Getter;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.extern.slf4j.Slf4j;

/**
 * POJO Class for holding a Claim. A Claim is a triple represented by a subject, predicate, and object.
 */
@Slf4j
public class CredentialClaim {

  @Getter
  private Statement triple;
  private final String subject;
  private final String predicate;
  private final String object;

  public CredentialClaim(Statement triple, ObjectMapper objectMapper) {
    this.triple = triple;
    this.subject = rdf2String(triple.getSubject(), objectMapper);
    this.predicate = rdf2String(triple.getPredicate(), objectMapper);
    this.object = rdf2String(triple.getObject(), objectMapper);
  }

  public CredentialClaim(String subject, String predicate, String object) {
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
    return triple == null ? subject.substring(1, subject.length() - 1) : nodeValue(triple.getSubject());
  }

  public RDFNode getPredicate() {
    return triple == null ? null : triple.getPredicate();
  }

  public String getPredicateString() {
    return predicate;
  }

  public String getPredicateValue() {
    return triple == null ? predicate.substring(1, predicate.length() - 1) : nodeValue(triple.getPredicate());
  }

  public RDFNode getObject() {
    return triple == null ? null : triple.getObject();
  }

  public String getObjectString() {
    return object;
  }

  public String getObjectValue() {
    return triple == null ? object.substring(1, object.length() - 1) : nodeValue(triple.getObject());
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
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CredentialClaim other = (CredentialClaim) obj;
    return Objects.equals(object, other.object) && Objects.equals(predicate, other.predicate)
        && Objects.equals(subject, other.subject);
  }

  @Override
  public String toString() {
    return "Claim[" + subject + " " + predicate + " " + object + "]";
  }

  private String rdf2String(RDFNode node, ObjectMapper objectMapper) {
    if (node.isAnon()) {
      return node.asResource().getId().getLabelString();
    }
    try {
      if (node.isLiteral()) {
        return objectMapper.writeValueAsString(node.asLiteral().getLexicalForm());
      }
    } catch (JsonProcessingException e) {
      log.error("rdf2String error for node {}", node, e);
    }
    // node is IRI
    return "<" + node.asResource().getURI() + ">";
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
