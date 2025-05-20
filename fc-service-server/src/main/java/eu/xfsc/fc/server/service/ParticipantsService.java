package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.Participant;
import eu.xfsc.fc.api.generated.model.Participants;
import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.api.generated.model.UserProfiles;
import eu.xfsc.fc.server.generated.controller.ParticipantsApiDelegate;
import eu.xfsc.fc.core.dao.ParticipantDao;
import eu.xfsc.fc.core.dao.ValidatorCacheDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.ParticipantMetaData;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.VerificationService;

import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the {@link ParticipantsApiDelegate} interface.
 */
@Slf4j
@Service
public class ParticipantsService implements ParticipantsApiDelegate {
  @Autowired
  private ParticipantDao partDao;
  @Autowired
  private ValidatorCacheDao validatorCache;
  @Autowired
  private SelfDescriptionStore sdStorePublisher;
  @Autowired
  private VerificationService verificationService;

  /**
   * POST /participants : Register a new participant in the catalogue.
   *
   * @param body Participant Self-Description (required)
   * @return Created Participant (status code 201)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<Participant> addParticipant(String body) {
    log.debug("addParticipant.enter; got SD of length: {}", body.length()); // it can be JWT?
    Pair<VerificationResultParticipant, SelfDescriptionMetadata> pairResult = validateSelfDescription(body);
    VerificationResultParticipant verificationResult = pairResult.getLeft();
    SelfDescriptionMetadata selfDescriptionMetadata = pairResult.getRight();

    sdStorePublisher.storeSelfDescription(selfDescriptionMetadata, verificationResult);
    ParticipantMetaData participantMetaData = toParticipantMetaData(verificationResult, selfDescriptionMetadata);

    participantMetaData = partDao.create(participantMetaData);
    setParticipantPublicKey(participantMetaData);
    return ResponseEntity.created(URI.create("/participants/" + participantMetaData.getId())).body(participantMetaData);
  }

  /**
   * DELETE /participants/{participantId} : Delete a participant in the catalogue.
   *
   * @param participantId The participant to delete. (required)
   * @return Deleted Participant (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<Participant> deleteParticipant(String participantId) {
    log.debug("deleteParticipant.enter; got participant: {}", participantId);
    checkParticipantAccess(participantId);
    ParticipantMetaData participant = partDao.select(participantId)
        .orElseThrow(() -> new NotFoundException("Participant not found: " + participantId));
    String selfDescription = sdStorePublisher.getByHash(participant.getSdHash()).getSelfDescription().getContentAsString();
    sdStorePublisher.deleteSelfDescription(participant.getSdHash());
    participant = partDao.delete(participant.getId()).get();
    log.debug("deleteParticipant.exit; returning: {}", participant);
    participant.setSelfDescription(selfDescription);
    setParticipantPublicKey(participant);
    return ResponseEntity.ok(participant);
  }

  /**
   * GET /participants/{participantId} : Get the registered participant.
   *
   * @param participantId The participantId to get. (required)
   * @return The requested participant (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<Participant> getParticipant(String participantId) {
    log.debug("getParticipant.enter; got participant: {}", participantId);
    checkParticipantAccess(participantId);
    ParticipantMetaData part = partDao.select(participantId)
        .orElseThrow(() -> new NotFoundException("Participant not found: " + participantId));
    SelfDescriptionMetadata selfDescriptionMetadata = sdStorePublisher.getByHash(part.getSdHash());
    part.setSelfDescription(selfDescriptionMetadata.getSelfDescription().getContentAsString());
    setParticipantPublicKey(part);
    log.debug("getParticipant.exit; returning: {}", part);
    return ResponseEntity.ok(part);
  }

  /**
   * GET /participants/{participantId}/users : Get all users of the registered participant.
   *
   * @param participantId The participant Id (required)
   * @return Users of the participant (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfiles> getParticipantUsers(String participantId, Integer offset, Integer limit) {
    log.debug("getParticipantUsers.enter; got participantId: {}, offset :{}, limit:{}", participantId, offset, limit);
    checkParticipantAccess(participantId);
    PaginatedResults<UserProfile> profiles = partDao.selectUsers(participantId, offset, limit)
        .orElseThrow(() -> new NotFoundException("Participant not found: " + participantId));
    log.debug("getParticipantUsers.exit; returning: {}", profiles.getTotalCount());
    return ResponseEntity.ok(new UserProfiles((int) profiles.getTotalCount(), profiles.getResults()));
  }

  /**
   * GET /participants : Get the registered participants.
   *
   * @param offset The number of items to skip before starting to collect the result set. (optional, default to 0)
   * @param limit  The number of items to return. (optional, default to 100)
   * @return List of registered participants (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<Participants> getParticipants(Integer offset, Integer limit) { //String orderBy, Boolean asc) {
    // sorting is not supported yet by keycloak admin API
    log.debug("getParticipants.enter; got offset: {}, limit: {}", offset, limit);
    PaginatedResults<ParticipantMetaData> results = partDao.search(offset, limit);
    int total = (int) results.getTotalCount();
    if (total > 0) {
      //Adding actual SD from sd-store for each sd-hash present in keycloak
      SdFilter filter = new SdFilter();
      filter.setLimit(results.getResults().size());
      filter.setOffset(0);
      filter.setHashes(results.getResults().stream().map(ParticipantMetaData::getSdHash).collect(Collectors.toList()));
      PaginatedResults<SelfDescriptionMetadata> page = sdStorePublisher.getByFilter(filter, true, true);
      if (page.getTotalCount() > 0) {
        Map<String, ContentAccessor> sdsMap = page.getResults().stream().collect(
    		  Collectors.toMap(SelfDescriptionMetadata::getSdHash, SelfDescriptionMetadata::getSelfDescription));
        results.getResults().forEach(part -> {
          part.setSelfDescription(sdsMap.get(part.getSdHash()).getContentAsString());
          setParticipantPublicKey(part);
        });
      } else {
    	results.getResults().clear();
    	total = 0;
      }
    }
    List parts = results.getResults();
    log.debug("getParticipants.exit; returning parts: {} from total: {}", parts.size(), total);
    return ResponseEntity.ok(new Participants(total, parts));
  }

  /**
   * PUT /participants/{participantId} : Update a participant in the catalogue.
   *
   * @param participantId The participant to update. (required)
   * @param body          Participant Self-Description (required)
   * @return Updated Participant (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or The specified resource was not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<Participant> updateParticipant(String participantId, String body) {
    log.debug("updateParticipant.enter; got participant: {}", participantId);

    checkParticipantAccess(participantId);

    ParticipantMetaData participantExisted = partDao.select(participantId)
        .orElseThrow(() -> new NotFoundException("Participant not found: " + participantId));

    Pair<VerificationResultParticipant, SelfDescriptionMetadata> pairResult = validateSelfDescription(body);
    VerificationResultParticipant verificationResult = pairResult.getLeft();
    SelfDescriptionMetadata selfDescriptionMetadata = pairResult.getRight();

    ParticipantMetaData participantUpdated = toParticipantMetaData(verificationResult, selfDescriptionMetadata);
    if (!participantUpdated.getId().equals(participantExisted.getId())) {
      throw new ClientException("Participant ID cannot be changed");
    }

    sdStorePublisher.storeSelfDescription(selfDescriptionMetadata, verificationResult);
    ParticipantMetaData participantMetaData = partDao.update(participantId, participantUpdated)
        .orElseThrow(() -> new NotFoundException("Participant not found: " + participantId));
    log.debug("updateParticipant.exit; returning: {}", participantMetaData);
    participantMetaData.setSelfDescription(sdStorePublisher.getByHash(participantMetaData.getSdHash()).getSelfDescription().getContentAsString());
    setParticipantPublicKey(participantMetaData);
    return ResponseEntity.ok(participantMetaData);
  }

  /**
   * Utility method to return {@link ParticipantMetaData}.
   *
   * @param verificationResult      Result of validation
   * @param selfDescriptionMetadata Metadata of self-description
   * @return ParticipantMetaData
   */
  private ParticipantMetaData toParticipantMetaData(VerificationResultParticipant verificationResult,
                                                    SelfDescriptionMetadata selfDescriptionMetadata) {
    return new ParticipantMetaData(verificationResult.getId(), verificationResult.getParticipantName(),
        verificationResult.getParticipantPublicKey(), selfDescriptionMetadata.getSelfDescription().getContentAsString(),
        selfDescriptionMetadata.getSdHash());
  }

  /**
   * Validate self-Description.
   *
   * @param body self description
   * @return DTO object containing result and metadata of self-description
   */
  private Pair<VerificationResultParticipant, SelfDescriptionMetadata> validateSelfDescription(String body) {
    ContentAccessorDirect contentAccessorDirect = new ContentAccessorDirect(body);
    VerificationResultParticipant verificationResultParticipant =
        verificationService.verifyParticipantSelfDescription(contentAccessorDirect);
    log.debug("validateSelfDescription; verification result is: {}", verificationResultParticipant);

    SelfDescriptionMetadata selfDescriptionMetadata = new SelfDescriptionMetadata(contentAccessorDirect, verificationResultParticipant);
    log.debug("validateSelfDescription; SD metadata is: {}", selfDescriptionMetadata);

    return Pair.of(verificationResultParticipant, selfDescriptionMetadata);
  }

  private void setParticipantPublicKey(ParticipantMetaData participant) {
    String publicKey = participant.getPublicKey();
    Validator validator = publicKey != null ? validatorCache.getFromCache(publicKey) : null;
    participant.setPublicKey(validator == null ? publicKey : validator.getPublicKey());
  }
}
