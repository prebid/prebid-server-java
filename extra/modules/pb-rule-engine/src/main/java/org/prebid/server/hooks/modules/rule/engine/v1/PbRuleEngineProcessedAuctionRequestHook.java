package org.prebid.server.hooks.modules.rule.engine.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.ImpRejection;
import org.prebid.server.auction.model.Rejection;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.config.RuleParser;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PbRuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "pb-rule-engine-processed-auction-request";

    private final RuleParser ruleParser;
    private final String datacenter;

    public PbRuleEngineProcessedAuctionRequestHook(RuleParser ruleParser, String datacenter) {
        this.ruleParser = Objects.requireNonNull(ruleParser);
        this.datacenter = Objects.requireNonNull(datacenter);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final AuctionContext context = invocationContext.auctionContext();
        final String accountId = StringUtils.defaultString(invocationContext.auctionContext().getAccount().getId());
        final ObjectNode accountConfig = invocationContext.accountConfig();
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();

        if (accountConfig == null) {
            return succeeded(RuleResult.noAction(bidRequest));
        }

        return ruleParser.parseForAccount(accountId, accountConfig)
                .map(PerStageRule::processedAuctionRequestRule)
                .map(rule -> rule.process(
                        bidRequest, RequestRuleContext.of(context, Granularity.Request.instance(), datacenter)))
                .flatMap(PbRuleEngineProcessedAuctionRequestHook::succeeded)
                .recover(PbRuleEngineProcessedAuctionRequestHook::failure);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> succeeded(RuleResult<BidRequest> result) {
        final InvocationResultImpl.InvocationResultImplBuilder<AuctionRequestPayload> resultBuilder =
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(toInvocationAction(result.getAction()))
                        .rejections(toRejections(result.getSeatNonBid()))
                        .analyticsTags(result.getAnalyticsTags());

        if (result.isUpdate()) {
            resultBuilder.payloadUpdate(initialPayload -> AuctionRequestPayloadImpl.of(result.getValue()));
        }

        return Future.succeededFuture(resultBuilder.build());
    }

    private static InvocationAction toInvocationAction(RuleAction ruleAction) {
        return switch (ruleAction) {
            case NO_ACTION -> InvocationAction.no_action;
            case UPDATE -> InvocationAction.update;
            case REJECT -> InvocationAction.reject;
        };
    }

    private static List<Rejection> toRejections(SeatNonBid seatNonBid) {
        return seatNonBid.getNonBid().stream()
                .map(nonBid -> (Rejection) ImpRejection.of(nonBid.getImpId(), nonBid.getStatusCode()))
                .toList();
    }

    private static Map<String, List<Rejection>> toRejections(List<SeatNonBid> seatNonBids) {
        return seatNonBids.stream()
                .collect(Collectors.groupingBy(SeatNonBid::getSeat,
                        Collectors.flatMapping(
                                seatNonBid -> toRejections(seatNonBid).stream(),
                                Collectors.toList())));
    }

    private static Future<InvocationResult<AuctionRequestPayload>> failure(Throwable error) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.failure)
                        .action(InvocationAction.no_invocation)
                        .message(error.getMessage())
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
