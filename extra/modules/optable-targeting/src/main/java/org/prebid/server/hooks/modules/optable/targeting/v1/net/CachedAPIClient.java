package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.Cache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class CachedAPIClient implements APIClient {

    private final APIClient apiClient;
    private final Cache cache;

    public CachedAPIClient(APIClient apiClient, Cache cache) {
        this.apiClient = Objects.requireNonNull(apiClient);
        this.cache = cache;
    }

    @Override
    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                Query query,
                                                List<String> ips,
                                                long timeout) {

        final String cachingKey = createCachingKey(
                properties.getTenant(),
                properties.getOrigin(),
                ips,
                query,
                true);
        return cache.get(cachingKey)
                .recover(ignore -> fetchAndCacheResult(
                        properties,
                        properties.getCache().getTtlseconds(),
                        query,
                        ips,
                        timeout));
    }

    private Future<TargetingResult> fetchAndCacheResult(OptableTargetingProperties properties,
                                                        int ttlSeconds, Query query, List<String> ips,
                                                        long timeout) {

        final String cachingKey = createCachingKey(
                properties.getTenant(),
                properties.getOrigin(),
                ips,
                query,
                false);
        return apiClient.getTargeting(properties, query, ips, timeout)
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
