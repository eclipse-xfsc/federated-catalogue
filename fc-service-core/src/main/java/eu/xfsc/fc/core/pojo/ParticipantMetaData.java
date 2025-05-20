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
  private String sdHash;

  public ParticipantMetaData(String id, String participantName, String participantPublicKey, String selfDescription) {
    super(id, participantName, participantPublicKey, selfDescription);
    this.sdHash = HashUtils.calculateSha256AsHex(selfDescription);
  }

  public ParticipantMetaData(String id, String participantName, String participantPublicKey, String selfDescription, String sdHash) {
    super(id, participantName, participantPublicKey, selfDescription);
    this.sdHash = sdHash;
  }
}
