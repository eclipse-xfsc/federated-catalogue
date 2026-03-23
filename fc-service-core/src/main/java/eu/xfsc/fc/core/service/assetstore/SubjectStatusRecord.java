package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;

public record SubjectStatusRecord(String subjectId, Integer status) {

	AssetStatus getAssetStatus() {
		return status == null ? null : AssetStatus.values()[status];
	}

}
