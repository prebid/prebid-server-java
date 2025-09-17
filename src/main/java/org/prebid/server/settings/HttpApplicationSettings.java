package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Category;
<<<<<<< HEAD
import org.prebid.server.settings.model.Profile;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.net.URISyntaxException;
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
<<<<<<< HEAD
import java.util.stream.Stream;
=======
>>>>>>> 04d9d4a13 (Initial commit)

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Fetches an application settings from the service via HTTP protocol.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link HttpApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 * <p>
 * Expected the endpoint to satisfy the following API (URL is encoded):
 * <p>
 * GET {endpoint}?request-ids=["req1","req2"]&imp-ids=["imp1","imp2","imp3"]
 * <p>
 * or settings.http.rfc3986-compatible is set to true
 * <p>
<<<<<<< HEAD
 * * GET {endpoint}?request-id=req1&request-id=req2&imp-id=imp1&imp-id=imp2&imp-id=imp3
 * * <p>
=======
 *  * GET {endpoint}?request-id=req1&request-id=req2&imp-id=imp1&imp-id=imp2&imp-id=imp3
 *  * <p>
>>>>>>> 04d9d4a13 (Initial commit)
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

<<<<<<< HEAD
    private final boolean isRfc3986Compatible;
=======
>>>>>>> 04d9d4a13 (Initial commit)
    private final String endpoint;
    private final String ampEndpoint;
    private final String videoEndpoint;
    private final String categoryEndpoint;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
<<<<<<< HEAD

    public HttpApplicationSettings(boolean isRfc3986Compatible,
=======
    private final boolean isRfc3986Compatible;

    public HttpApplicationSettings(HttpClient httpClient,
                                   JacksonMapper mapper,
>>>>>>> 04d9d4a13 (Initial commit)
                                   String endpoint,
                                   String ampEndpoint,
                                   String videoEndpoint,
                                   String categoryEndpoint,
<<<<<<< HEAD
                                   HttpClient httpClient,
                                   JacksonMapper mapper) {

        this.isRfc3986Compatible = isRfc3986Compatible;
=======
                                   boolean isRfc3986Compatible) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
>>>>>>> 04d9d4a13 (Initial commit)
        this.endpoint = HttpUtil.validateUrlSyntax(Objects.requireNonNull(endpoint));
        this.ampEndpoint = HttpUtil.validateUrlSyntax(Objects.requireNonNull(ampEndpoint));
        this.videoEndpoint = HttpUtil.validateUrlSyntax(Objects.requireNonNull(videoEndpoint));
        this.categoryEndpoint = HttpUtil.validateUrlSyntax(Objects.requireNonNull(categoryEndpoint));
<<<<<<< HEAD
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
=======
        this.isRfc3986Compatible = isRfc3986Compatible;
>>>>>>> 04d9d4a13 (Initial commit)
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
<<<<<<< HEAD

=======
>>>>>>> 04d9d4a13 (Initial commit)
        final long remainingTimeout = timeout.remaining();
        if (timeout.remaining() <= 0) {
            return Future.failedFuture(new TimeoutException("Timeout has been exceeded"));
        }

        return httpClient.get(accountsRequestUrlFrom(endpoint, accountIds), HttpUtil.headers(), remainingTimeout)
<<<<<<< HEAD
                .map(response -> processAccountsResponse(response, accountIds));
=======
                .compose(response -> processAccountsResponse(response, accountIds))
                .recover(Future::failedFuture);
>>>>>>> 04d9d4a13 (Initial commit)
    }

    private String accountsRequestUrlFrom(String endpoint, Set<String> accountIds) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(endpoint);
            if (!accountIds.isEmpty()) {
                if (isRfc3986Compatible) {
                    accountIds.forEach(accountId -> uriBuilder.addParameter("account-id", accountId));
                } else {
                    uriBuilder.addParameter("account-ids", "[\"%s\"]".formatted(joinIds(accountIds)));
                }
            }
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException("URL %s has bad syntax".formatted(endpoint));
        }
    }

