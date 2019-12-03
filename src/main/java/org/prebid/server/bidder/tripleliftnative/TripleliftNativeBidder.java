package org.prebid.server.bidder.tripleliftnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.request.tripleliftnative.ExtImpTripleliftNative;

import java.util.List;
import java.util.Map;

public class TripleliftNativeBidder implements Bidder<BidRequest> {

    public TripleliftNativeBidder(String endpointUrl, Map<String, String> extraInfo) {
        System.out.println(extraInfo);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        return null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return null;
    }
}
