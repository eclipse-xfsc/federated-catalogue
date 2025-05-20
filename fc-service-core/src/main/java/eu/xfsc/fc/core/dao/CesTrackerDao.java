package eu.xfsc.fc.core.dao;

import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;

public interface CesTrackerDao {

    void insert(CesTracking event);
    CesTracking select(String cesId);
    CesTracking selectLatest();
	
}
