package org.prebid.server.deals.events;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.vertx.LocalMessageCodec;

import java.util.Objects;

public class AdminEventService {

    private static final String ADDRESS_ADMIN_CENTRAL_COMMAND = "event.admin-central";

    private static final DeliveryOptions DELIVERY_OPTIONS =
            new DeliveryOptions()
                    .setCodecName(LocalMessageCodec.codecName());

    private final EventBus eventBus;

    public AdminEventService(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * Publishes admin central event.
     */
    public void publishAdminCentralEvent(AdminCentralResponse adminCentralResponse) {
        eventBus.publish(ADDRESS_ADMIN_CENTRAL_COMMAND, adminCentralResponse, DELIVERY_OPTIONS);
    }
}
