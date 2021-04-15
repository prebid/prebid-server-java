package org.prebid.server.hooks.execution;

import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.function.BiFunction;

public interface InvocationContextProvider<CONTEXT extends InvocationContext>
        extends BiFunction<Long, HookId, CONTEXT> {

}
