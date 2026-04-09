package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;

import java.util.List;

/**
 * A store for storing and retrieving asset meta data objects.
 *
 * @author hylke
 * @author j_reuter
 */
public interface AssetStore {
  /**
   * Fetch an asset file by its hash value.
   *
   * @param hash The hash value that identifies the asset meta data.
   * @return The asset file.
   */
  ContentAccessor getFileByHash(String hash);

  /**
   * Fetch an asset and its meta data by its hash value.
   *
   * @param hash The hash value that identifies the asset meta data.
   * @return The asset meta data object with the specified hash value.
   */
  AssetMetadata getByHash(String hash);

  /**
   * Fetch all assets that match the filter parameters.
   *
   * @param filter The filter to match all assets against.
   * @param withMeta flag indicating the full metaData of the asset should be loaded instead of just the hash.
   * @param withContent flag indicating the content of the asset should also be returned.
   * @return List of all asset meta data objects that match the
   *         specified filter.
   */
  PaginatedResults<AssetMetadata> getByFilter(AssetFilter filter, boolean withMeta, boolean withContent);

  /**
   * Store the given credential.
   *
   * @param credential            The credential metadata to store.
   * @param verificationResults   The results of the verification of the
   *                              credential.
   */
  void storeCredential(AssetMetadata credential, CredentialVerificationResult verificationResults);

  /**
   * Change the life cycle status of the asset with the given hash.
   *
   * @param hash         The hash of the asset to work on.
   * @param targetStatus The new status.
   */
  void changeLifeCycleStatus(String hash, AssetStatus targetStatus);

  /**
   * Remove the asset with the given hash from the store.
   *
   * @param hash The hash of the asset to work on.
   */
  void deleteAsset(String hash);

  /**
   * Invalidate expired assets in the store.
   *
   * @return Number of expired assets found.
   */
  int invalidateExpiredAssets();

  /**
   * Get "count" hashes of active assets, ordered by asset_hash, after
   * the given hash. Chunking is done using:
   * <pre>hashtext(asset_hash) % chunks = chunkId</pre>
   *
   * @param afterHash The last hash of the previous batch.
   * @param count the number of hashes to retrieve.
   * @param chunks the number of chunks to subdivide hashes into.
   * @param chunkId the 0-based id of the chunk to get.
   * @return the list of hashes coming after the hash "afterHash", ordered by
   * hash.
   */
  List<String> getActiveAssetHashes(String afterHash, int count, int chunks, int chunkId);

  /**
   * Store a non-RDF asset. The content is stored in the FileStore and metadata
   * (with content=NULL) is inserted into the database. No verification or graph
   * interaction is performed. If the asset ID is not set on the metadata, a UUID
   * URN will be generated automatically via {@link IriGenerator#generateUuidUrn()}.
   *
   * @param assetMetadata The asset metadata to store (ID may be null — a UUID URN
   *                      will be generated).
   * @param originalFilename The original filename from the upload, or null.
   * @return the stored metadata.
   * @throws eu.xfsc.fc.core.exception.ServerException if the file store write fails.
   * @throws eu.xfsc.fc.core.exception.ConflictException if an asset with the same hash already exists.
   */
  AssetMetadata storeUnverified(AssetMetadata assetMetadata, String originalFilename);

  // --- IRI-based methods for public API ---
  // Note: All IRI-based methods resolve to the active asset only.

  /**
   * Fetch an active asset by its IRI (subjectId).
   *
   * @param id The IRI that identifies the asset.
   * @return The asset meta data.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if no asset with the given IRI exists.
   */
  AssetMetadata getById(String id);

  /**
   * Fetch an active asset's file content by its IRI (subjectId).
   *
   * @param id The IRI that identifies the asset.
   * @return The asset file content.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if no asset with the given IRI exists.
   */
  ContentAccessor getFileById(String id);

  /**
   * Remove all assets from the AssetStore.
   */
  void clear();

  /**
   * Returns a paginated page of asset versions in descending order (newest first),
   * plus the total version count.
   *
   * @param id   The IRI (subjectId) of the asset.
   * @param page 0-based page index.
   * @param size Page size.
   * @return Paginated results containing version records.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if asset does not exist.
   */
  PaginatedResults<AssetRecord> getVersionHistoryPage(String id, int page, int size);

  /**
   * Returns a specific version (1-based ordinal) of the asset.
   *
   * @param id      The IRI (subjectId) of the asset.
   * @param version 1-based version ordinal.
   * @return The asset record at that version.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if asset or version does not exist.
   */
  AssetRecord getByIdAndVersion(String id, int version);

  /**
   * Returns the total number of versions of the asset.
   *
   * @param id The IRI (subjectId) of the asset.
   * @return Total version count.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if asset does not exist.
   */
  int getVersionCount(String id);

}
