package org.prebid.server.hooks.modules.rule.engine.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestRuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.ArrayList;
import java.util.List;

public class RuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "rule-engine-processed-auction-request";

    @Value
    @Builder(toBuilder = true)
    private static class Test {

        String name;

        int version;

        boolean test;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final BidRequest originalBidRequest = auctionRequestPayload.bidRequest();

        final BidRequest bidRequest = auctionRequestPayload.bidRequest();

        final RuleTree<ResultFunctionHolder<BidRequest, RequestRuleResult>> ruleTree = null;
        final List<SchemaFunctionHolder<BidRequest>> schemaFunctions = new ArrayList<>();

        for (Imp imp : imps) {
            final List<String> schema = schemaFunctions.stream()
                    .map(holder -> holder.getSchemaFunction().extract(
                            SchemaFunctionArguments.of(
                                    bidRequest, holder.getArguments(), false, imp)))
                    .toList();

            final ResultFunctionHolder<BidRequest, RequestRuleResult> result = ruleTree.getValue(schema);
        }

        final Imp mutated = rule
                .apply(new Test("bruh", 12, false))
                .apply(Imp.builder().build());

        System.out.println(mutated);

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
