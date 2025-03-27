package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;

import java.util.Objects;
import java.util.Optional;

public class OptableTargeting {

    public OptableTargeting(IdsMapper idsMapper, QueryBuilder queryBuilder, APIClient apiClient) {
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
                .map(query -> apiClient.getTargeting(query, attributes.getIps(), timeout))
                .orElse(Future.failedFuture("Can't get targeting"));
    }
}
