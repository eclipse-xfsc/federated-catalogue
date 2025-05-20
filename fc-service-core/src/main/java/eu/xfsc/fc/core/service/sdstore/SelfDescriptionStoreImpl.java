package eu.xfsc.fc.core.service.sdstore;

import java.time.Instant;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableInt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.dao.SelfDescriptionDao;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import lombok.extern.slf4j.Slf4j;

/**
 * File system based implementation of the self-description store interface.
 *
 * @author hylke
 * @author j_reuter
 */
@Slf4j
@Component("sdStore")
@Transactional
public class SelfDescriptionStoreImpl implements SelfDescriptionStore {

  @Autowired
  private SelfDescriptionDao dao;

  @Autowired
  private GraphStore graphDb;

  @Override
  public ContentAccessor getSDFileByHash(final String hash) {
    SdMetaRecord meta = (SdMetaRecord) getByHash(hash);
    return meta.getSelfDescription();
  }

  @Override
  public SelfDescriptionMetadata getByHash(final String hash) {
    SdMetaRecord sdmRecord = dao.select(hash);
    if (sdmRecord == null) {
      throw new NotFoundException(String.format("no self-description found for hash %s", hash));
    }
    return sdmRecord;
  }

  @Override
  public PaginatedResults<SelfDescriptionMetadata> getByFilter(final SdFilter filter, final boolean withMeta, final boolean withContent) {
    log.debug("getByFilter.enter; got filter: {}, withMeta: {}, withContent: {}", filter, withMeta, withContent);
    PaginatedResults<SdMetaRecord> page = dao.selectByFilter(filter, withMeta, withContent);
    List sds = page.getResults();
    return new PaginatedResults<>(page.getTotalCount(), (List<SelfDescriptionMetadata>) sds);
  }

  @Override
  public void storeSelfDescription(final SelfDescriptionMetadata sdMetadata, final VerificationResult verificationResult) {
	storeSDInternal(sdMetadata, verificationResult);
  }
  
  protected SubjectHashRecord storeSDInternal(final SelfDescriptionMetadata sdMetadata, final VerificationResult verificationResult) {
    if (verificationResult == null) {
      throw new IllegalArgumentException("verification result must not be null");
    }
    log.debug("storeSelfDescription.enter; got meta: {}", sdMetadata);

    Instant expirationTime = null;
    final List<Validator> validators = verificationResult.getValidators();
    if (validators != null) {
      Validator minVal = validators.stream().min(new Validator.ExpirationComparator()).orElse(null);
      expirationTime = minVal == null ? null : minVal.getExpirationDate();
    }
    SdMetaRecord sd = new SdMetaRecord(sdMetadata.getSdHash(), sdMetadata.getId(), sdMetadata.getStatus(), sdMetadata.getIssuer(), sdMetadata.getValidatorDids(), 
    		sdMetadata.getUploadDatetime(), sdMetadata.getStatusDatetime(), sdMetadata.getSelfDescription(), expirationTime); // sdMetadata.getSelfDescription(), verificationResult);

    SubjectHashRecord subjectHash = null;
    try {
      subjectHash = dao.insert(sd);
    } catch (DuplicateKeyException ex) {
      if (ex.getMessage().contains("sdfiles_pkey")) {
        throw new ConflictException(String.format("self-description with hash %s already exists", sdMetadata.getSdHash()));
      }
      if (ex.getMessage().contains("idx_sd_file_is_active")) {
        throw new ConflictException(String.format("active self-description with subjectId %s already exists", sdMetadata.getId()));
      }
      log.error("storeSelfDescription.error 2", ex);
      throw new ServerException(ex);
    }
    if (subjectHash != null && subjectHash.subjectId() != null) {
      graphDb.deleteClaims(subjectHash.subjectId());
    }
    graphDb.addClaims(verificationResult.getClaims(), sdMetadata.getId());
    return subjectHash;
  }

  @Override
  public void changeLifeCycleStatus(final String hash, final SelfDescriptionStatus targetStatus) {
	SubjectStatusRecord ssr = dao.update(hash, targetStatus.ordinal());
    log.debug("changeLifeCycleStatus; update result: {}", ssr);
    if (ssr == null) {
      throw new NotFoundException("no self-description found for hash " + hash);
    }

    if (ssr.subjectId() == null) {
      throw new ConflictException(String.format("can not change status of self-description with hash %s: require status %s, but encountered status %s", 
    	hash, SelfDescriptionStatus.ACTIVE, ssr.getSdStatus()));
    }
    graphDb.deleteClaims(ssr.subjectId());
  }

  @Override
  public void deleteSelfDescription(final String hash) {
	SubjectStatusRecord ssr = dao.delete(hash);
    log.debug("deleteSelfDescription; delete result: {}", ssr);
    if (ssr == null) {
      throw new NotFoundException("no self-description found for hash " + hash);
    }
    
    if (ssr.getSdStatus() == SelfDescriptionStatus.ACTIVE) {
      graphDb.deleteClaims(ssr.subjectId());
    }
  }

  @Override
  public int invalidateSelfDescriptions() {
    // A possible Performance optimisation may be required here to limit the number
    // of SDs that are expired in one run, to limit the size of the Transaction.
    List<String> expiredSds = dao.selectExpiredHashes();
    final MutableInt count = new MutableInt();
    // we could also expire/update all SDs from batch in one batchUpdate..
    expiredSds.forEach(sdHash -> {
      try {
        changeLifeCycleStatus(sdHash, SelfDescriptionStatus.EOL);
        count.increment();
      } catch (ConflictException exc) {
        log.info("invalidateSelfDescriptions; SD was set non-active before we could expire it. Hash: {}", sdHash);
      }
    });
    return count.intValue();
  }

  @Override
  public List<String> getActiveSdHashes(String afterHash, int count, int chunks, int chunkId) {
    return dao.selectHashes(afterHash, count, chunks, chunkId);
  }

  @Override
  public void clear() {
	int cnt = dao.deleteAll();
    log.debug("clear; deleted {} self-descriptions", cnt);
  }

}
