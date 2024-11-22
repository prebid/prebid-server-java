package org.prebid.server.hooks.execution.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.Objects;

public abstract class HookDecorator<PAYLOAD, CONTEXT extends InvocationContext> implements Hook<PAYLOAD, CONTEXT> {

    protected final Hook<PAYLOAD, CONTEXT> hook;

    public HookDecorator(Hook<PAYLOAD, CONTEXT> hook) {
        this.hook = Objects.requireNonNull(hook);
    }

    @Override
    public String code() {
        return hook.code();
    }
}
