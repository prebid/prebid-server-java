package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.HttpFetcherResponse;
import org.prebid.server.settings.model.StoredRequestResult;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Fetches an application settings from another service via HTTP protocol.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link HttpApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 * <p>
 * Expected the endpoint to satisfy the following API:
 * <p>
 * GET {endpoint}?request-ids=req1,req2&imp-ids=imp1,imp2,imp3
 * <p>
 * This endpoint should return a payload like:
 * <pre>
 * {
 *   "requests": {
 *     "req1": { ... stored data for req1 ... },
 *     "req2": { ... stored data for req2 ... },
 *   },
 *   "imps": {
 *     "imp1": { ... stored data for imp1 ... },
 *     "imp2": { ... stored data for imp2 ... },
 *     "imp3": null // If imp3 is not found
 *   }
 * }
 * </pre>
 */
public class HttpApplicationSettings implements ApplicationSettings {

    private static final Logger logger = LoggerFactory.getLogger(HttpApplicationSettings.class);

    private static final String REQUEST_IDS_PARAM = "request-ids";

    // FIXME: uncomment when will be implemented obtaining by imp id
    // private static final String IMP_IDS_PARAM = "imp-ids";

    private HttpClient httpClient;
    private String endpoint;
    private String ampEndpoint;

    public HttpApplicationSettings(HttpClient httpClient, String endpoint, String ampEndpoint) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.ampEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(ampEndpoint));
    }

    /**
     * Not supported and returns failed result.
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
    }

    /**
     * Not supported and returns failed result.
     */
    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
    }

    /**
     * Runs a process to get stored requests by a collection of ids from http service
     * and returns {@link Future&lt;{@link StoredRequestResult}&gt;}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, Timeout timeout) {
        return fetchStoredRequests(endpoint, ids, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from http service
     * and returns {@link Future&lt;{@link StoredRequestResult}&gt;}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, Timeout timeout) {
        return fetchStoredRequests(ampEndpoint, ids, timeout);
    }

    private Future<StoredRequestResult> fetchStoredRequests(String endpoint, Set<String> ids, Timeout timeout) {
        final Future<StoredRequestResult> future = Future.future();

        if (CollectionUtils.isEmpty(ids)) {
            future.complete(StoredRequestResult.of(Collections.emptyMap(), Collections.emptyList()));
        } else {
            final long remainingTimeout = timeout.remaining();
            if (remainingTimeout <= 0) {
                handleException(new TimeoutException("Timeout has been exceeded"), future, ids);
            } else {
                httpClient.getAbs(urlFrom(endpoint, ids),
                        response -> handleResponse(response, future, ids))
                        .exceptionHandler(throwable -> handleException(throwable, future, ids))
                        .setTimeout(remainingTimeout)
                        .end();
            }
        }

        return future;
    }

    private static String urlFrom(String endpoint, Set<String> ids) {
        final String joinedIds = ids.stream().collect(Collectors.joining(","));
        return endpoint + (endpoint.contains("?") ? "&" : "?") + REQUEST_IDS_PARAM + "=" + joinedIds;
    }

    private static void handleException(Throwable throwable, Future<StoredRequestResult> future, Set<String> ids) {
        future.complete(
                failWith("Error fetching stored requests for ids %s via HTTP: %s", ids, throwable.getMessage()));
    }

    private static StoredRequestResult failWith(String errorMessageFormat, Object... args) {
        final String error = String.format(errorMessageFormat, args);

        logger.warn(error);
        return StoredRequestResult.of(Collections.emptyMap(), Collections.singletonList(error));
    }

    private static void handleResponse(HttpClientResponse response, Future<StoredRequestResult> future,
                                       Set<String> ids) {
        response
                .bodyHandler(buffer -> future.complete(
                        toStoredRequestResult(ids, response.statusCode(), buffer.toString())))
                .exceptionHandler(exception -> handleException(exception, future, ids));
    }

    private static StoredRequestResult toStoredRequestResult(Set<String> ids, int statusCode, String body) {
        if (statusCode != 200) {
            return failWith("Error fetching stored requests for ids %s via HTTP: Response code was %d",
                    ids, statusCode);
        }

        final HttpFetcherResponse response;
        try {
            response = Json.decodeValue(body, HttpFetcherResponse.class);
        } catch (DecodeException e) {
            return failWith(
                    "Error occurred while parsing stored requests for ids %s from response: %s with message: %s",
                    ids, body, e.getMessage());
        }

        return parseResponse(ids, response.getRequests());
    }

    private static StoredRequestResult parseResponse(Set<String> ids, Map<String, ObjectNode> requests) {
        final Map<String, String> storedIdToJson = new HashMap<>(ids.size());
        final List<String> errors = new ArrayList<>();
        final Set<String> notParsedIds = new HashSet<>();

        if (requests != null) {
            for (Map.Entry<String, ObjectNode> entry : requests.entrySet()) {
                final String id = entry.getKey();

                final String jsonAsString;
                try {
                    jsonAsString = Json.mapper.writeValueAsString(entry.getValue());
                } catch (JsonProcessingException e) {
                    errors.add(
                            String.format("Error while parsing json for id: %s with message: %s", id, e.getMessage()));
                    notParsedIds.add(id);
                    continue;
                }

                storedIdToJson.put(id, jsonAsString);
            }
        }

        if (storedIdToJson.size() < ids.size()) {
            final Set<String> missedIds = new HashSet<>(ids);
            missedIds.removeAll(storedIdToJson.keySet());
            missedIds.removeAll(notParsedIds);

            errors.addAll(missedIds.stream()
                    .map(id -> String.format("No config found for id: %s", id))
                    .collect(Collectors.toList()));
        }

        return StoredRequestResult.of(storedIdToJson, errors);
    }
}
