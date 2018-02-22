package org.prebid.adapter;

import org.prebid.adapter.model.ExchangeCall;
import org.prebid.adapter.model.HttpRequest;
import org.prebid.exception.PreBidException;
import org.prebid.model.Bidder;
import org.prebid.model.BidderResult;
import org.prebid.model.PreBidRequestContext;
import org.prebid.model.response.Bid;
import org.prebid.model.response.UsersyncInfo;

import java.util.List;

/**
 * Describes behavior for {@link Adapter} implementations.
 * <p>
 * Used by {@link HttpConnector} while performing requests to exchanges and compose results.
 */
public interface Adapter {

    /**
     * Returns adapter code
     */
    String code();

    /**
     * Adapter name in UID cookie
     */
    String cookieFamily();

    /**
     * Returns user sync info for given adapter
     */
    UsersyncInfo usersyncInfo();

    /**
     * Composes list of http request to submit to exchange
     *
     * @throws PreBidException if error occurs while adUnitBids validation
     */
    List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) throws PreBidException;

    /**
     * Extracts bids from exchange response
     *
     * @throws PreBidException if error occurs while bids validation
     */
    List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) throws PreBidException;

    /**
     * If true - {@link BidderResult} will contain bids if at least one valid bid exists, otherwise will contain error.
     * <p>
     * If false - {@link BidderResult} will contain error if at least one error occurs during processing.
     */
    boolean tolerateErrors();
}
