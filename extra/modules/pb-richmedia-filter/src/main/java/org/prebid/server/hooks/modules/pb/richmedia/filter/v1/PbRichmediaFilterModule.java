package org.prebid.server.hooks.modules.pb.richmedia.filter.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.List;

public class PbRichmediaFilterModule implements Module {

    public static final String CODE = "pb-richmedia-filter";

    private final List<? extends Hook<?, ? extends InvocationContext>> hooks;

    public PbRichmediaFilterModule(List<? extends Hook<?, ? extends InvocationContext>> hooks) {
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
