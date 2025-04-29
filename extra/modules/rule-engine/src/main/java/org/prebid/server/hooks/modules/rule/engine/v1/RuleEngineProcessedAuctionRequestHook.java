package org.prebid.server.hooks.modules.rule.engine.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestRuleParser;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.Map;
import java.util.Objects;

public class RuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "rule-engine-processed-auction-request";

    private final RequestRuleParser ruleParser;

    public RuleEngineProcessedAuctionRequestHook(RequestRuleParser ruleParser) {
        this.ruleParser = Objects.requireNonNull(ruleParser);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final Map<Stage, Rule<BidRequest>> rule = ruleParser.parse(invocationContext.accountConfig());
        final RuleResult<BidRequest> result = rule.get(Stage.processed_auction_request)
                .process(auctionRequestPayload.bidRequest());

        return Future.succeededFuture(succeeded(payload ->
                AuctionRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .ext(originalBidRequest.getExt())
                        .build())));
    }

    @Override
    public String code() {
        return CODE;
    }
}
