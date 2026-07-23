package org.prebid.server.hooks.modules.id5.userid.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

public class Id5IdModule implements Module {

    public static final String CODE = "id5-user-id";

    private final Collection<? extends Hook<?, ? extends InvocationContext>> hooks;

    public Id5IdModule(Collection<? extends Hook<?, ? extends InvocationContext>> hooks) {
        this.hooks = hooks;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return hooks;
    }
}
