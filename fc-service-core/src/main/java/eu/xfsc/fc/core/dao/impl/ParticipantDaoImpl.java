package eu.xfsc.fc.core.dao.impl;

import static eu.xfsc.fc.core.dao.impl.UserDaoImpl.toUserProfile;
import static eu.xfsc.fc.core.util.KeycloakUtils.getErrorMessage;

import eu.xfsc.fc.api.generated.model.Participant;
import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.core.dao.ParticipantDao;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.ParticipantMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementation of the {@link ParticipantDao} interface.
 */
@Slf4j
@Component
public class ParticipantDaoImpl implements ParticipantDao {
  private static final String ATR_NAME = "name";
  private static final String ATR_PUBLIC_KEY  = "publicKey";
  private static final String ATR_SD_HASH = "sdHash";

  @Value("${keycloak.realm}")
  private String realm;
  @Value("${keycloak.resource}")
  private String resourceId;
  @Autowired
  private Keycloak keycloak;

  /**
   * Create Participant.
   *
   * @param participant Participant entity.
   * @return Created participant.
   */
  @Override
  public ParticipantMetaData create(ParticipantMetaData participant) {

    GroupsResource instance = keycloak.realm(realm).groups();
    GroupRepresentation groupRepo = toGroupRepo(participant);
    Response response = instance.add(groupRepo);
    if (response.getStatus() != HttpStatus.SC_CREATED) {
      String message = getErrorMessage(response);
      log.info("create.error; status {}:{}, {}", response.getStatus(), response.getStatusInfo(), message);
      throw new ConflictException(message);
    }
    return participant;
  }

  /**
   * Get an Optional Participant by id.
   *
   * @param participantId Participant id.
   * @return Optional Participant.
   */
  @Override
  public Optional<ParticipantMetaData> select(String participantId) {

    GroupsResource instance = keycloak.realm(realm).groups();
    List<GroupRepresentation> groups = instance.groups(participantId, 0, 1, false);
    if (groups.size() == 0) {
      return Optional.empty();
    }
    return Optional.of(toParticipantExt(groups.get(0)));
  }

  /**
   * Get list of users by participant id.
   *
   * @param participantId Participant id.
   * @return Optional list of users.
   */
  @Override
  public Optional<PaginatedResults<UserProfile>> selectUsers(String participantId, Integer offset, Integer limit) {

    GroupsResource instance = keycloak.realm(realm).groups();
    List<GroupRepresentation> groups = instance.groups(participantId, 0, 1, false);
    if (groups.size() == 0) {
      return Optional.empty();
    }
    GroupRepresentation groupRepo = groups.get(0);

    List<UserRepresentation> users;
    List<UserProfile> profiles = new ArrayList<>();

    GroupResource group = instance.group(groupRepo.getId());
    ClientRepresentation client = keycloak.realm(realm).clients().findByClientId(resourceId).get(0);
    UsersResource usersResource = keycloak.realm(realm).users();
      users = group.members(offset, limit, false);
    users.stream().map(user ->
            toUserProfile(user, usersResource.get(user.getId()).roles().clientLevel(client.getId()).listAll()))
        .forEach(profiles::add);
    return Optional.of(new PaginatedResults<>(profiles));
  }

  /**
   * Remove the Participant with the given id.
   *
   * @param participantId Participant id.
   * @return Removed optional participant.
   */
  @Override
  public Optional<ParticipantMetaData> delete(String participantId) {

    GroupsResource instance = keycloak.realm(realm).groups();
    List<GroupRepresentation> groups = instance.groups(participantId, 0, 1, false);
    if (groups.size() == 0) {
      return Optional.empty();
    }
    GroupRepresentation groupRepo = groups.get(0);

    UsersResource resource = keycloak.realm(realm).users();
    List<UserRepresentation> users;
    do {
      users = instance.group(groupRepo.getId()).members();
      users.stream().forEach(ur -> resource.delete(ur.getId()));
    } while (users.size() > 0);

    instance.group(groupRepo.getId()).remove();
    return Optional.of(toParticipantExt(groupRepo));
  }

  /**
   * Update the Participant with the given id.
   *
   * @param participantId Participant id.
   * @param participant Participant model.
   * @return Updated optional participant.
   */
  @Override
  public Optional<ParticipantMetaData> update(String participantId, ParticipantMetaData participant) {
    GroupsResource instance = keycloak.realm(realm).groups();
    List<GroupRepresentation> groups = instance.groups(participantId, 0, 1, true);
    if (groups.size() == 0) {
      return Optional.empty();
    }
    GroupRepresentation groupRepo = groups.get(0);
    GroupRepresentation updated = toGroupRepo(participant);
    instance.group(groupRepo.getId()).update(updated);
    return Optional.of(toParticipantExt(updated));
  }

  /**
   * Get participants by filtered params.
   *
   * @param offset How many items to skip.
   * @param limit The maximum number of items to return.
   * @return List of filtered participants.
   */
  @Override
  public PaginatedResults<ParticipantMetaData> search(Integer offset, Integer limit) {
    GroupsResource instance = keycloak.realm(realm).groups();
    List<GroupRepresentation> groups = instance.groups(null, offset, limit, false);
    Map<String, Long> counts = instance.count();
    long total = counts.get("count");
    // map groups to ParticipantMetaData, filter out null values
    List<ParticipantMetaData> parts = groups.stream()
      .map(this::toParticipantExt)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    // adjust totals. not strictly true, but better..
    total -= groups.size() - parts.size();
    return new PaginatedResults<>(total, parts);
  }

  /**
   * Map participant to user group representation model.
   *
   * @param participant Participant model.
   * @return User group representation model.
   */
  public static GroupRepresentation toGroupRepo(ParticipantMetaData participant) {
    GroupRepresentation groupRepo = new GroupRepresentation();
    groupRepo.setName(participant.getId());
    groupRepo.singleAttribute(ATR_NAME, participant.getName());
    groupRepo.singleAttribute(ATR_PUBLIC_KEY, participant.getPublicKey());
    //String sdHash = HashUtils.calculateSha256AsHex(participant.getSelfDescription()); 
    groupRepo.singleAttribute(ATR_SD_HASH, participant.getSdHash()); // sdHash);
    return groupRepo;
  }

  /**
   * Map group representation to participant model.
   *
   * @param groupRepo Group representation model.
   * @return ParticipantMetaData model.
   */
  private ParticipantMetaData toParticipantExt(GroupRepresentation groupRepo) {
    Map<String, List<String>> attributes = groupRepo.getAttributes();
    // check for required attributes..
    if (emptyAttributes(attributes.get(ATR_NAME)) || emptyAttributes(attributes.get(ATR_PUBLIC_KEY)) ||
        emptyAttributes(attributes.get(ATR_SD_HASH))) {
      return null; 
    }
    return new ParticipantMetaData(groupRepo.getName(), attributes.get(ATR_NAME).get(0),
        attributes.get(ATR_PUBLIC_KEY).get(0), null, attributes.get(ATR_SD_HASH).get(0));
  }

  private boolean emptyAttributes(List<String> attrs) {
    return attrs == null || attrs.isEmpty();
  }
}
