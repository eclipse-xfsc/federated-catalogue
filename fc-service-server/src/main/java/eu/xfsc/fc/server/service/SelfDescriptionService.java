package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.SelfDescriptionHelper.parseTimeRange;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.api.generated.model.SelfDescription;
import eu.xfsc.fc.api.generated.model.SelfDescriptionResult;
import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.api.generated.model.SelfDescriptions;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultResource;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.server.generated.controller.SelfDescriptionsApiDelegate;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.SelfDescriptionsApiDelegate} interface.
 */
@Slf4j
@Service
public class SelfDescriptionService implements SelfDescriptionsApiDelegate {

  @Autowired
  private VerificationService verificationService;
  @Autowired
  private SelfDescriptionStore sdStorePublisher;

  /**
   * Service method for GET /self-descriptions : Get the list of metadata of SD in the Catalogue.
   *
   * @param uploadTr Filter for the time range when the SD was uploaded to the catalogue.
   *                        The time range has to be specified as start time and end time as ISO8601 timestamp
   *                        separated by a &#x60;/&#x60;. (optional)
   * @param statusTr Filter for the time range when the status of the SD was last changed in the catalogue.
   *                        The time range has to be specified as start time and end time as ISO8601 timestamp
   *                        separated by a &#x60;/&#x60;. (optional)
   * @param issuers         Filter for the issuer of the SD. This is the unique ID of the Participant
   *                        that has prepared the SD. (optional)
   * @param validators      Filter for a validator of the SD. This is the unique ID of the Participant
   *                        that validated (part of) the SD. (optional)
   * @param statuses        Filter for the status of the SD. (optional, default to active)
   * @param ids             Filter for id/credentialSubject of the SD. (optional)
   * @param hashes          Filter for a hash of the SD. (optional)
   * @param offset          The number of items to skip before starting to collect the result set.
   *                        (optional, default to 0)
   * @param limit           The number of items to return. (optional, default to 100)
   * @return List of meta-data of available SD. (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or May contain hints how to solve the error or indicate what went wrong at the server.
   *        Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<SelfDescriptions> readSelfDescriptions(String uploadTr, String statusTr, 
          List<String> issuers, List<String> validators, List<SelfDescriptionStatus> statuses, List<String> ids,
          List<String> hashes, Boolean withMeta, Boolean withContent, Integer offset, Integer limit) {
    log.debug("readSelfDescriptions.enter; got uploadTimeRange: {}, statusTimeRange: {}, issuers: {}, validators: {}, "
          + "statuses: {}, ids: {}, hashes: {}, withMeta: {}, withContent: {}, offset: {}, limit: {}",
        uploadTr, statusTr, issuers, validators, statuses, ids, hashes, withMeta, withContent, offset, limit);

    final SdFilter filter;
    if (isNotNullObjects(ids, hashes, issuers, validators, statuses, uploadTr, statusTr)) {
      filter = setupSdFilter(ids, hashes, statuses, issuers, validators, uploadTr, statusTr, limit, offset);
    } else {
      filter = new SdFilter();
      filter.setStatuses(List.of(SelfDescriptionStatus.ACTIVE));
      filter.setLimit(limit);
      filter.setOffset(offset);
    }
    final PaginatedResults<SelfDescriptionMetadata> selfDescriptions = sdStorePublisher.getByFilter(filter, withMeta, withContent);
    log.debug("readSelfDescriptions.exit; returning: {}", selfDescriptions);
    List<SelfDescriptionResult> results = null;
    if (withMeta) {
        if (withContent) {
            results = selfDescriptions.getResults().stream().map(sd -> 
                new SelfDescriptionResult(sd, sd.getSelfDescription().getContentAsString())).collect(Collectors.toList());
        } else {
            results = selfDescriptions.getResults().stream().map(sd -> 
                new SelfDescriptionResult(sd, null)).collect(Collectors.toList());
        }
    } else if (withContent) {
        results = selfDescriptions.getResults().stream().map(sd -> 
            new SelfDescriptionResult(null, sd.getSelfDescription().getContentAsString())).collect(Collectors.toList());
    }
    return ResponseEntity.ok(new SelfDescriptions((int) selfDescriptions.getTotalCount(), results));
  }
  
  /**
   * Service method for GET /self-descriptions/{self_description_hash} : Read a SD by its hash. Returns the content
   * of the single SD.
   *
   * @param selfDescriptionHash Hash of the self-description (required)
   * @return The requested Self-Description (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Self-Description not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<String> readSelfDescriptionByHash(String selfDescriptionHash) {
    log.debug("readSelfDescriptionByHash.enter; got hash: {}", selfDescriptionHash);
    SelfDescriptionMetadata sdMetadata = sdStorePublisher.getByHash(selfDescriptionHash);

    HttpHeaders responseHeaders = new HttpHeaders();
    //responseHeaders.set("Content-Type", "application/ld+json");

    log.debug("readSelfDescriptionByHash.exit; returning self-description by hash: {}", selfDescriptionHash);
    return ResponseEntity.ok()
        .headers(responseHeaders)
        .body(sdMetadata.getSelfDescription().getContentAsString());
  }

  /**
   * Service method for DELETE /self-descriptions/{self_description_hash} : Completely delete a SD.
   *
   * @param selfDescriptionHash Hash of the SD (required)
   * @return OK (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<Void> deleteSelfDescription(String selfDescriptionHash) {
    log.debug("deleteSelfDescription.enter; got hash: {}", selfDescriptionHash);
    // TODO: 27.07.2022 The method is not described in the documentation.

    SelfDescriptionMetadata sdMetadata = sdStorePublisher.getByHash(selfDescriptionHash);

    checkParticipantAccess(sdMetadata.getIssuer());

    sdStorePublisher.deleteSelfDescription(selfDescriptionHash);
    log.debug("deleteSelfDescription.exit; deleted self-description by hash: {}", sdMetadata.getSdHash());
    return new ResponseEntity<>(HttpStatus.OK);
  }

  /**
   * Service method for POST /self-descriptions : Add a new SD to the catalogue.
   *
   * @param selfDescription The new SD (required)
   * @return Created (status code 201)
   *         or The request was accepted but the validation is not finished yet. (status code 202)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ResponseEntity<SelfDescription> addSelfDescription(String selfDescription) {
    log.debug("addSelfDescription.enter; got selfDescription: {}", selfDescription.length());

    try {
     // TODO: 27.07.2022 Need to change the description and the order of actions in the documentation.
     //  The FH scheme is different from the real process.
      ContentAccessorDirect contentAccessor = new ContentAccessorDirect(selfDescription);

      VerificationResult verificationResult = verificationService.verifySelfDescription(contentAccessor);

      SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(verificationResult.getId(), verificationResult.getIssuer(), 
              verificationResult.getValidators(), contentAccessor);
      checkParticipantAccess(sdMetadata.getIssuer());
      sdStorePublisher.storeSelfDescription(sdMetadata, verificationResult);

      log.debug("addSelfDescription.exit; returning self-description by hash: {}", sdMetadata.getSdHash());
      return ResponseEntity.created(URI.create("/self-descriptions/" + sdMetadata.getSdHash())).body(sdMetadata);
    } catch (ValidationException exception) {
      log.debug("addSelfDescription.error; Self-description isn't parsed due to: " + exception.getMessage(), exception);
      throw new ClientException("Self-description isn't parsed due to: " + exception.getMessage());
    }
  }

  /**
   * Service method for POST /self-descriptions/{self_description_hash}/revoke :
   * Change the lifecycle state of a SD to revoke.
   *
   * @param selfDescriptionHash Hash of the self-description (required)
   * @return Revoked (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<SelfDescription> updateSelfDescription(String selfDescriptionHash) {
    log.debug("updateSelfDescription.enter; got hash: {}", selfDescriptionHash);
    // TODO: 27.07.2022 Need to change the description and the order of actions in the documentation
    //  (The documentation specifies a search by credential object, not by hash.)

    SelfDescriptionMetadata sdMetadata = sdStorePublisher.getByHash(selfDescriptionHash);

    checkParticipantAccess(sdMetadata.getIssuer());

    if (sdMetadata.getStatus().equals(SelfDescriptionStatus.ACTIVE)) {
      sdStorePublisher.changeLifeCycleStatus(sdMetadata.getSdHash(), SelfDescriptionStatus.REVOKED);
    } else {
      throw new ConflictException("The SD status cannot be changed because the SD Metadata status is "
          + sdMetadata.getStatus());
    }

    log.debug("updateSelfDescription.exit; update self-description by hash: {}", selfDescriptionHash);
    return new ResponseEntity<>(sdMetadata, HttpStatus.OK);
  }

  private boolean isNotNullObjects(Object... objs) {
    return Arrays.stream(objs).anyMatch(x -> !Objects.isNull(x));
  }

  private SdFilter setupSdFilter(List<String> ids, List<String> hashes, List<SelfDescriptionStatus> statuses, List<String> issuers,
                                 List<String> validators, String uploadTr, String statusTr, Integer limit, Integer offset) {
    SdFilter filterParams = new SdFilter();
    filterParams.setIds(ids);
    filterParams.setHashes(hashes);
    filterParams.setStatuses(Objects.requireNonNullElseGet(statuses, () -> List.of(SelfDescriptionStatus.ACTIVE)));
    filterParams.setIssuers(issuers);
    filterParams.setValidators(validators);
    if (uploadTr != null) {
      String[] timeRanges = parseTimeRange(uploadTr);
      filterParams.setUploadTimeRange(Instant.parse(timeRanges[0]), Instant.parse(timeRanges[1]));
    }
    if (statusTr != null) {
      String[] timeRanges = parseTimeRange(statusTr);
      filterParams.setStatusTimeRange(Instant.parse(timeRanges[0]), Instant.parse(timeRanges[1]));
    }
    filterParams.setLimit(limit);
    filterParams.setOffset(offset);
    return filterParams;
  }
}