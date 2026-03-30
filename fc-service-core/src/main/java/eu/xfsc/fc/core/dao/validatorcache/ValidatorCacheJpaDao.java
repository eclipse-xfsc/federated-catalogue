package eu.xfsc.fc.core.dao.validatorcache;

import eu.xfsc.fc.core.pojo.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ValidatorCacheJpaDao implements ValidatorCacheDao {

  private final ValidatorCacheRepository repository;

  @Override
  public void addToCache(Validator validator) {
    repository.save(ValidatorCacheMapper.toEntity(validator));
  }

  @Override
  public Validator getFromCache(String didURI) {
    return repository.findById(didURI)
        .map(ValidatorCacheMapper::toValidator)
        .orElse(null);
  }

  @Override
  public void removeFromCache(String didURI) {
    repository.deleteById(didURI);
  }

  @Override
  @Transactional
  public int expireValidators() {
    return repository.deleteExpired(Instant.now());
  }
}
