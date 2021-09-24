package org.prebid.server.hooks.execution.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

@AllArgsConstructor
@Getter
@Accessors(fluent = true)
class StageWithHookTypeImpl<TYPE extends Hook<?, ? extends InvocationContext>> implements StageWithHookType<TYPE> {

    private final Stage stage;

    private final Class<TYPE> hookType;
}
