package eu.xfsc.fc.core.dao.validation;

/**
 * Validator type discriminator values for {@link ValidationResult#getValidatorType()}.
 */
public enum ValidatorType {

  /** On-demand schema validation (SHACL, JSON Schema, XML Schema). */
  SCHEMA,

  /** External trust framework compliance check. */
  TRUST_FRAMEWORK
}
