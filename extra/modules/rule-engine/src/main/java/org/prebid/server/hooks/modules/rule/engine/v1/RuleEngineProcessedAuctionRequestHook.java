package org.prebid.server.hooks.modules.rule.engine.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.ArgumentExtractor;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleFactory;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.List;
import java.util.Map;

public class RuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "rule-engine-processed-auction-request";

    @Value
    @Builder(toBuilder = true)
    private static class Test {

        String name;

        int version;

        boolean test;
    }

    @Value
    private static class RuleContext {

        BidRequest.BidRequestBuilder bidRequest;

        Imp imp;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest originalBidRequest = auctionRequestPayload.bidRequest();
        final List<Imp> imps = ListUtils.emptyIfNull(originalBidRequest.getImp());

        final RuleFactory<Imp, Imp> ruleFactory = new RuleFactory<>();
        final Rule<Imp, RequestRuleResult, RequestRuleContext> rule = null;

        for (Imp imp : imps) {
            RequestRuleResult result = rule.apply(RequestRuleContext.of(false, imp, originalBidRequest));
        }
        final ArgumentExtractor<Test> isTestExtractor = test -> String.valueOf(test.test);
        final ArgumentExtractor<Test> versionExtractor = test -> String.valueOf(test.version);
        final ArgumentExtractor<Test> nameExtractor = test -> test.name;



        final Imp mutated = rule
                .apply(new Test("bruh", 12, false))
                .apply(Imp.builder().build());

        System.out.println(mutated);

        return Future.succeededFuture(succeeded(payload ->
                AuctionRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .ext(originalBidRequest.getExt())
                        .build())));
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> succeeded(PayloadUpdate<PAYLOAD> payloadUpdate) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payloadUpdate)
                .analyticsTags()
                .build();
    }

    @Override
    public String code() {
        return CODE;
    }
}
