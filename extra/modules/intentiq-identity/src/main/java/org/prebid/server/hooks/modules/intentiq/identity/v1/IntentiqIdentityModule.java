package org.prebid.server.hooks.modules.intentiq.identity.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

public record IntentiqIdentityModule(
        Collection<? extends Hook<?, ? extends InvocationContext>> hooks) implements Module {
    public static final String CODE = "intentiq-identity";

    @Override
    public String code() {
        return CODE;
    }
}
