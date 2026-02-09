package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Random;

public class SamplingFetchFilter implements FetchActionFilter {

    private final double sampleRate;
    private final Random random;

    public SamplingFetchFilter(Random random, double sampleRate) {
        this.sampleRate = sampleRate;
        this.random = random;
    }

    @Override
    public FilterResult shouldInvoke(AuctionRequestPayload payload, AuctionInvocationContext invocationContext) {
        final boolean shouldInvoke = random.nextDouble() <= sampleRate;
        return shouldInvoke ? FilterResult.accepted() : FilterResult.rejected("rejected by sampling");
    }
}
