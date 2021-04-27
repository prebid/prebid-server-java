package org.prebid.server.it.hooks;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;

import static java.util.Collections.singletonList;

public class SampleItModule implements Module {

    @Override
    public Collection<? extends Hook<?, ?>> hooks() {
        return singletonList(new SampleItRawAuctionRequestHook());
    }

    @Override
    public String code() {
        return "sample-it-module";
    }
}
