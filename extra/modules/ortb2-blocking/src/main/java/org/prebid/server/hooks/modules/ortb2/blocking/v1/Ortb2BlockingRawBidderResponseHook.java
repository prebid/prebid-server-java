package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.hooks.modules.ortb2.blocking.core.BidsBlocker;
import org.prebid.server.hooks.modules.ortb2.blocking.core.ResponseUpdater;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.AnalyticsResult;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedBids;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ExecutionResult;
import org.prebid.server.hooks.modules.ortb2.blocking.model.ModuleContext;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.BidderResponsePayloadImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.analytics.TagsImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Ortb2BlockingRawBidderResponseHook implements RawBidderResponseHook {

    private static final String CODE = "ortb2-blocking-raw-bidder-response";

    private static final String ENFORCE_BLOCKING_ACTIVITY = "enforce-blocking";
    private static final String SUCCESS_STATUS = "success";

    private final ObjectMapper mapper;

    public Ortb2BlockingRawBidderResponseHook(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(BidderResponsePayload bidderResponsePayload,
                                                                BidderInvocationContext invocationContext) {

        final String bidder = invocationContext.bidder();
        final ModuleContext moduleContext = moduleContext(invocationContext);

        final ExecutionResult<BlockedBids> blockedBidsResult = BidsBlocker
                .create(
                        bidderResponsePayload.bids(),
                        bidder,
                        ObjectUtils.defaultIfNull(moduleContext.ortbVersionOf(bidder), OrtbVersion.ORTB_2_5),
                        invocationContext.accountConfig(),
                        moduleContext.blockedAttributesFor(bidder),
                        invocationContext.debugEnabled())
                .block();

        final InvocationResultImpl.InvocationResultImplBuilder<BidderResponsePayload> resultBuilder =
                InvocationResultImpl.<BidderResponsePayload>builder()
                        .status(InvocationStatus.success)
                        .action(blockedBidsResult.hasValue()
                                ? InvocationAction.update
                                : InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .errors(blockedBidsResult.getErrors())
                        .warnings(blockedBidsResult.getWarnings())
                        .debugMessages(blockedBidsResult.getDebugMessages())
                        .analyticsTags(toAnalyticsTags(blockedBidsResult.getAnalyticsResults()));

        if (blockedBidsResult.hasValue()) {
            final ResponseUpdater responseUpdater = ResponseUpdater.create(blockedBidsResult.getValue());
            resultBuilder
                    .payloadUpdate(payload -> BidderResponsePayloadImpl.of(responseUpdater.update(payload.bids())));
        }

        return Future.succeededFuture(resultBuilder.build());
    }

    @Override
    public String code() {
        return CODE;
    }

    private static ModuleContext moduleContext(BidderInvocationContext invocationContext) {
        return invocationContext.moduleContext() instanceof ModuleContext moduleContext
                ? moduleContext
                : ModuleContext.create();
    }

    private Tags toAnalyticsTags(List<AnalyticsResult> analyticsResults) {
        if (CollectionUtils.isEmpty(analyticsResults)) {
            return null;
        }

        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                ENFORCE_BLOCKING_ACTIVITY,
                SUCCESS_STATUS,
                toResults(analyticsResults))));
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
                        .impIds(Collections.singletonList(analyticsResult.getImpId()))
                        .build());
    }

    private ObjectNode toObjectNode(Map<String, Object> values) {
        return values != null ? mapper.valueToTree(values) : null;
    }
}
