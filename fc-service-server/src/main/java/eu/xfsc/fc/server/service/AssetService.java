package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.AssetHelper.parseTimeRange;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetResult;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.Assets;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.server.generated.controller.AssetsApiDelegate;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.AssetsApiDelegate} interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService implements AssetsApiDelegate {

  private final VerificationService verificationService;
  private final AssetStore assetStorePublisher;
  private final HttpServletRequest httpServletRequest;

  /**
   * Service method for GET /assets : Get the list of metadata of assets in the Catalogue.
   *
   * @param uploadTr Filter for the time range when the asset was uploaded to the catalogue.
   *                        The time range has to be specified as start time and end time as ISO8601 timestamp
   *                        separated by a &#x60;/&#x60;. (optional)
   * @param statusTr Filter for the time range when the status of the asset was last changed in the catalogue.
   *                        The time range has to be specified as start time and end time as ISO8601 timestamp
   *                        separated by a &#x60;/&#x60;. (optional)
   * @param issuers         Filter for the issuer of the asset. This is the unique ID of the Participant
   *                        that has prepared the asset. (optional)
   * @param validators      Filter for a validator of the asset. This is the unique ID of the Participant
   *                        that validated (part of) the asset. (optional)
   * @param statuses        Filter for the status of the asset. (optional, default to active)
   * @param ids             Filter for id/credentialSubject of the asset. (optional)
   * @param hashes          Filter for a hash of the asset. (optional)
   * @param offset          The number of items to skip before starting to collect the result set.
   *                        (optional, default to 0)
   * @param limit           The number of items to return. (optional, default to 100)
   * @return List of meta-data of available assets. (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or May contain hints how to solve the error or indicate what went wrong at the server.
   *        Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<Assets> readAssets(String uploadTr, String statusTr,
          List<String> issuers, List<String> validators, List<AssetStatus> statuses, List<String> ids,
          List<String> hashes, Boolean withMeta, Boolean withContent, Integer offset, Integer limit) {
    log.debug("readAssets.enter; got uploadTimeRange: {}, statusTimeRange: {}, issuers: {}, validators: {}, "
          + "statuses: {}, ids: {}, hashes: {}, withMeta: {}, withContent: {}, offset: {}, limit: {}",
        uploadTr, statusTr, issuers, validators, statuses, ids, hashes, withMeta, withContent, offset, limit);

    final AssetFilter filter;
    if (isNotNullObjects(ids, hashes, issuers, validators, statuses, uploadTr, statusTr)) {
      filter = setupAssetFilter(ids, hashes, statuses, issuers, validators, uploadTr, statusTr, limit, offset);
    } else {
      filter = new AssetFilter();
      filter.setStatuses(List.of(AssetStatus.ACTIVE));
      filter.setLimit(limit);
      filter.setOffset(offset);
    }
    final PaginatedResults<AssetMetadata> assets = assetStorePublisher.getByFilter(filter, withMeta, withContent);
    log.debug("readAssets.exit; returning: {}", assets);
    List<AssetResult> results = null;
    if (withMeta) {
        if (withContent) {
            results = assets.getResults().stream().map((AssetMetadata asset) ->
                new AssetResult(asset, asset.getContentAccessor().getContentAsString())).collect(Collectors.toList());
        } else {
            results = assets.getResults().stream().map((AssetMetadata asset) ->
                new AssetResult(asset, null)).collect(Collectors.toList());
        }
    } else if (withContent) {
        results = assets.getResults().stream().map((AssetMetadata asset) ->
            new AssetResult(null, asset.getContentAccessor().getContentAsString())).collect(Collectors.toList());
    }
    return ResponseEntity.ok(new Assets((int) assets.getTotalCount(), results));
  }
  
  /**
   * Service method for GET /assets/{id} : Read an asset by its IRI.
   * Returns the content of the single asset.
   *
   * @param id IRI of the asset (required)
   * @return The requested asset (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Asset not found (status code 404)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<String> readAssetById(String id) {
    AssetMetadata assetMetadata = assetStorePublisher.getById(id);

    // Only RDF assets have content in DB. Non-RDF assets (PDF, binary) are in FileStore
    // and require a dedicated download endpoint
    ContentAccessor content = assetMetadata.getContentAccessor();
    if (content == null) {
      throw new ClientException("Asset " + id + " (hash: " + assetMetadata.getAssetHash()
          + ") is a non-RDF asset (content-type: " + assetMetadata.getContentType()
          + "). Raw content download is not supported via this endpoint.");
    }

    return ResponseEntity.ok()
        .body(content.getContentAsString());
  }

  /**
   * Service method for DELETE /assets/{asset_hash} : Completely delete an asset.
   *
   * <p>Unlike other asset endpoints which use IRI-based identification, the delete operation
   * uses the content hash (PRIMARY KEY) to guarantee unambiguous single-row targeting.
   * See ADR 7 — Delete Operation Exception.</p>
   *
   * @param assetHash SHA-256 content hash of the asset (required)
   * @return OK (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<Void> deleteAsset(String assetHash) {
    AssetMetadata assetMetadata = assetStorePublisher.getByHash(assetHash);

    checkParticipantAccess(assetMetadata.getIssuer());

    assetStorePublisher.deleteAsset(assetHash);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  /**
   * Service method for POST /assets : Add a new asset to the catalogue.
   *
   * @param body The new asset content (required)
   * @return Created (status code 201)
   *         or The request was accepted but the validation is not finished yet. (status code 202)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ResponseEntity<Asset> addAsset(String body) {
    log.debug("addAsset.enter; got asset of length: {}", body.length());

    try {
     // TODO: 27.07.2022 Need to change the description and the order of actions in the documentation.
     //  The FH scheme is different from the real process.
      String contentType = httpServletRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      ContentAccessorDirect contentAccessor = new ContentAccessorDirect(body, contentType);

      CredentialVerificationResult verificationResult = verificationService.verifyCredential(contentAccessor);

      AssetMetadata assetMetadata = new AssetMetadata(verificationResult.getId(), verificationResult.getIssuer(),
              verificationResult.getValidators(), contentAccessor);
      checkParticipantAccess(assetMetadata.getIssuer());
      assetStorePublisher.storeCredential(assetMetadata, verificationResult);

      if (verificationResult.getWarnings() != null && !verificationResult.getWarnings().isEmpty()) {
        assetMetadata.setWarnings(verificationResult.getWarnings());
      }

      log.debug("addAsset.exit; returning asset with id: {}", assetMetadata.getId());
      String encodedId = UriUtils.encodePathSegment(assetMetadata.getId(), StandardCharsets.UTF_8);
      return ResponseEntity.created(URI.create("/assets/" + encodedId)).body(assetMetadata);
    } catch (ValidationException exception) {
      log.debug("addAsset.error; Asset credential isn't parsed due to: " + exception.getMessage(), exception);
      throw new ClientException("Asset credential isn't parsed due to: " + exception.getMessage());
    }
  }

  /**
   * Service method for POST /assets/{asset_hash}/revoke :
   * Change the lifecycle state of an asset to revoked.
   *
   * <p>Like the delete operation, the revoke endpoint uses the content hash to guarantee
   * unambiguous single-row targeting. When versioning is introduced (CAT-FR-LM-01),
   * this will be replaced by version-specific revocation. See ADR 7 — Delete Operation
   * Exception.</p>
   *
   * @param assetHash SHA-256 content hash of the asset (required)
   * @return Revoked (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  @Transactional
  public ResponseEntity<Asset> revokeAsset(String assetHash) {
    AssetMetadata assetMetadata = assetStorePublisher.getByHash(assetHash);

    checkParticipantAccess(assetMetadata.getIssuer());

    if (assetMetadata.getStatus().equals(AssetStatus.ACTIVE)) {
      assetStorePublisher.changeLifeCycleStatus(assetHash, AssetStatus.REVOKED);
    } else {
      throw new ConflictException("The asset status cannot be changed because the asset metadata status is "
          + assetMetadata.getStatus());
    }

    return new ResponseEntity<>(assetMetadata, HttpStatus.OK);
  }

  private boolean isNotNullObjects(Object... objs) {
    return Arrays.stream(objs).anyMatch(x -> !Objects.isNull(x));
  }

  private AssetFilter setupAssetFilter(List<String> ids, List<String> hashes, List<AssetStatus> statuses, List<String> issuers,
                                 List<String> validators, String uploadTr, String statusTr, Integer limit, Integer offset) {
    AssetFilter filterParams = new AssetFilter();
    filterParams.setIds(ids);
    filterParams.setHashes(hashes);
    filterParams.setStatuses(Objects.requireNonNullElseGet(statuses, () -> List.of(AssetStatus.ACTIVE)));
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