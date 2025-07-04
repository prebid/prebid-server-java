package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.CacheProperties;
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
        this.cache = Objects.requireNonNull(cache);
    }

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                Query query,
                                                List<String> ips,
                                                Timeout timeout) {

        final CacheProperties cacheProperties = properties.getCache();
        if (!cacheProperties.isEnabled()) {
            return apiClient.getTargeting(properties, query, ips, timeout);
        }

        final String tenant = properties.getTenant();
        final String origin = properties.getOrigin();

        return cache.get(createCachingKey(tenant, origin, ips, query, true))
                .recover(ignore -> apiClient.getTargeting(properties, query, ips, timeout)
                        .recover(throwable -> Future.succeededFuture())
                        .compose(result -> cache.put(
                                createCachingKey(tenant, origin, ips, query, false),
                                        result,
                                        cacheProperties.getTtlseconds())
                                .otherwiseEmpty()
                                .map(result)));
    }

    private String createCachingKey(String tenant, String origin, List<String> ips, Query query, boolean encodeQuery) {
        return "%s:%s:%s:%s".formatted(
                tenant,
                origin,
                ips.getFirst(),
                encodeQuery
                        ? URLEncoder.encode(query.getIds(), StandardCharsets.UTF_8)
                        : query.getIds());
    }
}
