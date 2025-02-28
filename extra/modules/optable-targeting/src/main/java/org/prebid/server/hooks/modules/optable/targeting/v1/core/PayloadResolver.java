package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.BidRequestBuilder;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.BidResponseBuilder;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class PayloadResolver {

    private ObjectMapper mapper;

    public BidRequest enrichBidRequest(BidRequest bidRequest, TargetingResult targetingResults) {
        if (bidRequest == null) {
            return null;
        }
        if (targetingResults == null) {
            return bidRequest;
        }

        final User user = getUser(targetingResults);
        if (user == null) {
            return bidRequest;
        }

        return BidRequestBuilder.of(bidRequest)
                .addEids(user.getEids())
                .addData(user.getData())
                .build();
    }

    public BidResponse enrichBidResponse(BidResponse bidResponse, List<Audience> targeting) {
        if (bidResponse == null) {
            return null;
        }
        if (CollectionUtils.isEmpty(targeting)) {
            return bidResponse;
        }

        return BidResponseBuilder.of(bidResponse, mapper)
                .applyTargeting(targeting)
                .build();
    }

    public BidRequest clearBidRequest(BidRequest bidRequest) {
        if (bidRequest == null) {
            return null;
        }

        return BidRequestBuilder.of(bidRequest)
                .clearExtUserOptable()
                .build();
    }

    private User getUser(TargetingResult targetingResults) {
        return Optional.ofNullable(targetingResults)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .orElse(null);
    }
}
