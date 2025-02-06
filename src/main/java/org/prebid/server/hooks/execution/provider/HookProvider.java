package org.prebid.server.hooks.execution.provider;

import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.function.Function;

public interface HookProvider<PAYLOAD, CONTEXT extends InvocationContext>
        extends Function<HookId, Hook<PAYLOAD, CONTEXT>> {
}
