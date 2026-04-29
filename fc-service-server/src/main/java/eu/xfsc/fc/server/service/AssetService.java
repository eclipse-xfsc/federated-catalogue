package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetResult;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.AssetVersion;
import eu.xfsc.fc.api.generated.model.AssetVersionList;
import eu.xfsc.fc.api.generated.model.Assets;
import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceCredentials;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.api.generated.model.StoredValidationResult;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.AssetType;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.assetstore.LinkedAssetRef;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.server.config.AssetProperties;
import eu.xfsc.fc.server.generated.controller.AssetsApiDelegate;
import eu.xfsc.fc.server.util.OffsetBasedPageRequest;
import eu.xfsc.fc.server.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static eu.xfsc.fc.server.util.AssetHelper.parseTimeRange;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.AssetsApiDelegate} interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService implements AssetsApiDelegate {

  private static final int DEFAULT_VERSION_PAGE_SIZE = 20;

  private final VerificationService verificationService;
  private final AssetStore assetStorePublisher;
  private final HttpServletRequest httpServletRequest;
  private final AssetUploadService assetUploadService;
  @Qualifier("assetFileStore") private final FileStore assetFileStore;
  private final AssetProperties assetProperties;
  private final ValidationResultStore validationResultStore;
  private final ProvenanceService provenanceService;

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
                new AssetResult(asset, asset.getContentAccessor() != null
                    ? asset.getContentAccessor().getContentAsString() : null)).collect(Collectors.toList());
        } else {
            results = assets.getResults().stream().map((AssetMetadata asset) ->
                new AssetResult(asset, null)).collect(Collectors.toList());
        }
    } else if (withContent) {
        results = assets.getResults().stream().map((AssetMetadata asset) ->
            new AssetResult(null, asset.getContentAccessor() != null
                ? asset.getContentAccessor().getContentAsString() : null)).collect(Collectors.toList());
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
  public ResponseEntity<Asset> readAssetById(String id, Integer version) {
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    AssetMetadata assetMetadata = version != null
        ? assetStorePublisher.getByIdAndVersion(decodedId, version)
        : assetStorePublisher.getById(decodedId);

    ContentAccessor content = assetMetadata.getContentAccessor();
    if (content != null) {
      // RDF asset: embed raw JSON-LD in rawContent field so all paths return AssetMetadata.
      assetMetadata.setRawContent(content.getContentAsString());
    }

    populateLinkFields(decodedId, assetMetadata);
    log.debug("readAssetById; returning metadata for id: {}", decodedId);
    return ResponseEntity.ok(assetMetadata);
  }

  /**
   * Populate link fields ({@code humanReadableId}, {@code machineReadableId}) on the given metadata.
   *
   * @param id            asset IRI
   * @param assetMetadata metadata object to populate in place
   */
  private void populateLinkFields(String id, AssetMetadata assetMetadata) {
    assetStorePublisher.findLink(id).ifPresent(ref -> {
      if (ref.ownType() == AssetType.MACHINE_READABLE) {
        assetMetadata.setHumanReadableId(ref.linkedIri());
      } else {
        assetMetadata.setMachineReadableId(ref.linkedIri());
      }
    });
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
    AssetMetadata assetMetadata = verifyAndStore(body, null, null);
    log.debug("addAsset.exit; returning asset with id: {}", assetMetadata.getId());
    String encodedId = UriUtils.encodePathSegment(assetMetadata.getId(), StandardCharsets.UTF_8);
    return ResponseEntity.created(URI.create("/assets/" + encodedId)).body(assetMetadata);
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

    log.debug("revokeAsset.exit; updated asset by hash: {}", assetHash);
    return new ResponseEntity<>(assetMetadata, HttpStatus.OK);
  }

  /**
   * Service method for PUT /assets/{id} : Update an existing asset, creating a new Envers revision.
   *
   * @param id            IRI of the asset (required)
   * @param body          The new asset content (required)
   * @param changeComment Optional change note for this version (optional)
   * @return The updated asset (status code 200)
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ResponseEntity<Asset> updateAsset(String id, String body, String changeComment) {
    log.debug("updateAsset.enter; id: {}, changeComment: {}", id, changeComment);
    AssetMetadata assetMetadata = verifyAndStore(body, changeComment, id);
    log.debug("updateAsset.exit; returning asset with id: {}", id);
    return ResponseEntity.ok(assetMetadata);
  }

  /**
   * Service method for GET /assets/{id}/versions : Get the version history of an asset.
   *
   * @param id   IRI of the asset (required)
   * @param page 0-based page index (optional, default 0)
   * @param size Page size (optional, default 20)
   * @return Paginated version list (status code 200)
   */
  @Override
  public ResponseEntity<AssetVersionList> readAssetVersions(String id, Integer page, Integer size) {
    log.debug("readAssetVersions.enter; id: {}, page: {}, size: {}", id, page, size);
    int pageNum = page != null ? page : 0;
    int pageSize = size != null ? size : DEFAULT_VERSION_PAGE_SIZE;

    PaginatedResults<AssetRecord> results = assetStorePublisher.getVersionHistoryPage(id, pageNum, pageSize);

    int latestVersion = (int) results.getTotalCount();
    List<AssetVersion> versionItems = results.getResults().stream()
        .map(this::toAssetVersion)
        .toList();

    AssetVersionList versionList = new AssetVersionList();
    versionList.setId(id);
    versionList.setTotal(latestVersion);
    versionList.setVersions(versionItems);

    log.debug("readAssetVersions.exit; returning {} versions, total: {}", versionItems.size(), latestVersion);
    return ResponseEntity.ok(versionList);
  }

  /**
   * Service method for POST /assets/{id}/versions/{version}/revoke : Revoke a specific version.
   *
   * <p>Only the current (latest) version can be revoked. Revoking a historical version returns 409.</p>
   *
   * @param id      IRI of the asset (required)
   * @param version 1-based version ordinal to revoke (required)
   * @return The revoked version metadata (status code 200)
   */
  @Override
  @Transactional
  public ResponseEntity<AssetVersion> revokeAssetVersion(String id, Integer version) {
    log.debug("revokeAssetVersion.enter; id: {}, version: {}", id, version);

    // Load snapshot first (throws 404 if version unknown) so we can auth-check before disclosing state.
    AssetRecord snapshot = assetStorePublisher.getByIdAndVersion(id, version);
    checkParticipantAccess(snapshot.getIssuer());

    int totalVersions = assetStorePublisher.getVersionCount(id);
    if (!version.equals(totalVersions)) {
      throw new ConflictException(
          "Only the current version (" + totalVersions + ") can be revoked. Requested: " + version);
    }

    // changeLifeCycleStatus throws ConflictException (409) if the row is already non-ACTIVE (e.g. REVOKED).
    assetStorePublisher.changeLifeCycleStatus(snapshot.getAssetHash(), AssetStatus.REVOKED);

    AssetVersion av = new AssetVersion();
    av.setVersion(version);
    av.setCreatedAt(snapshot.getUploadDatetime());
    av.setCreatedBy(snapshot.getIssuer());
    av.setStatus(AssetStatus.REVOKED);
    av.setIsCurrent(true);
    av.setChangeComment(snapshot.getChangeComment());

    log.debug("revokeAssetVersion.exit; revoked version {} of asset {}", version, id);
    return ResponseEntity.ok(av);
  }

  /**
   * POST /assets/{id}/human-readable — upload and link a human-readable representation.
   * Returns 409 if a human-readable asset is already linked; use PUT to replace it.
   *
   * @param id   URL-encoded IRI of the machine-readable parent asset
   * @param file the human-readable document
   * @return 201 Created with the new human-readable asset metadata
   */
  @Override
  @Transactional
  public ResponseEntity<Asset> uploadHumanReadable(String id, MultipartFile file) {
    if (file == null) {
      throw new ClientException("No file was provided in the upload request");
    }
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    log.debug("uploadHumanReadable.enter; parentId: {}", decodedId);

    final AssetMetadata parentMeta = assetStorePublisher.getById(decodedId);
    checkParticipantAccess(parentMeta.getIssuer());

    final boolean hrAlreadyLinked = assetStorePublisher.findLink(decodedId)
        .filter(ref -> ref.ownType() == AssetType.MACHINE_READABLE)
        .isPresent();
    if (hrAlreadyLinked) {
      throw new ConflictException("A human-readable asset is already linked to " + decodedId + ". Use PUT to replace it.");
    }

    final AssetMetadata hrMetadata = storeAndLinkHumanReadable(decodedId, file, null);
    log.debug("uploadHumanReadable.exit; linked {} -> {}", decodedId, hrMetadata.getId());

    final String encodedId = UriUtils.encodePathSegment(hrMetadata.getId(), StandardCharsets.UTF_8);
    return ResponseEntity
        .created(URI.create("/assets/" + encodedId))
        .body(hrMetadata);
  }

  /**
   * PUT /assets/{id}/human-readable — replace the existing human-readable representation.
   * Returns 404 if no human-readable asset is currently linked; use POST to create the initial link.
   *
   * @param id   URL-encoded IRI of the machine-readable parent asset
   * @param file the replacement human-readable document
   * @return 200 OK with the updated human-readable asset metadata
   */
  @Override
  @Transactional
  public ResponseEntity<Asset> replaceHumanReadable(String id, MultipartFile file) {
    if (file == null) {
      throw new ClientException("No file was provided in the upload request");
    }
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    log.debug("replaceHumanReadable.enter; parentId: {}", decodedId);

    final AssetMetadata parentMeta = assetStorePublisher.getById(decodedId);
    checkParticipantAccess(parentMeta.getIssuer());

    final String existingHrId = assetStorePublisher.findLink(decodedId)
        .filter(ref -> ref.ownType() == AssetType.MACHINE_READABLE)
        .map(LinkedAssetRef::linkedIri)
        .orElseThrow(() -> new NotFoundException(
            "No human-readable asset is linked to " + decodedId + ". Use POST to create the initial link."));

    final AssetMetadata hrMetadata = storeAndLinkHumanReadable(decodedId, file, existingHrId);
    log.debug("replaceHumanReadable.exit; replaced {} -> {}", decodedId, hrMetadata.getId());

    return ResponseEntity.ok(hrMetadata);
  }

  private AssetMetadata storeAndLinkHumanReadable(String decodedId, MultipartFile file, String existingHrId) {
    final String contentType = file.getContentType() != null
        ? file.getContentType()
        : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    validateHumanReadableContentType(contentType);

    final byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new ServerException("Failed to read uploaded file", ex);
    }

    final AssetMetadata hrMetadata = assetUploadService.processUpload(
        content, contentType, StringUtils.sanitizeFilename(file.getOriginalFilename()), existingHrId);
    assetStorePublisher.linkAssets(decodedId, hrMetadata.getId());
    try {
      assetStorePublisher.writeAssetLinkTriples(decodedId, hrMetadata.getId());
    } catch (Exception ex) {
      log.warn("storeAndLinkHumanReadable; graph triple write failed (non-fatal)", ex);
    }
    return hrMetadata;
  }

  /**
   * GET /assets/{id}/human-readable — return the linked human-readable document.
   *
   * @param id URL-decoded IRI of the machine-readable asset
   * @return 200 with binary content and correct Content-Type, or 404 if no link exists
   */
  @Override
  public ResponseEntity<Resource> getHumanReadable(String id) {
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    log.debug("getHumanReadable.enter; id: {}", decodedId);

    final LinkedAssetRef link = assetStorePublisher.findLink(decodedId)
        .filter(ref -> ref.ownType() == AssetType.MACHINE_READABLE)
        .orElseThrow(() -> new NotFoundException(
            String.format("No human-readable representation linked to asset '%s'", decodedId)));

    return streamAssetContent(link.linkedIri());
  }

  /**
   * GET /assets/{id}/machine-readable — return the linked machine-readable asset.
   *
   * @param id URL-decoded IRI of the human-readable asset
   * @return 200 with binary content and correct Content-Type, or 404 if no link exists
   */
  @Override
  public ResponseEntity<Resource> getMachineReadable(String id) {
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    log.debug("getMachineReadable.enter; id: {}", decodedId);

    final LinkedAssetRef link = assetStorePublisher.findLink(decodedId)
        .filter(ref -> ref.ownType() == AssetType.HUMAN_READABLE)
        .orElseThrow(() -> new NotFoundException(
            String.format("No machine-readable asset linked to human-readable asset '%s'", decodedId)));

    return streamAssetContent(link.linkedIri());
  }

  /**
   * Load asset file content by IRI from the FileStore and stream it with the stored MIME type.
   *
   * @param assetIri IRI of the asset to stream
   * @return response entity with binary body and Content-Type header
   */
  private ResponseEntity<Resource> streamAssetContent(String assetIri) {
    final AssetMetadata meta = assetStorePublisher.getById(assetIri);

    final ContentAccessor content;
    try {
      content = assetFileStore.readFile(meta.getAssetHash());
    } catch (IOException ex) {
      throw new ServerException("Failed to read asset file for IRI: " + assetIri, ex);
    }

    final MediaType mediaType = resolveMediaType(meta.getContentType(), assetIri);
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(mediaType);
    return ResponseEntity.ok().headers(headers).body(new InputStreamResource(content.getContentAsStream()));
  }

  private MediaType resolveMediaType(String contentType, String assetIri) {
    if (contentType == null) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (IllegalArgumentException ex) {
      log.warn("streamAssetContent; unparseable content-type '{}' for asset {}, using octet-stream",
          contentType, assetIri);
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  /**
   * Validate that the given MIME type is an accepted human-readable content type.
   *
   * @param contentType the MIME type to validate
   * @throws ClientException if the type is not in {@code federated-catalogue.assets.hr-content-types}
   */
  void validateHumanReadableContentType(String contentType) {
    if (contentType == null || !assetProperties.getHumanReadableContentTypes().contains(contentType)) {
      throw new ClientException(String.format(
          "Unsupported content type '%s'. Accepted types: %s", contentType,
          String.join(", ", new TreeSet<>(assetProperties.getHumanReadableContentTypes()))));
    }
  }


  /**
   * Verify a credential body, check participant access, and store it.
   *
   * @param body          Raw credential content.
   * @param changeComment Optional change note (null for first upload).
   * @param expectedId    If non-null, the credential IRI must match; throws 400 otherwise.
   * @return Stored asset metadata with warnings populated.
   */
  private AssetMetadata verifyAndStore(String body, String changeComment, String expectedId) {
    try {
      String contentType = httpServletRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      ContentAccessorDirect contentAccessor = new ContentAccessorDirect(body, contentType);

      CredentialVerificationResult verificationResult = verificationService.verifyCredential(contentAccessor);

      if (expectedId != null && !expectedId.equals(verificationResult.getId())) {
        throw new ClientException(
            "Path id '" + expectedId + "' does not match credential IRI '" + verificationResult.getId() + "'");
      }

      AssetMetadata assetMetadata = new AssetMetadata(verificationResult.getId(), verificationResult.getIssuer(),
          verificationResult.getValidators(), contentAccessor);
      assetMetadata.setChangeComment(changeComment);
      checkParticipantAccess(assetMetadata.getIssuer());
      assetStorePublisher.storeCredential(assetMetadata, verificationResult);

      if (verificationResult.getWarnings() != null && !verificationResult.getWarnings().isEmpty()) {
        assetMetadata.setWarnings(verificationResult.getWarnings());
      }
      return assetMetadata;
    } catch (ValidationException exception) {
      throw new ClientException("Asset credential isn't parsed due to: " + exception.getMessage());
    }
  }

  /**
   * Validates and stores a provenance credential for the given asset version.
   *
   * @param id      asset IRI
   * @param body    raw VC content
   * @param version 1-based version ordinal, or {@code null} for the current version
   */
  @Override
  public ResponseEntity<ProvenanceCredential> addProvenanceCredential(String id, String body, Integer version) {
    log.debug("addProvenanceCredential; id={}, version={}", id, version);
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    final String format = httpServletRequest.getHeader(HttpHeaders.CONTENT_TYPE);

    // Participant-scoping: the caller must own the asset
    checkParticipantAccess(assetStorePublisher.getById(decodedId).getIssuer());

    ProvenanceCredential result = provenanceService.add(decodedId, version, body, format);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /**
   * Returns paginated provenance credentials for the given asset.
   *
   * @param id      asset IRI
   * @param version 1-based version ordinal to filter, or {@code null} for all versions
   * @param page    0-based page index (optional)
   * @param size    page size (optional)
   */
  @Override
  public ResponseEntity<ProvenanceCredentials> listProvenanceCredentials(
      String id, Integer version, Integer page, Integer size) {
    log.debug("listProvenanceCredentials; id={}, version={}, page={}, size={}", id, version, page, size);
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    int pageNum = page != null ? page : 0;
    int pageSize = size != null ? size : DEFAULT_VERSION_PAGE_SIZE;
    PageRequest pageable = PageRequest.of(pageNum, pageSize);
    ProvenanceCredentials result = provenanceService.list(decodedId, version, pageable);
    return ResponseEntity.ok(result);
  }

  /**
   * Returns a single provenance credential belonging to the given asset.
   *
   * @param id           asset IRI
   * @param credentialId credential identifier
   */
  @Override
  public ResponseEntity<ProvenanceCredential> getProvenanceCredential(String id, String credentialId) {
    log.debug("getProvenanceCredential; id={}, credentialId={}", id, credentialId);
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    final String decodedCredentialId = UriUtils.decode(credentialId, StandardCharsets.UTF_8);
    ProvenanceCredential result = provenanceService.get(decodedId, decodedCredentialId);
    return ResponseEntity.ok(result);
  }

  /**
   * Verifies a single provenance credential and persists the result.
   *
   * @param id           asset IRI
   * @param credentialId credential identifier
   */
  @Override
  public ResponseEntity<ProvenanceVerificationResult> verifyProvenanceCredential(
      String id, String credentialId) {
    log.debug("verifyProvenanceCredential; id={}, credentialId={}", id, credentialId);
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    final String decodedCredentialId = UriUtils.decode(credentialId, StandardCharsets.UTF_8);
    ProvenanceVerificationResult result = provenanceService.verifyOne(decodedId, decodedCredentialId);
    return ResponseEntity.ok(result);
  }

  /**
   * Verifies all provenance credentials for the given asset and persists the results.
   *
   * @param id      asset IRI
   * @param version 1-based version ordinal to scope, or {@code null} for all versions
   */
  @Override
  public ResponseEntity<ProvenanceVerificationResult> verifyAllProvenanceCredentials(
      String id, Integer version) {
    log.debug("verifyAllProvenanceCredentials; id={}, version={}", id, version);
    final String decodedId = UriUtils.decode(id, StandardCharsets.UTF_8);
    ProvenanceVerificationResult result = provenanceService.verifyAll(decodedId, version);
    return ResponseEntity.ok(result);
  }

  private AssetVersion toAssetVersion(AssetRecord record) {
    AssetVersion av = new AssetVersion();
    av.setVersion(record.getVersion());
    av.setCreatedAt(record.getUploadDatetime());
    av.setCreatedBy(record.getIssuer());
    av.setStatus(record.getStatus());
    av.setIsCurrent(record.getIsCurrent());
    av.setChangeComment(record.getChangeComment());
    return av;
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

  /**
   * GET /assets/{id}/validations — returns stored validation results for the asset.
   *
   * @param id     asset subject IRI
   * @param offset number of results to skip (default 0)
   * @param limit  maximum number of results to return (default 100)
   */
  @Override
  public ResponseEntity<List<StoredValidationResult>> getAssetValidations(
      String id, Integer offset, Integer limit) {
    log.debug("getAssetValidations; id={}, offset={}, limit={}", id, offset, limit);
    if (!assetStorePublisher.existsById(id)) {
      throw new NotFoundException("no active asset found for id %s".formatted(id));
    }
    int effectiveOffset = offset != null ? offset : 0;
    int effectiveLimit = limit != null ? limit : 100;
    List<StoredValidationResult> results =
        validationResultStore.getByAssetId(id, new OffsetBasedPageRequest(effectiveOffset, effectiveLimit))
            .stream()
            .map(ValidationResultMapper::toDto)
            .toList();
    return ResponseEntity.ok(results);
  }
}
