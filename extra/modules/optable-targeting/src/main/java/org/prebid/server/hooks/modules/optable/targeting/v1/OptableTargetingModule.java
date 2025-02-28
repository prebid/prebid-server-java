package org.prebid.server.hooks.modules.optable.targeting.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

public record OptableTargetingModule(
        Collection<? extends Hook<?, ? extends InvocationContext>> hooks) implements Module {

    public static final String CODE = "optable-targeting";

    @Override
    public String code() {
        return CODE;
    }
}