<<<<<<< HEAD
    private Set<Account> processAccountsResponse(HttpClientResponse httpClientResponse, Set<String> accountIds) {
        final int statusCode = httpClientResponse.getStatusCode();
=======
    private Future<Set<Account>> processAccountsResponse(HttpClientResponse response, Set<String> accountIds) {
        return Future.succeededFuture(
                toAccountsResult(response.getStatusCode(), response.getBody(), accountIds));
    }

    private Set<Account> toAccountsResult(int statusCode, String body, Set<String> accountIds) {
>>>>>>> 04d9d4a13 (Initial commit)
        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("Error fetching accounts %s via http: unexpected response status %d"
                    .formatted(accountIds, statusCode));
        }

        final HttpAccountsResponse response;
        try {
<<<<<<< HEAD
            response = mapper.decodeValue(httpClientResponse.getBody(), HttpAccountsResponse.class);
=======
            response = mapper.decodeValue(body, HttpAccountsResponse.class);
>>>>>>> 04d9d4a13 (Initial commit)
        } catch (DecodeException e) {
            throw new PreBidException("Error fetching accounts %s via http: failed to parse response: %s"
                    .formatted(accountIds, e.getMessage()));
        }
        final Map<String, Account> accounts = response.getAccounts();

        return MapUtils.isNotEmpty(accounts) ? new HashSet<>(accounts.values()) : Collections.emptySet();
    }

<<<<<<< HEAD
    @Override
    public Future<StoredDataResult<String>> getStoredData(String accountId,
                                                          Set<String> requestIds,
                                                          Set<String> impIds,
                                                          Timeout timeout) {

        return fetchStoredData(endpoint, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

=======
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
>>>>>>> 04d9d4a13 (Initial commit)
        return fetchStoredData(ampEndpoint, requestIds, Collections.emptySet(), timeout);
    }

    @Override
<<<<<<< HEAD
    public Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                               Set<String> requestIds,
                                                               Set<String> impIds,
                                                               Timeout timeout) {

        return fetchStoredData(videoEndpoint, requestIds, impIds, timeout);
    }

    private Future<StoredDataResult<String>> fetchStoredData(String endpoint,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

=======
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
>>>>>>> 04d9d4a13 (Initial commit)
        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            return Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failStoredDataResponse(new TimeoutException("Timeout has been exceeded"), requestIds, impIds);
        }

        return httpClient.get(storeRequestUrlFrom(endpoint, requestIds, impIds), HttpUtil.headers(), remainingTimeout)
<<<<<<< HEAD
                .map(response -> processStoredDataResponse(response, requestIds, impIds))
                .recover(exception -> failStoredDataResponse(exception, requestIds, impIds));
    }

    private static Future<StoredDataResult<String>> failStoredDataResponse(Throwable throwable,
                                                                           Set<String> requestIds,
                                                                           Set<String> impIds) {

        return Future.succeededFuture(toFailedStoredDataResult(requestIds, impIds, throwable.getMessage()));
    }

    private static StoredDataResult<String> toFailedStoredDataResult(Set<String> requestIds,
                                                                     Set<String> impIds,
                                                                     String errorMessageFormat,
                                                                     Object... args) {

        final String errorRequests = requestIds.isEmpty() ? "" : "stored requests for ids " + requestIds;
        final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
        final String errorImps = impIds.isEmpty() ? "" : "stored imps for ids " + impIds;

        final String error = "Error fetching %s%s%s via HTTP: %s"
                .formatted(errorRequests, separator, errorImps, errorMessageFormat.formatted(args));
        logger.info(error);

        return StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(error));
    }

=======
                .compose(response -> processStoredDataResponse(response, requestIds, impIds))
                .recover(exception -> failStoredDataResponse(exception, requestIds, impIds));
    }

>>>>>>> 04d9d4a13 (Initial commit)
    private String storeRequestUrlFrom(String endpoint, Set<String> requestIds, Set<String> impIds) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(endpoint);
            if (!requestIds.isEmpty()) {
                if (isRfc3986Compatible) {
                    requestIds.forEach(requestId -> uriBuilder.addParameter("request-id", requestId));
                } else {
                    uriBuilder.addParameter("request-ids", "[\"%s\"]".formatted(joinIds(requestIds)));
                }
            }
            if (!impIds.isEmpty()) {
                if (isRfc3986Compatible) {
                    impIds.forEach(impId -> uriBuilder.addParameter("imp-id", impId));
                } else {
                    uriBuilder.addParameter("imp-ids", "[\"%s\"]".formatted(joinIds(impIds)));
                }
            }
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException("URL %s has bad syntax".formatted(endpoint));
        }
    }

