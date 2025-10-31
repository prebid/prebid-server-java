package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Category;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.settings.proto.response.HttpFetcherResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.UriTemplateUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Instant;
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
import java.util.stream.Stream;

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
 * * GET {endpoint}?request-id=req1&request-id=req2&imp-id=imp1&imp-id=imp2&imp-id=imp3
 * * <p>
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
    private static final long TIMESTAMP = Instant.now().toEpochMilli();
    private static final String ACCOUNT_ID_PARAMETER = "accountId%s".formatted(TIMESTAMP);
    private static final String REQUEST_ID_PARAMETER = "requestId%s".formatted(TIMESTAMP);
    private static final String IMP_ID_PARAMETER = "impId%s".formatted(TIMESTAMP);

    private final boolean isRfc3986Compatible;
    private final String endpoint;
    private final String ampEndpoint;
    private final String videoEndpoint;
    private final String categoryEndpoint;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
    private final UriTemplate storedDataUrlTemplate;
    private final UriTemplate accountUrlTemplate;

    public HttpApplicationSettings(boolean isRfc3986Compatible,
                                   String endpoint,
                                   String ampEndpoint,
                                   String videoEndpoint,
                                   String categoryEndpoint,
                                   HttpClient httpClient,
                                   JacksonMapper mapper) {

        this.isRfc3986Compatible = isRfc3986Compatible;
        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.ampEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(ampEndpoint));
        this.videoEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(videoEndpoint));
        this.categoryEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(categoryEndpoint));
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);

        this.storedDataUrlTemplate = UriTemplateUtil.createTemplate(endpoint, REQUEST_ID_PARAMETER, IMP_ID_PARAMETER);
        this.accountUrlTemplate = UriTemplateUtil.createTemplate(endpoint, ACCOUNT_ID_PARAMETER);
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
                .map(response -> processAccountsResponse(response, accountIds));
    }

    private String accountsRequestUrlFrom(String endpoint, Set<String> accountIds) {
        if (accountIds.isEmpty()) {
            return endpoint;
        }

        final String resolvedUrl = isRfc3986Compatible
                ? accountUrlTemplate.expandToString(Variables.variables()
                .set(ACCOUNT_ID_PARAMETER, new ArrayList<>(accountIds)))
                : accountUrlTemplate.expandToString(Variables.variables()
                .set(ACCOUNT_ID_PARAMETER, "[\"%s\"]".formatted(joinIds(accountIds))));

        return resolvedUrl.replace(ACCOUNT_ID_PARAMETER, isRfc3986Compatible ? "account-id" : "account-ids");
    }

    private Set<Account> processAccountsResponse(HttpClientResponse httpClientResponse, Set<String> accountIds) {
        final int statusCode = httpClientResponse.getStatusCode();
        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("Error fetching accounts %s via http: unexpected response status %d"
                    .formatted(accountIds, statusCode));
        }

        final HttpAccountsResponse response;
        try {
            response = mapper.decodeValue(httpClientResponse.getBody(), HttpAccountsResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Error fetching accounts %s via http: failed to parse response: %s"
                    .formatted(accountIds, e.getMessage()));
        }
        final Map<String, Account> accounts = response.getAccounts();

        return MapUtils.isNotEmpty(accounts) ? new HashSet<>(accounts.values()) : Collections.emptySet();
    }

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

        return fetchStoredData(ampEndpoint, requestIds, Collections.emptySet(), timeout);
    }

    @Override
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

        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            return Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failStoredDataResponse(new TimeoutException("Timeout has been exceeded"), requestIds, impIds);
        }

        return httpClient.get(storeRequestUrlFrom(endpoint, requestIds, impIds), HttpUtil.headers(), remainingTimeout)
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

    private String storeRequestUrlFrom(String endpoint, Set<String> requestIds, Set<String> impIds) {
        if (requestIds.isEmpty() && impIds.isEmpty()) {
            return endpoint;
        }

        final Variables variables = Variables.variables();
        if (isRfc3986Compatible) {
            if (!requestIds.isEmpty()) {
                variables.set(REQUEST_ID_PARAMETER, new ArrayList<>(requestIds));
            }
            if (!impIds.isEmpty()) {
                variables.set(IMP_ID_PARAMETER, new ArrayList<>(impIds));
            }

            return storedDataUrlTemplate.expandToString(variables)
                    .replace(REQUEST_ID_PARAMETER, "request-id")
                    .replace(IMP_ID_PARAMETER, "imp-id");
        }

        if (!requestIds.isEmpty()) {
            variables.set(REQUEST_ID_PARAMETER, "[\"%s\"]".formatted(joinIds(requestIds)));
        }
        if (!impIds.isEmpty()) {
            variables.set(IMP_ID_PARAMETER, "[\"%s\"]".formatted(joinIds(impIds)));
        }
        return storedDataUrlTemplate.expandToString(variables)
                .replace(REQUEST_ID_PARAMETER, "request-ids")
                .replace(IMP_ID_PARAMETER, "imp-ids");
    }

    private StoredDataResult<String> processStoredDataResponse(HttpClientResponse httpClientResponse,
                                                               Set<String> requestIds,
                                                               Set<String> impIds) {

        final int statusCode = httpClientResponse.getStatusCode();
        if (statusCode != HttpResponseStatus.OK.code()) {
            return toFailedStoredDataResult(requestIds, impIds, "HTTP status code %d", statusCode);
        }

        final String body = httpClientResponse.getBody();
        final HttpFetcherResponse response;
        try {
            response = mapper.decodeValue(body, HttpFetcherResponse.class);
        } catch (DecodeException e) {
            return toFailedStoredDataResult(
                    requestIds, impIds, "parsing json failed for response: %s with message: %s", body, e.getMessage());
        }

        return parseResponse(requestIds, impIds, response);
    }

    private StoredDataResult<String> parseResponse(Set<String> requestIds,
                                                   Set<String> impIds,
                                                   HttpFetcherResponse response) {

        final List<String> errors = new ArrayList<>();

        final Map<String, String> storedIdToRequest =
                parseStoredDataOrAddError(requestIds, response.getRequests(), StoredDataType.request, errors);

        final Map<String, String> storedIdToImp =
                parseStoredDataOrAddError(impIds, response.getImps(), StoredDataType.imp, errors);

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    private Map<String, String> parseStoredDataOrAddError(Set<String> ids,
                                                          Map<String, ObjectNode> storedData,
                                                          StoredDataType type,
                                                          List<String> errors) {

        final Map<String, String> result = new HashMap<>(ids.size());

        if (storedData != null) {
            for (Map.Entry<String, ObjectNode> entry : storedData.entrySet()) {
                final String id = entry.getKey();

                final String jsonAsString;
                try {
                    jsonAsString = mapper.mapper().writeValueAsString(entry.getValue());
                } catch (JsonProcessingException e) {
                    errors.add("Error parsing %s json for id: %s with message: %s".formatted(type, id, e.getMessage()));
                    continue;
                }

                result.put(id, jsonAsString);
            }
        }

        if (result.size() < ids.size()) {
            final Set<String> missedIds = new HashSet<>(ids);
            missedIds.removeAll(result.keySet());

            missedIds.forEach(id -> errors.add("Stored %s not found for id: %s".formatted(type, id)));
        }

        return result;
    }

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
}
