package eu.xfsc.fc.client;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetResult;

public class AssetClient extends ServiceClient {

    public AssetClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public AssetClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public List<AssetResult> getAssets(Instant uploadStart, Instant uploadEnd, Instant statusStart, Instant statusEnd,
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

        return doGet("/assets", Map.of(), queryParams, List.class);
    }

    public Asset addAsset(String asset) {
        return doPost("/assets", asset, Map.of(), Map.of(), Asset.class);
    }

    public Asset getAsset(String id) {
        Map<String, Object> pathParams = Map.of("id", id);
        return doGet("/assets/{id}", pathParams, Map.of(), Asset.class);
    }

    /**
     * Delete an asset by its content hash.
     *
     * <p>Unlike {@link #getAsset(String)} which uses the asset's IRI, this method and
     * {@link #revokeAsset(String)} require the SHA-256 content hash. Hash-based targeting
     * guarantees unambiguous single-row deletion — see ADR 7, Delete Operation Exception.</p>
     *
     * @param assetHash the SHA-256 content hash of the asset to delete
     */
    public void deleteAsset(String assetHash) {
        Map<String, Object> pathParams = Map.of("asset_hash", assetHash);
        doDelete("/assets/{asset_hash}", pathParams, Map.of(), Void.class);
    }

    /**
     * Revoke an asset by its content hash.
     *
     * <p>Like {@link #deleteAsset(String)}, this method uses the SHA-256 content hash
     * rather than the IRI. See ADR 7 — Delete Operation Exception.</p>
     *
     * @param assetHash the SHA-256 content hash of the asset to revoke
     */
    public void revokeAsset(String assetHash) {
        Map<String, Object> pathParams = Map.of("asset_hash", assetHash);
        doPost("/assets/{asset_hash}/revoke", null, pathParams, Map.of(), Void.class);
    }

    public AssetResult getAssetByHash(String hash, boolean withMeta, boolean withContent) {
        List<AssetResult> assetList = getAssets(null, null, null, null, null, null, null, null, List.of(hash), withMeta, withContent, null, null);
        return assetList.isEmpty() ? null: assetList.getFirst();
    }
    
    public AssetResult getAssetById(String id) {
        List<AssetResult> assetList = getAssets(null, null, null, null, null, null, null, List.of(id), null, true, true, null, null);
        return assetList.isEmpty() ? null: assetList.getFirst();
    }
   
    public List<AssetResult> getAssetsByIds(List<String> ids) {
        return getAssets(null, null, null, null, null, null, null, ids, null, true, true, null, null);
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
