package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

public record FiftyOneDeviceDetectionModule(
        Collection<? extends Hook<?, ? extends InvocationContext>> hooks
) implements Module {
    public static final String CODE = "fiftyone-devicedetection";

    @Override
    public String code() {

        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {

        return hooks;
    }
}
