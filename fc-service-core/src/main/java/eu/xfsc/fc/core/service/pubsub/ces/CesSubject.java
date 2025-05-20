package eu.xfsc.fc.core.service.pubsub.ces;

import com.fasterxml.jackson.annotation.JsonProperty;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.ToString
public class CesSubject {
	
	private String type;
	private String id;
	@JsonProperty("gx:integrity")
	private String gxIntegrity;
	@JsonProperty("gx:integrityNormalization")
	private String gxIntegrityNormalization;
	@JsonProperty("gx:version")
	private String gxVersion;
	@JsonProperty("gx:type")
	private String gxType;

}

