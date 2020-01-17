package org.prebid.server.bidder.cpmstar;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.cpmstar.ExtImpCPMStar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CPMStar implements Bidder<BidRequest> {

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public CPMStar(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidRequest bidRequest = processRequest(request);
            return Result.of(
                    Collections.singletonList(createSingleRequest(bidRequest)),
                    errors
            );
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }
        return Result.of(Collections.emptyList(), errors);
    }

    private BidRequest processRequest(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            throw new RuntimeException("No Imps in Bid Request");
        }
        return null;
    }

    private ExtImpCPMStar parseAndValidateImpExt(Imp imp) {
        return null;
    }

    private HttpRequest<BidRequest> createSingleRequest(BidRequest request) {
        return null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
