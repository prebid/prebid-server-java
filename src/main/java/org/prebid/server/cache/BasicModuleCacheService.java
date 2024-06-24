package org.prebid.server.cache;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cache.proto.request.module.ModuleCacheRequest;
import org.prebid.server.cache.proto.request.module.ModuleCacheType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;
import org.prebid.server.cache.utils.CacheServiceUtil;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.net.URL;
import java.util.Objects;

public class BasicModuleCacheService implements ModuleCacheService {

    public static final String MODULE_KEY_PREFIX = "module";
    public static final String MODULE_KEY_DELIMETER = ".";

    private final HttpClient httpClient;
    private final URL endpointUrl;
    private final String apiKey;
    private final int callTimeoutMs;
    private final JacksonMapper mapper;

    public BasicModuleCacheService(HttpClient httpClient,
                                   URL endpointUrl,
                                   String apiKey,
                                   int callTimeoutMs,
                                   JacksonMapper mapper) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.apiKey = Objects.requireNonNull(apiKey);
        this.callTimeoutMs = callTimeoutMs;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<Void> storeModuleEntry(String key,
                                         String value,
                                         ModuleCacheType type,
                                         Integer ttlseconds,
                                         String application,
                                         String moduleCode) {
        try {
            validateStoreData(key, value, type, moduleCode);
        } catch (Throwable e) {
            return Future.failedFuture(e);
        }

        final ModuleCacheRequest moduleCacheRequest =
                ModuleCacheRequest.of(
                        constructEntryKey(key, moduleCode),
                        type,
                        prepareValueForStoring(value, type),
                        application,
                        ttlseconds);

        return httpClient.post(endpointUrl.toString(),
                        securedCallHeaders(),
                        mapper.encodeToString(moduleCacheRequest),
                        callTimeoutMs)
                .compose(response -> processStoreResponse(
                        response.getStatusCode(), response.getBody()));

    }

    private static void validateStoreData(String key,
                                          String value,
                                          ModuleCacheType type,
                                          String moduleCode) {
        if (StringUtils.isBlank(key)) {
            throw new PreBidException("Module cache 'key' can not be blank");
        }

        if (StringUtils.isBlank(value)) {
            throw new PreBidException("Module cache 'value' can not be blank");
        }

        if (type == null) {
            throw new PreBidException("Module cache 'type' can not be empty");
        }

        if (StringUtils.isBlank(moduleCode)) {
            throw new PreBidException("Module cache 'moduleCode' can not be blank");
        }
    }

    private static String prepareValueForStoring(String value, ModuleCacheType type) {
        return type == ModuleCacheType.TEXT
                ? new String(Base64.encodeBase64(value.getBytes()))
                : value;
    }

    private MultiMap securedCallHeaders() {
        return CacheServiceUtil.CACHE_HEADERS
                .add(HttpUtil.X_PBC_API_KEY_HEADER, apiKey);
    }

    private String constructEntryKey(String key, String moduleCode) {
        return MODULE_KEY_PREFIX + MODULE_KEY_DELIMETER + moduleCode + MODULE_KEY_DELIMETER + key;
    }

    private Future<Void> processStoreResponse(int statusCode, String responseBody) {

        if (statusCode != 204) {
            throw new PreBidException("HTTP status code: '%s', body: '%s' "
                    .formatted(statusCode, responseBody));
        }

        return Future.succeededFuture();
    }

    @Override
    public Future<ModuleCacheResponse> retrieveModuleEntry(String key,
                                                           String moduleCode,
                                                           String application) {

        try {
            validateRetrieveData(key, moduleCode);
        } catch (Throwable e) {
            return Future.failedFuture(e);
        }

        return httpClient.get(getRetrieveEndpoint(key, moduleCode, application),
                        securedCallHeaders(),
                        callTimeoutMs)
                .map(response -> toModuleCacheResponse(
                        response.getStatusCode(), response.getBody()));

    }

    private static void validateRetrieveData(String key, String moduleCode) {
        if (StringUtils.isBlank(key)) {
            throw new PreBidException("Module cache 'key' can not be blank");
        }

        if (StringUtils.isBlank(moduleCode)) {
            throw new PreBidException("Module cache 'moduleCode' can not be blank");
        }
    }

    private String getRetrieveEndpoint(String key,
                                       String moduleCode,
                                       String application) {

        return endpointUrl
                + "?key=" + constructEntryKey(key, moduleCode)
                + "&app=" + StringUtils.defaultString(application);
    }

    private ModuleCacheResponse toModuleCacheResponse(int statusCode,
                                                      String responseBody) {

        if (statusCode != 200) {
            throw new PreBidException("HTTP status code " + statusCode);
        }

        final ModuleCacheResponse moduleCacheResponse;
        try {
            moduleCacheResponse = mapper.decodeValue(responseBody, ModuleCacheResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Cannot parse response: " + responseBody, e);
        }

        final String processedValue =
                prepareValueAfterRetrieve(moduleCacheResponse.getValue(), moduleCacheResponse.getType());

        return StringUtils.equals(moduleCacheResponse.getValue(), processedValue)
                ? moduleCacheResponse
                : moduleCacheResponse.toBuilder().value(processedValue).build();
    }

    private static String prepareValueAfterRetrieve(String value, ModuleCacheType type) {
        return type == ModuleCacheType.TEXT
                ? new String(Base64.decodeBase64(value.getBytes()))
                : value;
    }
}
