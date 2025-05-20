package eu.xfsc.fc.core.pojo;

import java.util.Map;

import eu.xfsc.fc.api.generated.model.QueryLanguage;

/**
 * POJO Class for holding a Cypher Query.
 */
@lombok.AllArgsConstructor
@lombok.EqualsAndHashCode
@lombok.Getter
@lombok.ToString
public class GraphQuery {
    
    public static final int QUERY_TIMEOUT = 5;
	
	private final String query;
	private Map<String, Object> params;
	private QueryLanguage queryLanguage;
    private int timeout;
    private boolean withTotalCount;

    public GraphQuery(String query, Map<String, Object> params) {
      this(query, params, QueryLanguage.OPENCYPHER, QUERY_TIMEOUT, true);  
    }
    
}
