package org.prebid.server.cache;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cache.proto.request.module.ModuleCacheRequest;
import org.prebid.server.cache.proto.request.module.StorageDataType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;
import org.prebid.server.cache.utils.CacheServiceUtil;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.net.URL;
import java.time.Clock;
import java.util.Objects;

public class BasicPbcStorageService implements PbcStorageService {

    public static final String MODULE_KEY_PREFIX = "module";
    public static final String MODULE_KEY_DELIMETER = ".";

    private final HttpClient httpClient;
    private final URL endpointUrl;
    private final String apiKey;
    private final int callTimeoutMs;
    private final JacksonMapper mapper;
    private final Clock clock;
    private final Metrics metrics;

    public BasicPbcStorageService(HttpClient httpClient,
                                  URL endpointUrl,
                                  String apiKey,
                                  int callTimeoutMs,
                                  JacksonMapper mapper,
                                  Clock clock,
                                  Metrics metrics) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.apiKey = Objects.requireNonNull(apiKey);
        this.callTimeoutMs = callTimeoutMs;
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = metrics;
    }

    @Override
    public Future<Void> storeEntry(String key,
                                   String value,
                                   StorageDataType type,
                                   Integer ttlseconds,
                                   String application,
                                   String appCode) {

        try {
            validateStoreData(key, value, application, type, appCode);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final String valueToStore = prepareValueForStoring(value, type);
        final ModuleCacheRequest moduleCacheRequest =
                ModuleCacheRequest.of(
                        constructEntryKey(key, appCode),
                        type,
                        valueToStore,
                        application,
                        ttlseconds);

        updateCreativeMetrics(valueToStore, type, ttlseconds, appCode);

        final long startTime = clock.millis();
        return httpClient.post(
                        endpointUrl.toString(),
                        securedCallHeaders(),
                        mapper.encodeToString(moduleCacheRequest),
                        callTimeoutMs)
                .compose(response -> processStoreResponse(
                        response.getStatusCode(),
                        response.getBody(),
                        startTime,
                        appCode));

    }

    private static void validateStoreData(String key,
                                          String value,
                                          String application,
                                          StorageDataType type,
                                          String moduleCode) {

        if (StringUtils.isBlank(key)) {
            throw new PreBidException("Module cache 'key' can not be blank");
        }

        if (StringUtils.isBlank(value)) {
            throw new PreBidException("Module cache 'value' can not be blank");
        }

        if (StringUtils.isBlank(application)) {
            throw new PreBidException("Module cache 'application' can not be blank");
        }

        if (type == null) {
            throw new PreBidException("Module cache 'type' can not be empty");
        }

        if (StringUtils.isBlank(moduleCode)) {
            throw new PreBidException("Module cache 'moduleCode' can not be blank");
        }
    }

    private void updateCreativeMetrics(String value, StorageDataType type, Integer ttlseconds, String appCode) {
        final MetricName metricName = switch (type) {
            case XML -> MetricName.xml;
            case JSON -> MetricName.json;
            case TEXT -> MetricName.text;
        };

        metrics.updateModuleStorageCacheEntrySize(appCode, value.length(), metricName);
        if (ttlseconds != null) {
            metrics.updateModuleStorageCacheEntryTtl(appCode, ttlseconds, metricName);
        }
    }

    private static String prepareValueForStoring(String value, StorageDataType type) {
        return type == StorageDataType.TEXT
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

    private Future<Void> processStoreResponse(int statusCode, String responseBody, long startTime, String appCode) {
        if (statusCode != 204) {
            metrics.updateModuleStorageCacheWriteRequestTime(appCode, clock.millis() - startTime, MetricName.err);
            throw new PreBidException("HTTP status code: '%s', body: '%s' "
                    .formatted(statusCode, responseBody));
        }

        metrics.updateModuleStorageCacheWriteRequestTime(appCode, clock.millis() - startTime, MetricName.ok);
        return Future.succeededFuture();
    }

    @Override
    public Future<ModuleCacheResponse> retrieveEntry(String key, String appCode, String application) {
        try {
            validateRetrieveData(key, application, appCode);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final long startTime = clock.millis();
        return httpClient.get(
                        getRetrieveEndpoint(key, appCode, application),
                        securedCallHeaders(),
                        callTimeoutMs)
                .map(response -> toModuleCacheResponse(
                        response.getStatusCode(),
                        response.getBody(),
                        startTime,
                        appCode));

    }

    private static void validateRetrieveData(String key, String application, String moduleCode) {
        if (StringUtils.isBlank(key)) {
            throw new PreBidException("Module cache 'key' can not be blank");
        }

        if (StringUtils.isBlank(application)) {
            throw new PreBidException("Module cache 'application' can not be blank");
        }

        if (StringUtils.isBlank(moduleCode)) {
            throw new PreBidException("Module cache 'moduleCode' can not be blank");
        }
    }

    private String getRetrieveEndpoint(String key,
                                       String moduleCode,
                                       String application) {

        return endpointUrl
                + "?k=" + constructEntryKey(key, moduleCode)
                + "&a=" + application;
    }

    private ModuleCacheResponse toModuleCacheResponse(int statusCode,
                                                      String responseBody,
                                                      long startTime,
                                                      String application) {

        if (statusCode != 200) {
            metrics.updateModuleStorageCacheReadRequestTime(application, clock.millis() - startTime, MetricName.err);
            throw new PreBidException("HTTP status code " + statusCode);
        }

        metrics.updateModuleStorageCacheReadRequestTime(application, clock.millis() - startTime, MetricName.ok);

        final ModuleCacheResponse moduleCacheResponse;
        try {
            moduleCacheResponse = mapper.decodeValue(responseBody, ModuleCacheResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Cannot parse response: " + responseBody, e);
        }

        final String processedValue =
                prepareValueAfterRetrieve(moduleCacheResponse.getValue(), moduleCacheResponse.getType());

        // Use == instead of equals, because it is enough to check if the reference has changed
        return moduleCacheResponse.getValue() == processedValue
                ? moduleCacheResponse
                : ModuleCacheResponse.of(moduleCacheResponse.getKey(), moduleCacheResponse.getType(), processedValue);
    }

    private static String prepareValueAfterRetrieve(String value, StorageDataType type) {
        return type == StorageDataType.TEXT
                ? new String(Base64.decodeBase64(value.getBytes()))
                : value;
    }
}
