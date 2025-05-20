package eu.xfsc.fc.client;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.SelfDescription;
import eu.xfsc.fc.api.generated.model.SelfDescriptionResult;

public class SelfDescriptionClient extends ServiceClient {

    public SelfDescriptionClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public SelfDescriptionClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public List<SelfDescriptionResult> getSelfDescriptions(Instant uploadStart, Instant uploadEnd, Instant statusStart, Instant statusEnd,
                                                           Collection<String> issuers, Collection<String> validators, Collection<String> statuses,
                                                           Collection<String> ids, Collection<String> hashes, Boolean withMeta, Boolean withContent,
                                                           Integer offset, Integer limit) {
        Map<String, Object> queryParams = new HashMap<>();
        addQuery(queryParams, "upload-timerange", addQueryTimeRange(uploadStart, uploadEnd));
        addQuery(queryParams, "status-timerange", addQueryTimeRange(statusStart, statusEnd));
        addQuery(queryParams, "issuers", addQueryList(issuers));
        addQuery(queryParams, "validators", addQueryList(validators));
        addQuery(queryParams, "statuses", addQueryList(statuses));
        addQuery(queryParams, "ids", addQueryList(ids));
        addQuery(queryParams, "hashes", addQueryList(hashes));
        addQuery(queryParams, "withMeta", withMeta);
        addQuery(queryParams, "withContent", withContent);
        addQuery(queryParams, "offset", offset);
        addQuery(queryParams, "limit", limit);

        return doGet("/self-descriptions", Map.of(), queryParams, List.class);
    }

    public SelfDescription addSefDescription(String selfDescription) {
        return doPost("/self-descriptions", selfDescription, Map.of(), Map.of(), SelfDescription.class);
    }

    public SelfDescription getSelfDescription(String hash) {
        Map<String, Object> pathParams = Map.of("self_description_hash", hash);
        return doGet("/self-descriptions/{self_description_hash}", pathParams, Map.of(), SelfDescription.class);
    }
    public void deleteSelfDescription(String hash) {
        Map<String, Object> pathParams = Map.of("self_description_hash", hash);
        doDelete("/self-descriptions/{self_description_hash}", pathParams, Map.of(), Void.class);
    }

    public void revokeSelfDescription(String hash) {
        Map<String, Object> pathParams = Map.of("self_description_hash", hash);
        doPost("/self-descriptions/{self_description_hash}/revoke", null, pathParams, Map.of(), Void.class);
    }

    public SelfDescriptionResult getSelfDescriptionByHash(String hash, boolean withMeta, boolean withContent) {
        List<SelfDescriptionResult> sds = getSelfDescriptions(null, null, null, null, null, null, null, null, List.of(hash), withMeta, withContent, null, null);
        return sds.isEmpty() ? null: sds.get(0);
    }
    
    public SelfDescriptionResult getSelfDescriptionById(String id) {
        List<SelfDescriptionResult> sds = getSelfDescriptions(null, null, null, null, null, null, null, List.of(id), null, true, true, null, null);
        return sds.isEmpty() ? null: sds.get(0);
    }
   
    public List<SelfDescriptionResult> getSelfDescriptionsByIds(List<String> ids) {
        return getSelfDescriptions(null, null, null, null, null, null, null, ids, null, true, true, null, null);
    }

    private void addQuery(Map<String, Object> params, String param, Object value) {
        if (value != null) {
            params.put(param, value);
        }
    }

    private String addQueryList(Collection<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(",", list);
    }

    private String addQueryTimeRange(Instant start, Instant end) {
        if (start == null) {
            if (end == null) {
                return null;
            }
            start = Instant.ofEpochMilli(0);
        } else if (end == null) {
            end = Instant.now().plusSeconds(86400);
        }
        return start.toString() + "/" + end.toEpochMilli();
    }
}
