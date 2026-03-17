package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import eu.xfsc.fc.api.generated.model.Participant;
import eu.xfsc.fc.core.util.HashUtils;

@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.Getter
@lombok.Setter
public class ParticipantMetaData extends Participant {

  @JsonIgnore
  private String assetHash;

  public ParticipantMetaData(String id, String participantName, String participantPublicKey, String asset) {
    super(id, participantName, participantPublicKey, asset);
    this.assetHash = HashUtils.calculateSha256AsHex(asset);
  }

  public ParticipantMetaData(String id, String participantName, String participantPublicKey, String asset, String assetHash) {
    super(id, participantName, participantPublicKey, asset);
    this.assetHash = assetHash;
  }
}
