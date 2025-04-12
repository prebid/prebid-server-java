package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.CacheProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class OptableTargeting {

    private final Cache cache;
    private boolean moduleCacheEnabled = false;

    public OptableTargeting(Cache cache, IdsMapper idsMapper, QueryBuilder queryBuilder, APIClient apiClient,
                            boolean moduleCacheEnabled) {

        this.cache = cache;
        this.idsMapper = Objects.requireNonNull(idsMapper);
        this.queryBuilder = Objects.requireNonNull(queryBuilder);
        this.apiClient = Objects.requireNonNull(apiClient);
        this.moduleCacheEnabled = moduleCacheEnabled;
    }

    private final IdsMapper idsMapper;
    private final QueryBuilder queryBuilder;
    private final APIClient apiClient;

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties, BidRequest bidRequest,
                                                OptableAttributes attributes, long timeout) {

        return Optional.ofNullable(bidRequest)
                .map(it -> idsMapper.toIds(it, properties.getPpidMapping()))
                .map(ids -> queryBuilder.build(ids, attributes, properties.getIdPrefixOrder()))
                .map(query -> properties.getCache().isEnabled() && moduleCacheEnabled
                        ? getOrFetchTargetingResults(
                                properties.getCache(),
                                properties.getApiKey(),
                                query,
                                attributes.getIps(), timeout)
                        : apiClient.getTargeting(
                                properties.getApiKey(),
                                query,
                                attributes.getIps(),
                                timeout))
                .orElse(Future.failedFuture("Can't get targeting"));
    }

    private Future<TargetingResult> getOrFetchTargetingResults(CacheProperties cacheProperties, String apiKey,
                                                               String query, List<String> ips, long timeout) {

        final String key = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return cache.get(key)
                .recover(err -> Future.succeededFuture(null))
                .compose(entry -> entry != null
                        ? Future.succeededFuture(entry)
                        : fetchAndCacheResult(cacheProperties.getTtlseconds(), apiKey, query, ips, timeout));

    }

    private Future<TargetingResult> fetchAndCacheResult(int ttlSeconds, String apiKey, String query,
                                                        List<String> ips, long timeout) {

        return apiClient.getTargeting(apiKey, query, ips, timeout)
                .compose(result -> cache.put(query, result, ttlSeconds).map(result));
    }
}
