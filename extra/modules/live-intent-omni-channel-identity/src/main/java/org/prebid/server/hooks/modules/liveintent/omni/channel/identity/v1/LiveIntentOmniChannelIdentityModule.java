package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

public record LiveIntentOmniChannelIdentityModule(
        Collection<? extends Hook<?, ? extends InvocationContext>> hooks) implements Module {

    public static final String CODE = "liveintent-omni-channel-identity";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return hooks;
    }
}
