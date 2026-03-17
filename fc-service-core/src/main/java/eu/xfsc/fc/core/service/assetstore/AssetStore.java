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
   * interaction is performed. If the metadata has no subject ID, one is generated
   * from the configured prefix and the content hash.
   *
   * @param assetMetadata The asset metadata to store.
   * @param originalFilename The original filename from the upload, or null.
   * @return the stored metadata, including the generated subject ID.
   * @throws eu.xfsc.fc.core.exception.ServerException if the file store write fails.
   * @throws eu.xfsc.fc.core.exception.ConflictException if an asset with the same hash already exists.
   */
  AssetMetadata storeUnverified(AssetMetadata assetMetadata, String originalFilename);

  /**
   * Remove all assets from the AssetStore.
   */
  void clear();

}
