package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.function.Predicate;

@FunctionalInterface
public interface AccountControl extends Predicate<AuctionInvocationContext> {
    default boolean isAllowed(AuctionInvocationContext auctionInvocationContext) {
        return test(auctionInvocationContext);
    }
}
