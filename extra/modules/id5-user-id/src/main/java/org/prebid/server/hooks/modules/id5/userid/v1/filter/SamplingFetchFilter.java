package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Objects;
import java.util.function.Supplier;

public class SamplingFetchFilter implements FetchActionFilter {

    private final double sampleRate;
    private final Supplier<Double> randomSupplier;

    public SamplingFetchFilter(Supplier<Double> randomSupplier, double sampleRate) {
        this.sampleRate = sampleRate;
        this.randomSupplier = Objects.requireNonNull(randomSupplier);
    }

    @Override
    public FilterResult shouldInvoke(AuctionRequestPayload payload, AuctionInvocationContext invocationContext) {
        return randomSupplier.get() <= sampleRate
                ? FilterResult.accepted()
                : FilterResult.rejected("rejected by sampling");
    }
}
