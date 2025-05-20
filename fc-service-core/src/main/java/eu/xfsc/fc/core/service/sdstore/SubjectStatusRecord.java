package eu.xfsc.fc.core.service.sdstore;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;

public record SubjectStatusRecord(String subjectId, Integer status) {
	
	SelfDescriptionStatus getSdStatus() {
		return status == null ? null : SelfDescriptionStatus.values()[status];
	}

}
