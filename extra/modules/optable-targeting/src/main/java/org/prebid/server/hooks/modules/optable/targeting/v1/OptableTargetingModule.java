package org.prebid.server.hooks.modules.optable.targeting.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

public class OptableTargetingModule implements Module {

    public static final String CODE = "optable-targeting";

    private final Collection<? extends Hook<?, ? extends InvocationContext>> hooks;

    public OptableTargetingModule(Collection<? extends Hook<?, ? extends InvocationContext>> hooks) {
        this.hooks = hooks;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return hooks;
    }

    @Override
    public String code() {
        return CODE;
    }
}
