package org.prebid.server.bidder;

import org.prebid.server.bidder.model.HttpRequest;

/**
 * TimeoutBidder is used to identify bidders that support timeout notifications.
 */
public interface TimeoutBidder<T> extends Bidder<T> {

    /**
     * makeTimeoutNotification method much the same as makeRequests, except it is fed the bidder request that timed out,
     * and expects that only one notification "request" will be generated. A use case for multiple timeout notifications
     * has not been anticipated.
     * <p>
     * Do note that if makeRequests returns multiple requests, and more than one of these times out,
     * makeTimeoutNotification will be called once for each timed out request.
     */
    HttpRequest<Void> makeTimeoutNotification(HttpRequest<T> httpRequest);
}
