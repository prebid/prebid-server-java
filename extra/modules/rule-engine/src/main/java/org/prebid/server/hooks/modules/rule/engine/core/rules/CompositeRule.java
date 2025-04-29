package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.model.UpdateResult;

import java.util.ArrayList;
import java.util.List;

@Value(staticConstructor = "of")
public class CompositeRule<T> implements Rule<T> {

    List<Rule<T>> subrules;

    @Override
    public RuleResult<T> process(T value) {
        final List<Activity> activities = new ArrayList<>();
        T modifiedValue = value;
        boolean updated = false;

        for (Rule<T> subrule : subrules) {
            final RuleResult<T> subresult = subrule.process(modifiedValue);
            modifiedValue = subresult.getUpdateResult().getValue();
            updated = updated | subresult.getUpdateResult().isUpdated();
            activities.addAll(subresult.getAnalyticsTags().activities());
        }

        return RuleResult.of(UpdateResult.of(updated, modifiedValue), TagsImpl.of(activities));
    }
}
