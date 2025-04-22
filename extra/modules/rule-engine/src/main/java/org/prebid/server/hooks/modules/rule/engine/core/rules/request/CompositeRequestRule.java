package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.model.UpdateResult;

import java.util.ArrayList;
import java.util.List;

@Value(staticConstructor = "of")
public class CompositeRequestRule implements RequestRule {

    List<RequestRule> subrules;

    @Override
    public RequestRuleResult process(BidRequest bidRequest) {
        final List<Activity> activities = new ArrayList<>();
        BidRequest modifiedRequest = bidRequest;
        boolean updated = false;

        for (RequestRule subrule : subrules) {
            final RequestRuleResult subresult = subrule.process(modifiedRequest);
            modifiedRequest = subresult.getBidRequest().getValue();
            updated = updated | subresult.getBidRequest().isUpdated();
            activities.addAll(subresult.getAnalyticsTags().activities());
        }

        return RequestRuleResult.of(UpdateResult.of(updated, modifiedRequest), TagsImpl.of(activities));
    }
}
