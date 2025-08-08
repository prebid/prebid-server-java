package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;

import java.util.Objects;

public class PerImpMatchingRule implements Rule<BidRequest, RequestRuleContext> {

    private final RequestMatchingRule delegate;

    public PerImpMatchingRule(RequestMatchingRule delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public RuleResult<BidRequest> process(BidRequest value, RequestRuleContext context) {
        return value.getImp().stream().reduce(
                RuleResult.unaltered(value),
                (result, imp) -> result.mergeWith(
                        delegate.process(
                                result.getUpdateResult().getValue(),
                                RequestRuleContext.of(
                                        context.getAuctionContext(),
                                        new Granularity.Imp(imp.getId()),
                                        context.getDatacenter()))),
                RuleResult::mergeWith);
    }
}
