package eu.xfsc.fc.core.service.schemastore;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 *
 * @author hylke
 */
@NoArgsConstructor
@Getter
@Setter
@Accessors(chain = true)
public class SchemaAnalysisResult {

  /**
   * Flag indicating the schema is a valid schema.
   */
  private boolean valid;
  /**
   * The detected type of the schema.
   */
  private SchemaType schemaType;
  /**
   * The identifier of the schema if it can be extracted from the schema. Null
   * otherwise.
   */
  private String extractedId;
  /**
   * The URLs of the entities that are defined in this schema.
   */
  private Set<String> extractedUrls;
  /**
   * The error message if validation failed.
   */
  private String errorMessage;
  /**
   * Warning message if protected namespace statements were filtered during import.
   */
  private String warning;

}
