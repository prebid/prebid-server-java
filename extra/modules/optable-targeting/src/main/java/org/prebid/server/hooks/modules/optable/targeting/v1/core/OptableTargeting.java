package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
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
    private final boolean moduleCacheEnabled;
    private final IdsMapper idsMapper;
    private final QueryBuilder queryBuilder;
    private final APIClient apiClient;

    public OptableTargeting(Cache cache,
                            IdsMapper idsMapper,
                            QueryBuilder queryBuilder,
                            APIClient apiClient,
                            boolean moduleCacheEnabled) {

        this.cache = Objects.requireNonNull(cache);
        this.idsMapper = Objects.requireNonNull(idsMapper);
        this.queryBuilder = Objects.requireNonNull(queryBuilder);
        this.apiClient = Objects.requireNonNull(apiClient);
        this.moduleCacheEnabled = moduleCacheEnabled;
    }

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                BidRequest bidRequest,
                                                OptableAttributes attributes,
                                                long timeout) {

        return Optional.ofNullable(bidRequest)
                .map(it -> idsMapper.toIds(it, properties.getPpidMapping()))
                .map(ids -> queryBuilder.build(ids, attributes, properties.getIdPrefixOrder()))
                .map(query -> properties.getCache().isEnabled() && moduleCacheEnabled
                        ? getOrFetchTargetingResults(
                                properties.getCache(),
                                properties.getApiKey(),
                                properties.getTenant(),
                                properties.getOrigin(),
                                query,
                                attributes.getIps(), timeout)
                        : apiClient.getTargeting(
                                properties.getApiKey(),
                                properties.getTenant(),
                                properties.getOrigin(),
                                query,
                                attributes.getIps(),
                                timeout))
                .orElse(Future.failedFuture("Can't get targeting"));
    }

    private Future<TargetingResult> getOrFetchTargetingResults(CacheProperties cacheProperties,
                                                               String apiKey,
                                                               String tenant,
                                                               String origin,
                                                               Query query,
                                                               List<String> ips,
                                                               long timeout) {

        final String cachingKey = createCachingKey(tenant, origin, ips, query, true);
        return cache.get(cachingKey)
                .recover(ignore -> fetchAndCacheResult(
                        tenant,
                        origin,
                        cacheProperties.getTtlseconds(),
                        apiKey,
                        query,
                        ips,
                        timeout));
    }

    private Future<TargetingResult> fetchAndCacheResult(String tenant, String origin,
                                                        int ttlSeconds, String apiKey, Query query, List<String> ips,
                                                        long timeout) {

        final String cachingKey = createCachingKey(tenant, origin, ips, query, false);
        return apiClient.getTargeting(apiKey, tenant, origin, query, ips, timeout)
                .compose(result -> cache.put(cachingKey, result, ttlSeconds)
                        .recover(throwable -> Future.succeededFuture())
                        .map(result));
    }

    private String createCachingKey(String tenant, String origin, List<String> ips, Query query, boolean encodeQuery) {
        return "%s:%s:%s:%s".formatted(
                tenant,
                origin,
                CollectionUtils.isNotEmpty(ips) ? ips.getFirst() : "none",
                encodeQuery
                        ? URLEncoder.encode(query.getIds(), StandardCharsets.UTF_8)
                        : query.getIds());
    }
}
