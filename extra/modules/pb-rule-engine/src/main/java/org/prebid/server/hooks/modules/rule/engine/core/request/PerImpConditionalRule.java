package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.modules.rule.engine.core.rules.ConditionalRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;

import java.util.Objects;

public class PerImpConditionalRule implements Rule<BidRequest, RequestRuleContext> {

    private final ConditionalRule<BidRequest, RequestRuleContext> delegate;

    public PerImpConditionalRule(ConditionalRule<BidRequest, RequestRuleContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest value, RequestRuleContext context) {
        RuleResult<BidRequest> result = RuleResult.unaltered(value);
        for (Imp imp : value.getImp()) {
            result = result.mergeWith(delegate.process(result.getValue(), contextForImp(context, imp)));

            if (result.isReject())
                return result;
        }

        return result;
    }

    private RequestRuleContext contextForImp(RequestRuleContext context, Imp imp) {
        return RequestRuleContext.of(
                context.getAuctionContext(),
                new Granularity.Imp(imp.getId()),
                context.getDatacenter());
    }
}
