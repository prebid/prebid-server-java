package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClientFactory;

import java.util.Objects;
import java.util.Optional;

public class OptableTargeting {

    private final IdsMapper idsMapper;
    private final QueryBuilder queryBuilder;
    private final APIClientFactory apiClientFactory;

    public OptableTargeting(IdsMapper idsMapper,
                            QueryBuilder queryBuilder,
                            APIClientFactory apiClientFactory) {

        this.idsMapper = Objects.requireNonNull(idsMapper);
        this.queryBuilder = Objects.requireNonNull(queryBuilder);
        this.apiClientFactory = Objects.requireNonNull(apiClientFactory);
    }

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                BidRequest bidRequest,
                                                OptableAttributes attributes,
                                                long timeout) {

        return Optional.ofNullable(bidRequest)
                .map(it -> idsMapper.toIds(it, properties.getPpidMapping()))
                .map(ids -> queryBuilder.build(ids, attributes, properties.getIdPrefixOrder()))
                .map(query ->
                        apiClientFactory.getClient(properties.getCache().isEnabled()).getTargeting(
                                properties,
                                query,
                                attributes.getIps(),
                                timeout))
                .orElse(Future.failedFuture("Can't get targeting"));
    }
}
