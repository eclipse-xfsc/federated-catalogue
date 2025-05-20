package eu.xfsc.fc.core.dao;

import eu.xfsc.fc.core.pojo.Validator;

/**
 *
 * @author hylke
 */
public interface ValidatorCacheDao {

  /**
   * Add the given validator to the cache.
   *
   * @param validator
   */
  void addToCache(Validator validator);

  /**
   * Search for a validator with the given DID.
   *
   * @param didURI The DID of the requested validator.
   * @return the requested validator, or null if it does not exist.
   */
  Validator getFromCache(String didURI);

  /**
   * Remove the validator with the given DID from the cache.
   *
   * @param didURI the DID of the validator to remove.
   */
  void removeFromCache(String didURI);

  /**
   * Removed expired validators from the cache.
   *
   * @return the number of deleted validators.
   */
  int expireValidators();

}
