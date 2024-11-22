package org.prebid.server.hooks.execution.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.provider.HookProvider;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;

import java.util.Objects;

public record LazyHook<PAYLOAD, CONTEXT extends InvocationContext>(HookId hookId,
                                                                   HookProvider<PAYLOAD, CONTEXT> hookProvider)
        implements Hook<PAYLOAD, CONTEXT> {

    public LazyHook(HookId hookId, HookProvider<PAYLOAD, CONTEXT> hookProvider) {
        this.hookId = Objects.requireNonNull(hookId);
        this.hookProvider = Objects.requireNonNull(hookProvider);
    }

    @Override
    public String code() {
        return hookId.getHookImplCode();
    }

    @Override
    public Future<InvocationResult<PAYLOAD>> call(PAYLOAD payload, CONTEXT invocationContext) {
        return hookProvider.apply(hookId).call(payload, invocationContext);
    }
}
