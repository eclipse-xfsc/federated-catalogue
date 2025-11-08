package eu.xfsc.fc.core.service.graphdb;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;

//@Slf4j
@Component
@ConditionalOnProperty(value = "graphstore.impl", havingValue = "dummy")
public class DummyGraphStore implements GraphStore {

    @Override
    public void addClaims(List<SdClaim> sdClaimList, String credentialSubject) {
        // Dummy implementation
    }

    @Override
    public void deleteClaims(String credentialSubject) {
        // Dummy implementation
    }

    @Override
    public PaginatedResults<Map<String, Object>> queryData(GraphQuery sdQuery) {
        // Dummy implementation
        return new PaginatedResults<Map<String, Object>>(Collections.emptyList());
    }

}
