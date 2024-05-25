package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Category;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

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
    private static final TypeReference<Map<String, Category>> CATEGORY_RESPONSE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpoint;
    private final String ampEndpoint;
    private final String videoEndpoint;
    private final String categoryEndpoint;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;

    public HttpApplicationSettings(HttpClient httpClient, JacksonMapper mapper, String endpoint, String ampEndpoint,
                                   String videoEndpoint, String categoryEndpoint) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.ampEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(ampEndpoint));
        this.videoEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(videoEndpoint));
        this.categoryEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(categoryEndpoint));
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {

        return fetchAccountsByIds(Collections.singleton(accountId), timeout)
                .map(accounts -> accounts.stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new PreBidException("Account with id : %s not found".formatted(accountId))));
    }

    private Future<Set<Account>> fetchAccountsByIds(Set<String> accountIds, Timeout timeout) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return Future.succeededFuture(Collections.emptySet());
        }
        final long remainingTimeout = timeout.remaining();
        if (timeout.remaining() <= 0) {
            return Future.failedFuture(new TimeoutException("Timeout has been exceeded"));
        }

        return httpClient.get(accountsRequestUrlFrom(endpoint, accountIds), HttpUtil.headers(), remainingTimeout)
                .compose(response -> processAccountsResponse(response, accountIds))
                .recover(Future::failedFuture);
    }

    private static String accountsRequestUrlFrom(String endpoint, Set<String> accountIds) {
        final StringBuilder url = new StringBuilder(endpoint);
        url.append(endpoint.contains("?") ? "&" : "?");

        if (!accountIds.isEmpty()) {
            url.append("account-ids=[\"").append(joinIds(accountIds)).append("\"]");
        }

        return url.toString();
    }

    private Future<Set<Account>> processAccountsResponse(HttpClientResponse response, Set<String> accountIds) {
        return Future.succeededFuture(
                toAccountsResult(response.getStatusCode(), response.getBody(), accountIds));
    }

    private Set<Account> toAccountsResult(int statusCode, String body, Set<String> accountIds) {
        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("Error fetching accounts %s via http: unexpected response status %d"
                    .formatted(accountIds, statusCode));
        }

        final HttpAccountsResponse response;
        try {
            response = mapper.decodeValue(body, HttpAccountsResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Error fetching accounts %s via http: failed to parse response: %s"
                    .formatted(accountIds, e.getMessage()));
        }
        final Map<String, Account> accounts = response.getAccounts();

        return MapUtils.isNotEmpty(accounts) ? new HashSet<>(accounts.values()) : Collections.emptySet();
    }

    /**
     * Runs a process to get stored requests by a collection of ids from http service
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                  Timeout timeout) {
        return fetchStoredData(endpoint, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from http service
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        return fetchStoredData(ampEndpoint, requestIds, Collections.emptySet(), timeout);
    }

    /**
     * Not supported and returns failed result.
     */
    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                       Timeout timeout) {
        return fetchStoredData(videoEndpoint, requestIds, impIds, timeout);
    }

    /**
     * Not supported and returns failed result.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        final String url = StringUtils.isNotEmpty(publisher)
                ? "%s/%s/%s.json".formatted(categoryEndpoint, primaryAdServer, publisher)
                : "%s/%s.json".formatted(categoryEndpoint, primaryAdServer);
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.failedFuture(new TimeoutException(
                    "Failed to fetch categories from url '%s'. Reason: Timeout exceeded".formatted(url)));
        }
        return httpClient.get(url, remainingTimeout)
                .map(httpClientResponse -> processCategoryResponse(httpClientResponse, url));
    }

    private Map<String, String> processCategoryResponse(HttpClientResponse httpClientResponse, String url) {
        final int statusCode = httpClientResponse.getStatusCode();
        if (statusCode != 200) {
            throw makeFailedCategoryFetchException(url, "Response status code is '%d'".formatted(statusCode));
        }

        final String body = httpClientResponse.getBody();
        if (StringUtils.isEmpty(body)) {
            throw makeFailedCategoryFetchException(url, "Response body is null or empty");
        }

        final Map<String, Category> categories;
        try {
            categories = mapper.decodeValue(body, CATEGORY_RESPONSE_REFERENCE);
        } catch (DecodeException e) {
            throw makeFailedCategoryFetchException(url, "Failed to decode response body with error " + e.getMessage());
        }
        return categories.entrySet().stream()
                .filter(catToCategory -> catToCategory.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        catToCategory -> catToCategory.getValue().getId()));
    }

    private PreBidException makeFailedCategoryFetchException(String url, String reason) {
        return new PreBidException("Failed to fetch categories from url '%s'. Reason: %s".formatted(url, reason));
    }

    private Future<StoredDataResult> fetchStoredData(String endpoint, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            return Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failStoredDataResponse(new TimeoutException("Timeout has been exceeded"), requestIds, impIds);
        }

        return httpClient.get(storeRequestUrlFrom(endpoint, requestIds, impIds), HttpUtil.headers(), remainingTimeout)
                .compose(response -> processStoredDataResponse(response, requestIds, impIds))
                .recover(exception -> failStoredDataResponse(exception, requestIds, impIds));
    }

    private static String storeRequestUrlFrom(String endpoint, Set<String> requestIds, Set<String> impIds) {
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

    private static Future<StoredDataResult> failStoredDataResponse(Throwable throwable, Set<String> requestIds,
                                                                   Set<String> impIds) {
        return Future.succeededFuture(
                toFailedStoredDataResult(requestIds, impIds, throwable.getMessage()));
    }

    private Future<StoredDataResult> processStoredDataResponse(HttpClientResponse response, Set<String> requestIds,
                                                               Set<String> impIds) {
        return Future.succeededFuture(
                toStoredDataResult(requestIds, impIds, response.getStatusCode(), response.getBody()));
    }

    private static StoredDataResult toFailedStoredDataResult(Set<String> requestIds, Set<String> impIds,
                                                             String errorMessageFormat, Object... args) {
        final String errorRequests = requestIds.isEmpty() ? ""
                : "stored requests for ids " + requestIds;
        final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
        final String errorImps = impIds.isEmpty() ? "" : "stored imps for ids " + impIds;

        final String error = "Error fetching %s%s%s via HTTP: %s"
                .formatted(errorRequests, separator, errorImps, errorMessageFormat.formatted(args));

        logger.info(error);
        return StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(error));
    }

    private StoredDataResult toStoredDataResult(Set<String> requestIds, Set<String> impIds,
                                                int statusCode, String body) {
        if (statusCode != HttpResponseStatus.OK.code()) {
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
                    errors.add("Error parsing %s json for id: %s with message: %s".formatted(type, id, e.getMessage()));
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
                    .map(id -> "Stored %s not found for id: %s".formatted(type, id))
                    .toList());
        }

        return result;
    }
}
