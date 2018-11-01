package org.prebid.server.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.proto.response.HttpRefreshResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.BasicHttpClient;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.singletonMap;

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
        this.cacheNotificationListener = cacheNotificationListener;
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
                .compose(HttpPeriodicRefreshService::processResponse)
                .map(this::save)
                .recover(HttpPeriodicRefreshService::failResponse);
    }

    private HttpRefreshResponse save(HttpRefreshResponse refreshResponse) {
        final Map<String, String> requests = parseStoredData(refreshResponse.getRequests(), StoredDataType.request);
        final Map<String, String> imps = parseStoredData(refreshResponse.getImps(), StoredDataType.imp);

        cacheNotificationListener.save(requests, imps);

        return null;
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<HttpRefreshResponse> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to currency service", exception);
        return Future.failedFuture(exception);
    }

    private static Future<HttpRefreshResponse> processResponse(HttpClientResponse response) {
        return Future.succeededFuture(parseResponse(response));
    }

    private static HttpRefreshResponse parseResponse(HttpClientResponse response) {
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

        String thisEndpoint = refreshUrl + "?last-modified=" + lastUpdateTime.toEpochMilli();

        httpClient.get(thisEndpoint, timeout)
                .compose(HttpPeriodicRefreshService::processResponse)
                .map(this::update)
                .map(this::save)
                .recover(HttpPeriodicRefreshService::failResponse);

        lastUpdateTime = updateTime;
    }

    private HttpRefreshResponse update(HttpRefreshResponse refreshResponse) {

        final List<String> invalidatedRequests = getInvalidatedKeys(refreshResponse.getRequests());
        final List<String> invalidatedImps = getInvalidatedKeys(refreshResponse.getImps());

        cacheNotificationListener.invalidate(invalidatedRequests, invalidatedImps);

        final Map<String, ObjectNode> requestsToSave = removeFromMap(refreshResponse.getRequests(), invalidatedRequests);
        final Map<String, ObjectNode> impsToSave = removeFromMap(refreshResponse.getImps(), invalidatedImps);

        return HttpRefreshResponse.of(requestsToSave, impsToSave);
    }

    private static List<String> getInvalidatedKeys(Map<String, ObjectNode> changes) {

        List<String> result = new ArrayList<>();
        for (String id : changes.keySet()) {
            String value;
            try {
                value = Json.mapper.writeValueAsString(changes.get(id));
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error parsing json for id: %s with message: %s", id,
                        e.getMessage()));
            }
            if (value.contains("deleted")) {
                result.add(id);
            }
        }
        return result;
    }

    private static Map<String, ObjectNode> removeFromMap(Map<String, ObjectNode> map, List<String> invalidatedKeys) {
        for (String key : invalidatedKeys) {
            map.remove(key);
        }
        return map;
    }
}
