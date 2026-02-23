package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;

import java.util.List;
import java.util.Objects;

public class OptableTargeting {

    private final IdsMapper idsMapper;
    private final APIClient apiClient;

    public OptableTargeting(IdsMapper idsMapper, APIClient apiClient) {
        this.idsMapper = Objects.requireNonNull(idsMapper);
        this.apiClient = Objects.requireNonNull(apiClient);
    }

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                BidRequest bidRequest,
                                                OptableAttributes attributes,
                                                Timeout timeout) {

        final List<Id> ids = idsMapper.toIds(bidRequest, properties.getPpidMapping());
        final Query query = QueryBuilder.build(ids, attributes, properties.getIdPrefixOrder());
        if (query == null) {
            return Future.failedFuture("Can't get targeting");
        }

        return apiClient.getTargeting(properties, query, attributes.getIps(), attributes.getUserAgent(), timeout);
    }
}
