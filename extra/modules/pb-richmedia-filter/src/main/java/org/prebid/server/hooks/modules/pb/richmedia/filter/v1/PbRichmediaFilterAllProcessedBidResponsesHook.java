package org.prebid.server.hooks.modules.pb.richmedia.filter.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.PbRichMediaFilterProperties;
import org.prebid.server.hooks.modules.pb.richmedia.filter.core.BidResponsesMraidFilter;
import org.prebid.server.hooks.modules.pb.richmedia.filter.core.ModuleConfigResolver;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.AnalyticsResult;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.MraidFilterResult;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.TagsImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesHook;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PbRichmediaFilterAllProcessedBidResponsesHook implements AllProcessedBidResponsesHook {

    private static final String CODE = "pb-richmedia-filter-all-processed-bid-responses-hook";
    private static final String ACTIVITY = "reject-richmedia";
    private static final String SUCCESS_STATUS = "success";

    private final ObjectMapper mapper;
    private final BidResponsesMraidFilter mraidFilter;
    private final ModuleConfigResolver configResolver;

    public PbRichmediaFilterAllProcessedBidResponsesHook(ObjectMapper mapper,
                                                         BidResponsesMraidFilter mraidFilter,
                                                         ModuleConfigResolver configResolver) {
        this.mapper = Objects.requireNonNull(mapper);
        this.mraidFilter = Objects.requireNonNull(mraidFilter);
        this.configResolver = Objects.requireNonNull(configResolver);
    }

    @Override
    public Future<InvocationResult<AllProcessedBidResponsesPayload>> call(
            AllProcessedBidResponsesPayload allProcessedBidResponsesPayload,
            AuctionInvocationContext auctionInvocationContext) {

        final PbRichMediaFilterProperties properties = configResolver.resolve(auctionInvocationContext.accountConfig());
        final List<BidderResponse> responses = allProcessedBidResponsesPayload.bidResponses();

        if (Boolean.TRUE.equals(properties.getFilterMraid())) {
            final MraidFilterResult filterResult = mraidFilter.filterByPattern(properties.getMraidScriptPattern(), responses);
            final InvocationAction action = filterResult.hasRejectedBids()
                        ? InvocationAction.update
                        : InvocationAction.no_action;
            return Future.succeededFuture(toInvocationResult(
                    filterResult.getFilterResult(),
                    toAnalyticsTags(filterResult.getAnalyticsResult()),
                    action));
        }

        return Future.succeededFuture(toInvocationResult(
                responses,
                toAnalyticsTags(Collections.emptyList()),
                InvocationAction.no_action));
    }

    private InvocationResult<AllProcessedBidResponsesPayload> toInvocationResult(List<BidderResponse> bidderResponses,
                                                                                 Tags analyticsTags,
                                                                                 InvocationAction action) {

        final InvocationResultImpl.InvocationResultImplBuilder<AllProcessedBidResponsesPayload> resultBuilder =
                InvocationResultImpl.<AllProcessedBidResponsesPayload>builder()
                        .status(InvocationStatus.success)
                        .action(action)
                        .analyticsTags(analyticsTags)
                        .payloadUpdate(payload -> AllProcessedBidResponsesPayloadImpl.of(bidderResponses));

        return resultBuilder.build();
    }

    private Tags toAnalyticsTags(List<AnalyticsResult> analyticsResults) {
        if (CollectionUtils.isEmpty(analyticsResults)) {
            return null;
        }

        return TagsImpl.of(Collections.singletonList(
                ActivityImpl.of(ACTIVITY, SUCCESS_STATUS, toResults(analyticsResults))));
    }

    private List<Result> toResults(List<AnalyticsResult> analyticsResults) {
        return analyticsResults.stream()
                .map(this::toResult)
                .toList();
    }

    private Result toResult(AnalyticsResult analyticsResult) {
        return ResultImpl.of(
                analyticsResult.getStatus(),
                toObjectNode(analyticsResult.getValues()),
                AppliedToImpl.builder()
                        .bidders(Collections.singletonList(analyticsResult.getBidder()))
                        .impIds(analyticsResult.getImpId())
                        .build());
    }

    private ObjectNode toObjectNode(Map<String, Object> values) {
        return values != null ? mapper.valueToTree(values) : null;
    }

    @Override
    public String code() {
        return CODE;
    }
}
