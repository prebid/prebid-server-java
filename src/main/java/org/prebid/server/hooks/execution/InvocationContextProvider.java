package org.prebid.server.hooks.execution;

import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.v1.InvocationContext;

@FunctionalInterface
interface InvocationContextProvider<CONTEXT extends InvocationContext> {

    CONTEXT apply(Long timeout, HookId hookId, Object moduleContext);
}
