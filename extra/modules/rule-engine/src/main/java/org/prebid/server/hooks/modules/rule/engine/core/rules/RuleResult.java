package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.util.ListUtil;

import java.util.Collections;

@Value(staticConstructor = "of")
public class RuleResult<T> {

    UpdateResult<T> updateResult;

    Tags analyticsTags;

    public RuleResult<T> mergeWith(RuleResult<T> other) {
        final boolean updated = other.updateResult.isUpdated() || updateResult.isUpdated();
        final T value = other.updateResult.getValue();
        final Tags tags = TagsImpl.of(ListUtil.union(other.analyticsTags.activities(), analyticsTags.activities()));

        return RuleResult.of(UpdateResult.of(updated, value), tags);
    }

    public static <T> RuleResult<T> unaltered(T value) {
        return RuleResult.of(UpdateResult.unaltered(value), TagsImpl.of(Collections.emptyList()));
    }
}
