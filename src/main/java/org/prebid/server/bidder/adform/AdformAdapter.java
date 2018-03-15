package org.prebid.server.bidder.adform;

import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;

public class AdformAdapter extends OpenrtbAdapter {

    private static final String BIDDER = "adform";
    private static final String VERSION = "0.1.0";
    private final String endpointUrl;

    public AdformAdapter(Usersyncer usersyncer, String endpointUrl) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest> makeHttpRequests(AdapterRequest adapterRequest,
                                                     PreBidRequestContext preBidRequestContext) throws PreBidException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall exchangeCall) throws PreBidException {
        throw new UnsupportedOperationException();
    }
}
