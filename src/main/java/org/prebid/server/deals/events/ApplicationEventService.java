package org.prebid.server.deals.events;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.vertx.LocalMessageCodec;

import java.util.Objects;

/**
 * Main purpose of this service is decoupling of application events delivery from their generators to consumers.
 * <p>
 * This service is essentially a facade for Vert.x {@link EventBus}, it encapsulates addressing and consumers
 * configuration concerns and provides type-safe API for publishing different application events which are consumed
 * by all {@link ApplicationEventProcessor}s registered in the application.
 * <p>
 * Implementation notes:
 * Communication through {@link EventBus} is performed only locally, that's why no serialization/deserialization
 * happens for objects passed over the bus and hence no implied performance penalty (see {@link LocalMessageCodec}).
 */
public class ApplicationEventService {

    private static final String ADDRESS_EVENT_OPENRTB2_AUCTION = "event.openrtb2-auction";
    private static final String ADDRESS_EVENT_LINE_ITEM_WIN = "event.line-item-win";
    private static final String ADDRESS_EVENT_DELIVERY_UPDATE = "event.delivery-update";

    private static final DeliveryOptions DELIVERY_OPTIONS =
            new DeliveryOptions()
                    .setCodecName(LocalMessageCodec.codecName());

    private final EventBus eventBus;

    public ApplicationEventService(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * Publishes auction event.
     */
    public void publishAuctionEvent(AuctionContext auctionContext) {
        eventBus.publish(ADDRESS_EVENT_OPENRTB2_AUCTION, auctionContext, DELIVERY_OPTIONS);
    }

    /**
     * Publishes line item win event.
     */
    public void publishLineItemWinEvent(String lineItemId) {
        eventBus.publish(ADDRESS_EVENT_LINE_ITEM_WIN, lineItemId);
    }

    /**
     * Publishes delivery update event.
     */
    public void publishDeliveryUpdateEvent() {
        eventBus.publish(ADDRESS_EVENT_DELIVERY_UPDATE, null);
    }
}
