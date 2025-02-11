package org.prebid.server.hooks.modules.rule.engine.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
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

        final ArgumentExtractor<Test> isTestExtractor = test -> String.valueOf(test.test);
        final ArgumentExtractor<Test> versionExtractor = test -> String.valueOf(test.version);
        final ArgumentExtractor<Test> nameExtractor = test -> test.name;

        final RuleFactory<Imp, Imp> ruleFactory = new RuleFactory<>();

        final Rule<Imp, Imp, Test> rule = ruleFactory.buildRule(
                List.of(isTestExtractor, versionExtractor, nameExtractor),
                Map.of(
                        "false|12|hello", test -> test,
                        "true|5|test", test -> test,
                        "false|12|*", test -> test.toBuilder().id("id").build(),
                        "true|1|hello", test -> test,
                        "true|-2|aloha", test -> test,
                        "true|-2|hello", test -> test,
                        "false|*|*", test -> test));

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
                .build();
    }

    @Override
    public String code() {
        return CODE;
    }
}
