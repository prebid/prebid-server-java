package org.prebid.server.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.proto.response.HttpRefreshResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**<p>
 * Service that periodically calls external HTTP API for stored request updates.
 * If refreshRate is negative, then the data will never be refreshed.
 * <p>
 * It expects the following endpoint to exist remotely:
 * <p>
 * GET {endpoint}
 *   -- Returns all the known Stored Requests and Stored Imps.
 * <p>
 * GET {endpoint}?last-modified={timestamp}
 *   -- Returns the Stored Requests and Stored Imps which have been updated since the last timestamp.
 *      This timestamp will be sent in the rfc3339 format, using UTC and no timezone shift.
 *      For more info, see: https://tools.ietf.org/html/rfc3339
 * <p>
 * The responses should be JSON like this:
 * <pre>
 * {
 *   "requests": {
 *     "request1": { ... stored request data ... },
 *     "request2": { ... stored request data ... },
 *     "request3": { ... stored request data ... },
 *   },
 *   "imps": {
 *     "imp1": { ... stored data for imp1 ... },
 *     "imp2": { ... stored data for imp2 ... },
 *   }
 * }
 * </pre>
 * <p>
 * To signal deletions, the endpoint may return { "deleted": true }
 * in place of the Stored Data if the "last-modified" param existed.
 */
public class HttpPeriodicRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(HttpPeriodicRefreshService.class);

    private final CacheNotificationListener cacheNotificationListener;
    private final String refreshUrl;
    private final long refreshPeriod;
    private final long timeout;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private Instant lastUpdateTime;

    public HttpPeriodicRefreshService(CacheNotificationListener cacheNotificationListener, String refreshUrl,
                                      long refreshPeriod, long timeout, Vertx vertx, HttpClient httpClient) {
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.refreshUrl = HttpUtil.validateUrl(Objects.requireNonNull(refreshUrl));
        this.refreshPeriod = refreshPeriod;
        this.timeout = timeout;
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    public void initialize() {
        if (refreshPeriod > 0) {
            vertx.setPeriodic(refreshPeriod, aLong -> refresh());
        }
        getAll();
    }

    private void getAll() {
        lastUpdateTime = Instant.now();

        httpClient.get(refreshUrl, timeout)
                .map(HttpPeriodicRefreshService::processResponse)
                .map(this::save)
                .recover(HttpPeriodicRefreshService::failResponse);
    }

    private Void save(HttpRefreshResponse refreshResponse) {
        final Map<String, String> requests = parseStoredData(refreshResponse.getRequests(), StoredDataType.request);
        final Map<String, String> imps = parseStoredData(refreshResponse.getImps(), StoredDataType.imp);

        cacheNotificationListener.save(requests, imps);

        return null;
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<Void> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to currency service", exception);
        return Future.failedFuture(exception);
    }

    private static HttpRefreshResponse processResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final String body = response.getBody();
        final HttpRefreshResponse refreshResponse;
        try {
            refreshResponse = Json.decodeValue(body, HttpRefreshResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", body), e);
        }

        return refreshResponse;
    }

    private static Map<String, String> parseStoredData(Map<String, ObjectNode> refreshResponse,
                                                       StoredDataType type) {
        final Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, ObjectNode> entry : refreshResponse.entrySet()) {
            final String id = entry.getKey();

            final String jsonAsString;
            try {
                jsonAsString = Json.mapper.writeValueAsString(entry.getValue());
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error parsing %s json for id: %s with message: %s", type, id,
                        e.getMessage()));
            }
            result.put(id, jsonAsString);
        }
        return result;
    }

    private void refresh() {
        final Instant updateTime = Instant.now();

        final String lastModifiedParam = "last-modified=" + lastUpdateTime;
        final String andOrParam = refreshUrl.contains("?") ? "&" : "?";
        final String refreshEndpoint = refreshUrl + andOrParam + lastModifiedParam;

        httpClient.get(refreshEndpoint, timeout)
                .map(HttpPeriodicRefreshService::processResponse)
                .map(this::update)
                .map(this::save)
                .recover(HttpPeriodicRefreshService::failResponse);

        lastUpdateTime = updateTime;
    }

    private HttpRefreshResponse update(HttpRefreshResponse refreshResponse) {
        final List<String> invalidatedRequests = getInvalidatedKeys(refreshResponse.getRequests());
        final List<String> invalidatedImps = getInvalidatedKeys(refreshResponse.getImps());

        if (!invalidatedRequests.isEmpty() || !invalidatedImps.isEmpty()) {
            cacheNotificationListener.invalidate(invalidatedRequests, invalidatedImps);
        }

        final Map<String, ObjectNode> requestsToSave = removeFromMap(refreshResponse.getRequests(),
                invalidatedRequests);
        final Map<String, ObjectNode> impsToSave = removeFromMap(refreshResponse.getImps(), invalidatedImps);

        return HttpRefreshResponse.of(requestsToSave, impsToSave);
    }

    private static List<String> getInvalidatedKeys(Map<String, ObjectNode> changes) {
        final List<String> result = new ArrayList<>();

        for (String id : changes.keySet()) {
            final ObjectNode jsonNodes = changes.get(id);
            final JsonNode deleted = jsonNodes.get("deleted");
            if (deleted != null && deleted.asBoolean()) {
                result.add(id);
            }
        }
        return result;
    }

    private static Map<String, ObjectNode> removeFromMap(Map<String, ObjectNode> map, List<String> invalidatedKeys) {
        final Map<String, ObjectNode> result = new HashMap<>(map);
        for (String key : invalidatedKeys) {
            result.remove(key);
        }
        return result;
    }
}
