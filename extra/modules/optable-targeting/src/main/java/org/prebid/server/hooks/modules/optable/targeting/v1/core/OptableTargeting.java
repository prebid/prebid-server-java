package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;

import java.util.Optional;

@AllArgsConstructor
public class OptableTargeting {

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
