package org.prebid.server.hooks.modules.ortb2.blocking.v1.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Map;

@Accessors(fluent = true)
@Builder
@Value
public class BidderInvocationContextImpl implements BidderInvocationContext {

    Timeout timeout;

    Endpoint endpoint;

    AuctionContext auctionContext;

    Object moduleContext;

    boolean debugEnabled;

    ObjectNode accountConfig;

    String bidder;

    public static BidderInvocationContext of(String bidder,
                                             BidRejectionTracker bidRejectionTracker,
                                             ObjectNode accountConfig,
                                             boolean debugEnabled) {

        return BidderInvocationContextImpl.builder()
                .bidder(bidder)
                .auctionContext(AuctionContext.builder()
                        .bidRequest(BidRequest.builder().build())
                        .bidRejectionTrackers(Map.of(bidder, bidRejectionTracker))
                        .build())
                .accountConfig(accountConfig)
                .debugEnabled(debugEnabled)
                .build();
    }

    public static BidderInvocationContext of(String bidder,
                                             Map<String, String> aliases,
                                             BidRejectionTracker bidRejectionTracker,
                                             ObjectNode accountConfig,
                                             boolean debugEnabled) {

        return BidderInvocationContextImpl.builder()
                .bidder(bidder)
                .auctionContext(AuctionContext.builder()
                        .bidRequest(BidRequest.builder()
                                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .aliases(aliases)
                                        .build()))
                                .build())
                        .bidRejectionTrackers(Map.of(bidder, bidRejectionTracker))
                        .build())
                .accountConfig(accountConfig)
                .debugEnabled(debugEnabled)
                .build();
    }
}
