package eu.xfsc.fc.core.dao.cestracker;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;

public final class CesTrackerMapper {

  private CesTrackerMapper() {
  }

  public static CesTracking toTracking(CesTracker entity) {
    if (entity == null) {
      return null;
    }
    return new CesTracking(
        entity.getCesId(),
        entity.getEvent(),
        entity.getCreatedAt().toInstant(ZoneOffset.UTC),
        entity.getCredProcessed(),
        entity.getCredId(),
        entity.getError());
  }

  public static CesTracker toEntity(CesTracking tracking) {
    if (tracking == null) {
      return null;
    }
    CesTracker entity = new CesTracker();
    entity.setCesId(tracking.getCesId());
    entity.setEvent(tracking.getEvent());
    entity.setCreatedAt(LocalDateTime.ofInstant(tracking.getCreatedAt(), ZoneOffset.UTC));
    entity.setCredProcessed(tracking.getCredProcessed());
    entity.setCredId(tracking.getCredId());
    entity.setError(tracking.getError());
    return entity;
  }
}
