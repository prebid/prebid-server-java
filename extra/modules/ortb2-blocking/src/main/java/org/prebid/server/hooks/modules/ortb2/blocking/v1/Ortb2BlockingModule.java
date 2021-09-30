package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Arrays;
import java.util.Collection;

public class Ortb2BlockingModule implements Module {

    public static final String CODE = "ortb2-blocking";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new Ortb2BlockingBidderRequestHook(),
                new Ortb2BlockingRawBidderResponseHook());
    }
}
