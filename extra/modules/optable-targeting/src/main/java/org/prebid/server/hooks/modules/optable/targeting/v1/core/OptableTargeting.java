package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class OptableTargeting {

    private final Cache cache;

    public OptableTargeting(Cache cache, IdsMapper idsMapper, QueryBuilder queryBuilder, APIClient apiClient) {
        this.cache = cache;
        this.idsMapper = Objects.requireNonNull(idsMapper);
        this.queryBuilder = Objects.requireNonNull(queryBuilder);
        this.apiClient = Objects.requireNonNull(apiClient);
    }

    private final IdsMapper idsMapper;
    private final QueryBuilder queryBuilder;
    private final APIClient apiClient;

    public Future<TargetingResult> getTargeting(BidRequest bidRequest, OptableAttributes attributes, long timeout) {
        return Optional.ofNullable(bidRequest)
                .map(idsMapper::toIds)
                .map(ids -> queryBuilder.build(ids, attributes))
                .map(query -> cache.isEnabled()
                        ? getTargetingWithCache(query, attributes.getIps(), timeout)
                        : apiClient.getTargeting(query, attributes.getIps(), timeout) )
                .orElse(Future.failedFuture("Can't get targeting"));
    }

    private Future<TargetingResult> getTargetingWithCache(String query, List<String> ips, long timeout) {
            final String key = URLEncoder.encode(query, StandardCharsets.UTF_8);
            return cache.get(key)
                    .compose(it -> it == null
                            ? callAPIAndCacheResult(query, ips, timeout)
                            : Future.succeededFuture(it))
                    .recover(throwable -> callAPIAndCacheResult(query, ips, timeout));
    }

    private Future<TargetingResult> callAPIAndCacheResult(String query, List<String> ips, long timeout) {
        return apiClient.getTargeting(query, ips, timeout)
                .onComplete(targetingResultAsyncResult ->
                        cache.put(query, targetingResultAsyncResult.result()));
    }
}
