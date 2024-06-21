package org.prebid.server.cache;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
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
    public static final long CACHE_CALL_TIMEOUT_MS = 100L;

    private final HttpClient httpClient;
    private final URL endpointUrl;
    private final String pbcApiKey;
    private final JacksonMapper mapper;

    public BasicModuleCacheService(HttpClient httpClient,
                                   URL endpointUrl,
                                   String pbcApiKey,
                                   JacksonMapper mapper) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.pbcApiKey = Objects.requireNonNull(pbcApiKey);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<Void> storeModuleEntry(String key,
                                         String value,
                                         ModuleCacheType type,
                                         Integer ttlseconds,
                                         String application,
                                         String moduleCode) {

        final ModuleCacheRequest moduleCacheRequest =
                ModuleCacheRequest.of(constructEntryKey(key, moduleCode), type, value, application, ttlseconds);

        return httpClient.post(endpointUrl.toString(),
                        securedCallHeaders(),
                        mapper.encodeToString(moduleCacheRequest),
                        CACHE_CALL_TIMEOUT_MS)
                .compose(response -> processStoreResponse(
                        response.getStatusCode(), response.getBody()));

    }

    private MultiMap securedCallHeaders() {
        return CacheServiceUtil.CACHE_HEADERS
                .add(HttpUtil.X_PBC_API_KEY_HEADER, pbcApiKey);
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
                                                           String moduleCode) {

        return httpClient.get(getRetrieveEndpoint(key, moduleCode),
                        securedCallHeaders(),
                        CACHE_CALL_TIMEOUT_MS)
                .map(response -> toBidCacheResponse(
                        response.getStatusCode(), response.getBody()));

    }

    private String getRetrieveEndpoint(String key,
                                       String moduleCode) {
        return endpointUrl + "?key=" + constructEntryKey(key, moduleCode);
    }

    private ModuleCacheResponse toBidCacheResponse(int statusCode,
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

        return moduleCacheResponse;
    }
}
