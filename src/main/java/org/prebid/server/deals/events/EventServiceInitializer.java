package org.prebid.server.deals.events;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.vertx.Initializable;

import java.util.List;
import java.util.Objects;

public class EventServiceInitializer implements Initializable {

    private static final String ADDRESS_EVENT_OPENRTB2_AUCTION = "event.openrtb2-auction";
    private static final String ADDRESS_EVENT_LINE_ITEM_WIN = "event.line-item-win";
    private static final String ADDRESS_EVENT_DELIVERY_UPDATE = "event.delivery-update";
    private static final String ADDRESS_ADMIN_CENTRAL_COMMAND = "event.admin-central";

    private final List<ApplicationEventProcessor> applicationEventProcessors;
    private final List<AdminEventProcessor> adminEventProcessors;
    private final EventBus eventBus;

    public EventServiceInitializer(List<ApplicationEventProcessor> applicationEventProcessors,
                                   List<AdminEventProcessor> adminEventProcessors,
                                   EventBus eventBus) {
        this.applicationEventProcessors = Objects.requireNonNull(applicationEventProcessors);
        this.adminEventProcessors = Objects.requireNonNull(adminEventProcessors);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    @Override
    public void initialize() {
        eventBus.localConsumer(
                ADDRESS_EVENT_OPENRTB2_AUCTION,
                (Message<AuctionContext> message) -> applicationEventProcessors.forEach(
                        recorder -> recorder.processAuctionEvent(message.body())));

        eventBus.localConsumer(
                ADDRESS_EVENT_LINE_ITEM_WIN,
                (Message<String> message) -> applicationEventProcessors.forEach(
                        recorder -> recorder.processLineItemWinEvent(message.body())));

        eventBus.localConsumer(
                ADDRESS_EVENT_DELIVERY_UPDATE,
                (Message<String> message) -> applicationEventProcessors.forEach(
                        ApplicationEventProcessor::processDeliveryProgressUpdateEvent));

        eventBus.localConsumer(
                ADDRESS_ADMIN_CENTRAL_COMMAND,
                (Message<AdminCentralResponse> message) -> adminEventProcessors.forEach(
                        recorder -> recorder.processAdminCentralEvent(message.body())));
    }
}
