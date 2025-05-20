package eu.xfsc.fc.core.dao;

import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.ParticipantMetaData;

import java.util.Optional;

/**
 * Participant repository interface.
 */
public interface ParticipantDao {
  /**
   * Create Participant.
   *
   * @param participant Participant entity.
   * @return Created participant.
   */
  ParticipantMetaData create(ParticipantMetaData participant);

  /**
   * Get an Optional Participant by id.
   *
   * @param participantId Participant id.
   * @return Optional Participant.
   */
  Optional<ParticipantMetaData> select(String participantId);

  /**
   * Get list of users by participant id.
   *
   * @param participantId Participant id.
   * @return Optional list of users.
   */
  Optional<PaginatedResults<UserProfile>> selectUsers(String participantId, Integer offset, Integer limit);

  /**
   * Remove the Participant with the given id.
   *
   * @param participantId Participant id.
   * @return Removed optional participant.
   */
  Optional<ParticipantMetaData> delete(String participantId);

  /**
   * Update the Participant with the given id.
   *
   * @param participantId Participant id.
   * @param participant Participant model.
   * @return Updated optional participant.
   */
  Optional<ParticipantMetaData> update(String participantId, ParticipantMetaData participant);

  /**
   * Get participants by filtered params.
   *
   * @param offset How many items to skip.
   * @param limit The maximum number of items to return.
   * @return List of filtered participants.
   */
  PaginatedResults<ParticipantMetaData> search(Integer offset, Integer limit);
}
