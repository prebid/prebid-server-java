package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.Objects;
import java.util.random.RandomGenerator;

public class WeightedRequestRule implements RequestRule {

    private final RandomGenerator random;
    private final WeightedList<RequestRule> weightedList;

    public WeightedRequestRule(RandomGenerator random, WeightedList<RequestRule> weightedList) {
        this.random = Objects.requireNonNull(random);
        this.weightedList = Objects.requireNonNull(weightedList);
    }

    @Override
    public RequestRuleResult process(BidRequest bidRequest) {
        return weightedList.getForSeed(random.nextDouble()).process(bidRequest);
    }
}
