package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import org.prebid.server.hooks.v1.Module;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.Collection;
import java.util.List;

public class WURFLDeviceDetectionModule implements Module {

    public static final String CODE = "wurfl-devicedetection";

    private final List<? extends Hook<?, ? extends InvocationContext>> hooks;

    public WURFLDeviceDetectionModule(List<? extends Hook<?, ? extends InvocationContext>> hooks) {
        this.hooks = hooks;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return this.hooks;
    }
}
