package org.prebid.server.bidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.response.Bid;

import java.util.List;
import java.util.Objects;

/**
 * Used to indicate disabled adapter. First method call to this adapter should throw exception.
 * Other methods should never be called.
 */
public class DisabledAdapter implements Adapter<BidRequest, BidResponse> {

    private String errorMessage;

    public DisabledAdapter(String errorMessage) {
        this.errorMessage = Objects.requireNonNull(errorMessage);
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(
            AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext) throws PreBidException {
        throw new PreBidException(errorMessage);
    }

    @Override
    public List<Bid.BidBuilder> extractBids(
            AdapterRequest adapterRequest, ExchangeCall<BidRequest, BidResponse> exchangeCall) throws PreBidException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tolerateErrors() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeReference<BidResponse> responseTypeReference() {
        throw new UnsupportedOperationException();
    }
}
