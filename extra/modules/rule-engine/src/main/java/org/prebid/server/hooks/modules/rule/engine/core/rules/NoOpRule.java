package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.NoArgsConstructor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;

@NoArgsConstructor
public class NoOpRule<T, C> implements Rule<T, C> {

    @Override
    public RuleResult<T> process(T value, C context) {
        return RuleResult.of(UpdateResult.unaltered(value), TagsImpl.of(Collections.emptyList()));
    }
}
