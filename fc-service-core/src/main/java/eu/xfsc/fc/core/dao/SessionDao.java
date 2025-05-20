package eu.xfsc.fc.core.dao;

import eu.xfsc.fc.api.generated.model.Session;

/**
 * Session repository interface.
 */
public interface SessionDao {
    
    Session select(String id);
    
    void delete(String id);

}
