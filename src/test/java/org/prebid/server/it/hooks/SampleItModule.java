package org.prebid.server.it.hooks;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;

import java.util.Arrays;
import java.util.Collection;

public class SampleItModule implements Module {

    static final String MODULE_EXT = "sample-it-module";

    private final JacksonMapper mapper;

    public SampleItModule(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Collection<? extends Hook<?, ?>> hooks() {
        return Arrays.asList(
                new SampleItEntrypointHook(),
                new SampleItRawAuctionRequestHook(mapper),
                new SampleItProcessedAuctionRequestHook(mapper),
                new SampleItBidderRequestHook(),
                new SampleItRawBidderResponseHook(),
                new SampleItProcessedBidderResponseHook(),
                new SampleItAuctionResponseHook(),
                new SampleItRejectingRawAuctionRequestHook(),
                new SampleItRejectingProcessedAuctionRequestHook(),
                new SampleItRejectingBidderRequestHook(),
                new SampleItRejectingRawBidderResponseHook(),
                new SampleItRejectingProcessedBidderResponseHook());
    }

    @Override
    public String code() {
        return "sample-it-module";
    }
}
