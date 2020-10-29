package org.prebid.server.bidder;

import com.fasterxml.jackson.core.type.TypeReference;
import org.prebid.server.auction.legacy.model.AdapterRequest;
import org.prebid.server.auction.legacy.model.AdapterResponse;
import org.prebid.server.auction.legacy.model.PreBidRequestContext;
import org.prebid.server.bidder.model.legacy.AdapterHttpRequest;
import org.prebid.server.bidder.model.legacy.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.response.legacy.Bid;

import java.util.List;

/**
 * Describes the behavior for {@link Adapter} implementations.
 * <p>
 * Used by {@link HttpAdapterConnector} while performing requests to exchanges and compose results.
 */
@Deprecated
public interface Adapter<T, R> {

    /**
     * Composes list of http request to submit to exchange.
     *
     * @throws PreBidException if error occurs while adUnitBids validation.
     */
    List<AdapterHttpRequest<T>> makeHttpRequests(AdapterRequest adapterRequest,
                                                 PreBidRequestContext preBidRequestContext) throws PreBidException;

    /**
     * Extracts bids from exchange response.
     *
     * @throws PreBidException if error occurs while bids validation.
     */
    List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest, ExchangeCall<T, R> exchangeCall)
            throws PreBidException;

    /**
     * If true - {@link AdapterResponse} will contain bids if at least one valid bid
     * exists, otherwise will contain
     * error.
     * <p>
     * If false - {@link AdapterResponse} will contain error if at least one error
     * occurs during processing.
     */
    boolean tolerateErrors();

    /**
     * A class that will be used to parse response from exchange.
     */
    TypeReference<R> responseTypeReference();
}
