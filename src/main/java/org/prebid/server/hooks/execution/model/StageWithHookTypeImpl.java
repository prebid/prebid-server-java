package org.prebid.server.hooks.execution.model;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

@Accessors(fluent = true)
@Value
class StageWithHookTypeImpl<TYPE extends Hook<?, ? extends InvocationContext>> implements StageWithHookType<TYPE> {

    Stage stage;

    Class<TYPE> hookType;
}
