package org.prebid.server.auction.bidderrequestpostprocessor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Collections;

public class BidderRequestCleaner implements BidderRequestPostProcessor {

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        final BidRequest bidRequest = bidderRequest.getBidRequest();
        final ExtRequest ext = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ext != null ? ext.getPrebid() : null;
        final ObjectNode bidderControls = extPrebid != null ? extPrebid.getBiddercontrols() : null;

        if (bidderControls == null) {
            return resultOf(bidderRequest);
        }

        final ExtRequest cleanedExt = ExtRequest.of(extPrebid.toBuilder().biddercontrols(null).build());
        cleanedExt.addProperties(ext.getProperties());

        return resultOf(bidderRequest.with(bidRequest.toBuilder().ext(cleanedExt).build()));
    }

    private static Future<Result<BidderRequest>> resultOf(BidderRequest bidderRequest) {
        return Future.succeededFuture(Result.of(bidderRequest, Collections.emptyList()));
    }
}
