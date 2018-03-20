package org.prebid.server.bidder.adform;

import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adform.model.AdformResponse;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;

public class AdformAdapter implements Adapter<Void, AdformResponse> {

    private static final String BIDDER = "adform";
    private static final String VERSION = "0.1.0";

    private final Usersyncer usersyncer;
    private final String endpointUrl;

    public AdformAdapter(Usersyncer usersyncer, String endpointUrl) {
        this.usersyncer = Objects.requireNonNull(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<Void>> makeHttpRequests(AdapterRequest adapterRequest,
                                                           PreBidRequestContext preBidRequestContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<Void, AdformResponse> exchangeCall) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tolerateErrors() {
        return false;
    }

    @Override
    public Class<AdformResponse> responseClass() {
        return AdformResponse.class;
    }
}
