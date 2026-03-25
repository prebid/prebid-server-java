package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.List;

public class ConfiantAdQualityModule implements Module {

    public static final String CODE = "confiant-ad-quality";

    private final List<? extends Hook<?, ? extends InvocationContext>> hooks;

    public ConfiantAdQualityModule(List<? extends Hook<?, ? extends InvocationContext>> hooks) {
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