<<<<<<< HEAD
    private StoredDataResult<String> processStoredDataResponse(HttpClientResponse httpClientResponse,
                                                               Set<String> requestIds,
                                                               Set<String> impIds) {

        final int statusCode = httpClientResponse.getStatusCode();
=======
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
>>>>>>> 04d9d4a13 (Initial commit)
        if (statusCode != HttpResponseStatus.OK.code()) {
            return toFailedStoredDataResult(requestIds, impIds, "HTTP status code %d", statusCode);
        }

<<<<<<< HEAD
        final String body = httpClientResponse.getBody();
=======
>>>>>>> 04d9d4a13 (Initial commit)
        final HttpFetcherResponse response;
        try {
            response = mapper.decodeValue(body, HttpFetcherResponse.class);
        } catch (DecodeException e) {
            return toFailedStoredDataResult(
                    requestIds, impIds, "parsing json failed for response: %s with message: %s", body, e.getMessage());
        }

        return parseResponse(requestIds, impIds, response);
    }

<<<<<<< HEAD
    private StoredDataResult<String> parseResponse(Set<String> requestIds,
                                                   Set<String> impIds,
                                                   HttpFetcherResponse response) {

=======
    private StoredDataResult parseResponse(Set<String> requestIds, Set<String> impIds,
                                           HttpFetcherResponse response) {
>>>>>>> 04d9d4a13 (Initial commit)
        final List<String> errors = new ArrayList<>();

        final Map<String, String> storedIdToRequest =
                parseStoredDataOrAddError(requestIds, response.getRequests(), StoredDataType.request, errors);

        final Map<String, String> storedIdToImp =
                parseStoredDataOrAddError(impIds, response.getImps(), StoredDataType.imp, errors);

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

<<<<<<< HEAD
    private Map<String, String> parseStoredDataOrAddError(Set<String> ids,
                                                          Map<String, ObjectNode> storedData,
                                                          StoredDataType type,
                                                          List<String> errors) {

        final Map<String, String> result = new HashMap<>(ids.size());
=======
    private Map<String, String> parseStoredDataOrAddError(Set<String> ids, Map<String, ObjectNode> storedData,
                                                          StoredDataType type, List<String> errors) {
        final Map<String, String> result = new HashMap<>(ids.size());
        final Set<String> notParsedIds = new HashSet<>();
>>>>>>> 04d9d4a13 (Initial commit)

        if (storedData != null) {
            for (Map.Entry<String, ObjectNode> entry : storedData.entrySet()) {
                final String id = entry.getKey();

                final String jsonAsString;
                try {
                    jsonAsString = mapper.mapper().writeValueAsString(entry.getValue());
                } catch (JsonProcessingException e) {
                    errors.add("Error parsing %s json for id: %s with message: %s".formatted(type, id, e.getMessage()));
<<<<<<< HEAD
=======
                    notParsedIds.add(id);
>>>>>>> 04d9d4a13 (Initial commit)
                    continue;
                }

                result.put(id, jsonAsString);
            }
        }

        if (result.size() < ids.size()) {
            final Set<String> missedIds = new HashSet<>(ids);
            missedIds.removeAll(result.keySet());
<<<<<<< HEAD

            missedIds.forEach(id -> errors.add("Stored %s not found for id: %s".formatted(type, id)));
=======
            missedIds.removeAll(notParsedIds);

            errors.addAll(missedIds.stream()
                    .map(id -> "Stored %s not found for id: %s".formatted(type, id))
                    .toList());
>>>>>>> 04d9d4a13 (Initial commit)
        }

        return result;
    }
<<<<<<< HEAD

    @Override
    public Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                         Set<String> requestIds,
                                                         Set<String> impIds,
                                                         Timeout timeout) {

        return Future.succeededFuture(StoredDataResult.of(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Stream.concat(requestIds.stream(), impIds.stream())
                        .map(id -> "Profile not found for id: " + id)
                        .toList()));
    }

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
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        catToCategory -> catToCategory.getValue().getId()));
    }

    private PreBidException makeFailedCategoryFetchException(String url, String reason) {
        return new PreBidException("Failed to fetch categories from url '%s'. Reason: %s".formatted(url, reason));
    }

    private static String joinIds(Set<String> ids) {
        return String.join("\",\"", ids);
    }
=======
>>>>>>> 04d9d4a13 (Initial commit)
}
