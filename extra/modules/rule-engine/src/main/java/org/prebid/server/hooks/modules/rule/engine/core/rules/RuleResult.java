package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;

@Value(staticConstructor = "of")
public class RuleResult<T> {

    UpdateResult<T> updateResult;

    Tags analyticsTags;
}
