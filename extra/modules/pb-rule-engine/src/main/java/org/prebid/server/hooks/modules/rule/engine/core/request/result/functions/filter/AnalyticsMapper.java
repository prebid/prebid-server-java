package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestResultContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

public class AnalyticsMapper {

    private static final String ACTIVITY_NAME = "rules-filter";
    private static final String SUCCESS_STATUS = "success";

    private AnalyticsMapper() {
    }

    public static Tags toTags(ObjectMapper mapper,
                              String functionName,
                              List<SeatNonBid> seatNonBids,
                              InfrastructureArguments<RequestResultContext> infrastructureArguments,
                              String analyticsValue) {

        final String analyticsKey = infrastructureArguments.getAnalyticsKey();
        if (StringUtils.isEmpty(analyticsKey)) {
            return TagsImpl.of(Collections.emptyList());
        }

        final List<String> removedBidders = seatNonBids.stream()
                .map(SeatNonBid::getSeat)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(removedBidders)) {
            return TagsImpl.of(Collections.emptyList());
        }

        final List<String> impIds =
                infrastructureArguments.getContext().getGranularity() instanceof Granularity.Request
                        ? Collections.singletonList("*")
                        : seatNonBids.stream()
                        .flatMap(seatNonBid -> seatNonBid.getNonBid().stream())
                        .map(NonBid::getImpId)
                        .distinct()
                        .toList();

        final BidRejectionReason reason = seatNonBids.stream()
                .flatMap(seatNonBid -> seatNonBid.getNonBid().stream())
                .map(NonBid::getStatusCode)
                .findAny()
                .orElse(null);

        final AnalyticsData analyticsData = new AnalyticsData(
                analyticsKey,
                analyticsValue,
                infrastructureArguments.getModelVersion(),
                infrastructureArguments.getRuleFired(),
                functionName,
                removedBidders,
                reason);

        final Result result = ResultImpl.of(
                SUCCESS_STATUS, mapper.valueToTree(analyticsData), AppliedToImpl.builder().impIds(impIds).build());

        return TagsImpl.of(Collections.singletonList(
                ActivityImpl.of(ACTIVITY_NAME, SUCCESS_STATUS, Collections.singletonList(result))));
    }


    private record AnalyticsData(@JsonProperty("analyticsKey") String analyticsKey,
                                 @JsonProperty("analyticsValue") String analyticsValue,
                                 @JsonProperty("modelVersion") String modelVersion,
                                 @JsonProperty("conditionFired") String conditionFired,
                                 @JsonProperty("resultFunction") String resultFunction,
                                 @JsonProperty("biddersRemoved") List<String> biddersRemoved,
                                 @JsonProperty("seatNonBid") BidRejectionReason seatNonBid) {
    }
}
