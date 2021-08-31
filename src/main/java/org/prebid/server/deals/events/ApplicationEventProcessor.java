package org.prebid.server.deals.events;

import org.prebid.server.auction.model.AuctionContext;

/**
 * Interface for the components able to consume application events.
 *
 * @see ApplicationEventService
 */
public interface ApplicationEventProcessor {

    void processAuctionEvent(AuctionContext auctionContext);

    void processLineItemWinEvent(String lineItemId);

    void processDeliveryProgressUpdateEvent();
}
