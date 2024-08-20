package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.List;

public class GreenbidsRealTimeDataModule implements Module {

    public static final String CODE = "greenbids-real-time-data";

    private final List<? extends Hook<?, ? extends InvocationContext>> hooks;

    public GreenbidsRealTimeDataModule(List<? extends Hook<?, ? extends InvocationContext>> hooks) {
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
