package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

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
 * Fetches an application settings from the service via HTTP protocol.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link HttpApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 * <p>
 * Expected the endpoint to satisfy the following API:
 * <p>
 * GET {endpoint}?request-ids=["req1","req2"]&imp-ids=["imp1","imp2","imp3"]
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

    private String endpoint;
    private String ampEndpoint;
    private String videoEndpoint;
    private HttpClient httpClient;
    private final JacksonMapper mapper;

    public HttpApplicationSettings(HttpClient httpClient, JacksonMapper mapper, String endpoint, String ampEndpoint,
                                   String videoEndpoint) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.ampEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(ampEndpoint));
        this.videoEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(videoEndpoint));
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
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(endpoint, requestIds, impIds, timeout);
    }

    /**
     * Not supported and returns failed result.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from http service
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(ampEndpoint, requestIds, Collections.emptySet(), timeout);
    }

    /**
     * Not supported and returns failed result.
     */
    @Override
    public Future<StoredDataResult> getVideoStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(videoEndpoint, requestIds, impIds, timeout);
    }

    private Future<StoredDataResult> fetchStoredData(String endpoint, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            return Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(new TimeoutException("Timeout has been exceeded"), requestIds, impIds);
        }

        return httpClient.get(urlFrom(endpoint, requestIds, impIds), HttpUtil.headers(), remainingTimeout)
                .compose(response -> processResponse(response, requestIds, impIds))
                .recover(exception -> failResponse(exception, requestIds, impIds));
    }

    private static String urlFrom(String endpoint, Set<String> requestIds, Set<String> impIds) {
        final StringBuilder url = new StringBuilder(endpoint);
        url.append(endpoint.contains("?") ? "&" : "?");

        if (!requestIds.isEmpty()) {
            url.append("request-ids=[\"").append(joinIds(requestIds)).append("\"]");
        }

        if (!impIds.isEmpty()) {
            if (!requestIds.isEmpty()) {
                url.append("&");
            }
            url.append("imp-ids=[\"").append(joinIds(impIds)).append("\"]");
        }

        return url.toString();
    }

    private static String joinIds(Set<String> ids) {
        return String.join("\",\"", ids);
    }

    private static Future<StoredDataResult> failResponse(Throwable throwable, Set<String> requestIds,
                                                         Set<String> impIds) {
        return Future.succeededFuture(
                toFailedStoredDataResult(requestIds, impIds, throwable.getMessage()));
    }

    private Future<StoredDataResult> processResponse(HttpClientResponse response, Set<String> requestIds,
                                                     Set<String> impIds) {
        return Future.succeededFuture(
                toStoredDataResult(requestIds, impIds, response.getStatusCode(), response.getBody()));
    }

    private static StoredDataResult toFailedStoredDataResult(Set<String> requestIds, Set<String> impIds,
                                                             String errorMessageFormat, Object... args) {
        final String errorRequests = requestIds.isEmpty() ? ""
                : String.format("stored requests for ids %s", requestIds);
        final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
        final String errorImps = impIds.isEmpty() ? "" : String.format("stored imps for ids %s", impIds);

        final String error = String.format("Error fetching %s%s%s via HTTP: %s", errorRequests, separator, errorImps,
                String.format(errorMessageFormat, args));

        logger.info(error);
        return StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(error));
    }

    private StoredDataResult toStoredDataResult(Set<String> requestIds, Set<String> impIds,
                                                int statusCode, String body) {
        if (statusCode != 200) {
            return toFailedStoredDataResult(requestIds, impIds, "HTTP status code %d", statusCode);
        }

        final HttpFetcherResponse response;
        try {
            response = mapper.decodeValue(body, HttpFetcherResponse.class);
        } catch (DecodeException e) {
            return toFailedStoredDataResult(
                    requestIds, impIds, "parsing json failed for response: %s with message: %s", body, e.getMessage());
        }

        return parseResponse(requestIds, impIds, response);
    }

    private StoredDataResult parseResponse(Set<String> requestIds, Set<String> impIds,
                                           HttpFetcherResponse response) {
        final List<String> errors = new ArrayList<>();

        final Map<String, String> storedIdToRequest =
                parseStoredDataOrAddError(requestIds, response.getRequests(), StoredDataType.request, errors);

        final Map<String, String> storedIdToImp =
                parseStoredDataOrAddError(impIds, response.getImps(), StoredDataType.imp, errors);

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    private Map<String, String> parseStoredDataOrAddError(Set<String> ids, Map<String, ObjectNode> storedData,
                                                          StoredDataType type, List<String> errors) {
        final Map<String, String> result = new HashMap<>(ids.size());
        final Set<String> notParsedIds = new HashSet<>();

        if (storedData != null) {
            for (Map.Entry<String, ObjectNode> entry : storedData.entrySet()) {
                final String id = entry.getKey();

                final String jsonAsString;
                try {
                    jsonAsString = mapper.mapper().writeValueAsString(entry.getValue());
                } catch (JsonProcessingException e) {
                    errors.add(String.format("Error parsing %s json for id: %s with message: %s", type, id,
                            e.getMessage()));
                    notParsedIds.add(id);
                    continue;
                }

                result.put(id, jsonAsString);
            }
        }

        if (result.size() < ids.size()) {
            final Set<String> missedIds = new HashSet<>(ids);
            missedIds.removeAll(result.keySet());
            missedIds.removeAll(notParsedIds);

            errors.addAll(missedIds.stream()
                    .map(id -> String.format("Stored %s not found for id: %s", type, id))
                    .collect(Collectors.toList()));
        }

        return result;
    }
}
