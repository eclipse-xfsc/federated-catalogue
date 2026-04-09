package eu.xfsc.fc.core.service.verification;

/**
 * Constants for schema validation module type identifiers.
 *
 * <p>These values are used as keys in admin config (schema.module.{type}.enabled)
 * and as the module type field in the schema validation API.</p>
 */
public final class SchemaModuleType {

  public static final String SHACL = "SHACL";
  public static final String JSON_SCHEMA = "JSON_SCHEMA";
  public static final String XML_SCHEMA = "XML_SCHEMA";
  public static final String OWL = "OWL";

  private SchemaModuleType() {
  }
}
