package eu.xfsc.fc.core.service.assetstore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IriValidatorTest {

  private IriValidator validator;

  @BeforeEach
  void setUp() {
    validator = new IriValidator();
  }

  // --- isValid: valid cases ---

  @Test
  void isValid_validDidWeb_returnsTrue() {
    assertTrue(validator.isValid("did:web:example.com"));
  }

  @Test
  void isValid_validDidKey_returnsTrue() {
    assertTrue(validator.isValid("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"));
  }

  @Test
  void isValid_didWithColonsInId_returnsTrue() {
    assertTrue(validator.isValid("did:web:example.com:assets:contract-123"));
  }

  @Test
  void isValid_validUuidUrn_returnsTrue() {
    assertTrue(validator.isValid("urn:uuid:550e8400-e29b-41d4-a716-446655440000"));
  }

  @Test
  void isValid_uuidUrnUpperCase_returnsTrue() {
    assertTrue(validator.isValid("urn:uuid:550E8400-E29B-41D4-A716-446655440000"));
  }

  @Test
  void isValid_genericUrn_returnsTrue() {
    assertTrue(validator.isValid("urn:isbn:978-3-16-148410-0"));
  }

  @Test
  void isValid_httpIri_returnsTrue() {
    assertTrue(validator.isValid("http://example.org/MyOntology"));
  }

  @Test
  void isValid_httpsIri_returnsTrue() {
    assertTrue(validator.isValid("https://example.org/shapes/PersonShape"));
  }

  // --- isValid: invalid cases ---

  @Test
  void isValid_null_returnsFalse() {
    assertFalse(validator.isValid(null));
  }

  @Test
  void isValid_blank_returnsFalse() {
    assertFalse(validator.isValid("  "));
  }

  @Test
  void isValid_empty_returnsFalse() {
    assertFalse(validator.isValid(""));
  }

  @Test
  void isValid_malformedDid_returnsFalse() {
    assertFalse(validator.isValid("did:"));
  }

  @Test
  void isValid_didWithoutMethodSpecificId_returnsFalse() {
    assertFalse(validator.isValid("did:web"));
  }

  @Test
  void isValid_malformedUuidUrn_returnsFalse() {
    assertFalse(validator.isValid("urn:uuid:not-a-uuid"));
  }

  @Test
  void isValid_uuidUrnTooShort_returnsFalse() {
    assertFalse(validator.isValid("urn:uuid:550e8400"));
  }

  @Test
  void isValid_unrecognizedFormat_returnsFalse() {
    assertFalse(validator.isValid("foobar123"));
  }

  @Test
  void isValid_plainUuidWithoutPrefix_returnsFalse() {
    assertFalse(validator.isValid("550e8400-e29b-41d4-a716-446655440000"));
  }

  @Test
  void isValid_httpIriWithoutHost_returnsFalse() {
    assertFalse(validator.isValid("http://"));
  }

  @Test
  void isValid_httpIriInvalidSyntax_returnsFalse() {
    assertFalse(validator.isValid("http:// spaces not allowed"));
  }

  @Test
  void isValid_didUppercaseMethod_returnsFalse() {
    assertFalse(validator.isValid("did:Web:example.com"));
  }

}
