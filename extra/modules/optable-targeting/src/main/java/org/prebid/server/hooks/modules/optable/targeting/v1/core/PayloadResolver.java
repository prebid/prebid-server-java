package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.BidRequestBuilder;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.BidResponseResolver;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PayloadResolver {

    private ObjectMapper mapper;

    public PayloadResolver(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public BidRequest enrichBidRequest(BidRequest bidRequest, TargetingResult targetingResults) {
        if (bidRequest == null || targetingResults == null) {
            return bidRequest;
        }

        final User user = getUser(targetingResults);
        if (user == null) {
            return bidRequest;
        }

        return new BidRequestBuilder(bidRequest)
                .addEids(user.getEids())
                .addData(user.getData())
                .build();
    }

    public BidResponse enrichBidResponse(BidResponse bidResponse, List<Audience> targeting) {
        if (bidResponse == null || CollectionUtils.isEmpty(targeting)) {
            return bidResponse;
        }

        return BidResponseResolver.of(bidResponse, mapper).applyTargeting(targeting);
    }

    public BidRequest clearBidRequest(BidRequest bidRequest) {
        return bidRequest != null
                ? new BidRequestBuilder(bidRequest).clearExtUserOptable().build()
                : null;
    }

    private static User getUser(TargetingResult targetingResults) {
        return Optional.ofNullable(targetingResults)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .orElse(null);
    }
}
