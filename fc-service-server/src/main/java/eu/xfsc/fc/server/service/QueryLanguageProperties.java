package eu.xfsc.fc.server.service;

import java.util.EnumMap;
import java.util.Map;

import eu.xfsc.fc.api.generated.model.QueryLanguage;

/**
 * Metadata associated with each {@link QueryLanguage} value.
 * Centralises display names, content types, example queries and documentation URLs
 * that cannot live on the OpenAPI-generated enum itself.
 */
public record QueryLanguageProperties(
    String displayName, String contentType, String exampleQuery, String documentationUrl) {

  private static final Map<QueryLanguage, QueryLanguageProperties> PROPS = new EnumMap<>(Map.of(
      QueryLanguage.OPENCYPHER, new QueryLanguageProperties(
          "openCypher", "application/opencypher-query",
          "MATCH (n:Resource) RETURN n.uri AS id, n.name AS name LIMIT 10",
          "https://neo4j.com/docs/cypher-manual/"),
      QueryLanguage.SPARQL, new QueryLanguageProperties(
          "SPARQL", "application/sparql-query",
          "PREFIX ex: <http://example.org/> SELECT ?id ?name WHERE { ?id ex:name ?name } LIMIT 10",
          "https://jena.apache.org/documentation/query/")));

  /**
   * Returns the properties for the given query language.
   *
   * @param language the query language
   * @return the associated properties
   * @throws IllegalArgumentException if no properties are defined for the language
   */
  public static QueryLanguageProperties of(QueryLanguage language) {
    var props = PROPS.get(language);
    if (props == null) {
      throw new IllegalArgumentException("No properties defined for " + language);
    }
    return props;
  }

  /**
   * Resolves a {@link QueryLanguage} from a Content-Type header value.
   *
   * @param contentType the Content-Type header (may include charset or other parameters)
   * @return the matching query language
   * @throws IllegalArgumentException if the content type is not recognised
   */
  public static QueryLanguage fromContentType(String contentType) {
    String mediaType = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
    for (var entry : PROPS.entrySet()) {
      if (entry.getValue().contentType().equals(mediaType)) {
        return entry.getKey();
      }
    }
    throw new IllegalArgumentException("Unrecognised query content type: " + contentType);
  }
}