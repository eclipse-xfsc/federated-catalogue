package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.core.pojo.AssetMetadata;

/**
 * Return type for {@link AssetUploadService#processUpload}. Discriminates between a new asset
 * creation and an enrichment of an existing non-RDF asset with RDF metadata.
 */
public sealed interface UploadResult permits UploadResult.AssetCreated, UploadResult.AssetEnriched {

    record AssetCreated(AssetMetadata metadata) implements UploadResult {}

    record AssetEnriched(AssetEnrichmentResponse response) implements UploadResult {}
}
