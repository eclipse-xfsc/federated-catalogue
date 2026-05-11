package eu.xfsc.fc.core.service.assetstore;

/**
 * Published by {@link AssetStoreImpl} when an asset row is successfully deleted.
 * Listeners should use {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}
 * so cleanup runs inside the same transaction as the asset deletion.
 *
 * @param assetId the subject IRI of the deleted asset
 */
public record AssetDeletedEvent(String assetId) {
}
