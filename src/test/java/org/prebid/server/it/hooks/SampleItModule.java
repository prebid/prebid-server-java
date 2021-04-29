package org.prebid.server.it.hooks;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.Module;

import java.util.Arrays;
import java.util.Collection;

public class SampleItModule implements Module {

    @Override
    public Collection<? extends Hook<?, ?>> hooks() {
        return Arrays.asList(
                new SampleItEntrypointHook(),
                new SampleItRawAuctionRequestHook(),
                new SampleItProcessedAuctionRequestHook(),
                new SampleItBidderRequestHook(),
                new SampleItRawBidderResponseHook());
    }

    @Override
    public String code() {
        return "sample-it-module";
    }
}
