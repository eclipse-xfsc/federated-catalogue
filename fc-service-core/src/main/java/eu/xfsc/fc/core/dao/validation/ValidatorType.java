package eu.xfsc.fc.core.dao.validation;

/**
 * Validator type discriminator values for {@link ValidationResult#getValidatorType()}.
 */
public enum ValidatorType {

  /** On-demand SHACL validation of RDF assets. */
  SHACL,

  /** On-demand JSON Schema validation of non-RDF JSON assets, and of RDF assets serialised in JSON-LD. */
  JSON_SCHEMA,

  /** On-demand XML Schema validation of non-RDF XML assets, and of RDF assets serialised in RDF/XML. */
  XML_SCHEMA,

  /**
   * External trust framework compliance check.
   *
   * <p>No {@code ValidationStrategy} implementation exists yet; compliance evaluation is
   * currently performed by the trust-framework orchestrator outside the on-demand validation
   * pipeline.</p>
   */
  TRUST_FRAMEWORK
}
