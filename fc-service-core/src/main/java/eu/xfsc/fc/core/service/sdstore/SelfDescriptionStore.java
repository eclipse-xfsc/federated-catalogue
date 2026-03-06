package eu.xfsc.fc.core.service.sdstore;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;

import java.util.List;

/**
 * A store for storing and retrieving self-description meta data objects.
 *
 * @author hylke
 * @author j_reuter
 */
public interface SelfDescriptionStore {
  /**
   * Fetch a self-description file by its hash value.
   *
   * @param hash The hash value that identifies the self-description meta data.
   * @return The self-description file.
   */
  ContentAccessor getSDFileByHash(String hash);

  /**
   * Fetch a self-description and its meta data by its hash value.
   *
   * @param hash The hash value that identifies the self-description meta data.
   * @return The self-description meta data object with the specified hash value.
   */
  SelfDescriptionMetadata getByHash(String hash);

  /**
   * Fetch all self-descriptions that match the filter parameters.
   *
   * @param filter The filter to match all self-descriptions against.
   * @param withMeta flax indicating the full metaData of the SD should be loaded instead of just the hash.
   * @param withContent flag indicating the content of the SelfDescription should also be returned.
   * @return List of all self-description meta data objects that match the
   *         specified filter.
   */
  PaginatedResults<SelfDescriptionMetadata> getByFilter(SdFilter filter, boolean withMeta, boolean withContent);

  /**
   * Store the given self-description.
   *
   * @param selfDescription       The self-description to store.
   * @param sdVerificationResults The results of the verification of the
   *                              self-description.
   */
  void storeSelfDescription(SelfDescriptionMetadata selfDescription, VerificationResult sdVerificationResults);

  /**
   * Change the life cycle status of the self-description with the given hash.
   *
   * @param hash         The hash of the self-description to work on.
   * @param targetStatus The new status.
   */
  void changeLifeCycleStatus(String hash, SelfDescriptionStatus targetStatus);

  /**
   * Remove the self-description with the given hash from the store.
   *
   * @param hash The hash of the self-description to work on.
   */
  void deleteSelfDescription(String hash);

  /**
   * Invalidate expired Self-descriptions in the store.
   *
   * @return Number of expired Self-descriptions found.
   */
  int invalidateSelfDescriptions();

  /**
   * Get "count" hashes of active self-descriptions, ordered by sdhash, after
   * the given hash. Chunking is done using:
   * <pre>hashtext(sdhash) % chunks = chunkId</pre>
   *
   * @param afterHash The last hash of the previous batch.
   * @param count the number of hashes to retrieve.
   * @param chunks the number of chunks to subdivide hashes into.
   * @param chunkId the 0-based id of the chunk to get.
   * @return the list of hashes coming after the hash "afterHash", ordered by
   * hash.
   */
  List<String> getActiveSdHashes(String afterHash, int count, int chunks, int chunkId);

  /**
   * Store a non-RDF asset. The content is stored in the FileStore and metadata
   * (with content=NULL) is inserted into the database. No verification or graph
   * interaction is performed. If the metadata has no subject ID, one is generated
   * from the configured prefix and the content hash.
   *
   * @param sdMetadata The asset metadata to store.
   * @param originalFilename The original filename from the upload, or null.
   * @return the stored metadata, including the generated subject ID.
   * @throws eu.xfsc.fc.core.exception.ServerException if the file store write fails.
   * @throws eu.xfsc.fc.core.exception.ConflictException if an asset with the same hash already exists.
   */
  SelfDescriptionMetadata storeAsset(SelfDescriptionMetadata sdMetadata, String originalFilename);

  /**
   * Remove all SelfDescriptions from the SelfDescriptionStore.
   */
  void clear();

}
