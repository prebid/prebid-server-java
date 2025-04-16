package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Value;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;

@Value(staticConstructor = "of")
public class ResultFunctionResult<T> {

    UpdateResult<T> updateResult;

    Tags analyticsTags;
}
