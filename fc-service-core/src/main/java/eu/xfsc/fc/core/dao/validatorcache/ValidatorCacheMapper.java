package eu.xfsc.fc.core.dao.validatorcache;

import eu.xfsc.fc.core.pojo.Validator;

public final class ValidatorCacheMapper {

  private ValidatorCacheMapper() {
  }

  public static ValidatorCache toEntity(Validator validator) {
    if (validator == null) {
      return null;
    }
    return new ValidatorCache(
        validator.getDidURI(),
        validator.getPublicKey(),
        validator.getExpirationDate());
  }

  public static Validator toValidator(ValidatorCache entity) {
    if (entity == null) {
      return null;
    }
    return new Validator(
        entity.getDidUri(),
        entity.getPublicKey(),
        entity.getExpirationTime());
  }
}
