package org.prebid.server.bidder;

import org.prebid.server.bidder.model.HttpRequest;

public interface TimeoutBidder<T> extends Bidder<T> {

    HttpRequest<Void> makeTimeoutNotification(HttpRequest<T> httpRequest);

}
