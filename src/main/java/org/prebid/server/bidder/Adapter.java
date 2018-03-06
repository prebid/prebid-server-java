package org.prebid.server.bidder;

import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.response.Bid;

import java.util.List;

/**
 * Describes the behavior for {@link Adapter} implementations.
 * <p>
 * Used by {@link HttpAdapterConnector} while performing requests to exchanges and compose results.
 */
public interface Adapter {

    /**
     * Composes list of http request to submit to exchange.
     *
     * @throws PreBidException if error occurs while adUnitBids validation.
     */
    List<AdapterHttpRequest> makeHttpRequests(AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext)
            throws PreBidException;

    /**
     * Extracts bids from exchange response.
     *
     * @throws PreBidException if error occurs while bids validation.
     */
    List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest, ExchangeCall exchangeCall) throws PreBidException;

    /**
     * If true - {@link org.prebid.server.auction.model.AdapterResponse} will contain bids if at least one valid bid
     * exists, otherwise will contain
     * error.
     * <p>
     * If false - {@link org.prebid.server.auction.model.AdapterResponse} will contain error if at least one error
     * occurs during processing.
     */
    boolean tolerateErrors();
}
