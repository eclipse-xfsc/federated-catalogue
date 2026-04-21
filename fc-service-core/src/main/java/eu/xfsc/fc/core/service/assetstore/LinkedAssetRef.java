package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.core.pojo.AssetType;

/**
 * Projection returned by {@link AssetStore#findLink}: the IRI of the linked asset
 * and the asset type of the queried asset (not the linked one).
 */
public record LinkedAssetRef(String linkedIri, AssetType ownType) {}
