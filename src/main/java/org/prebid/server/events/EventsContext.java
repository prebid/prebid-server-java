package org.prebid.server.events;

import lombok.Builder;
import lombok.Value;

/**
 * Accumulates information for proceeding events.
 */
@Builder
@Value
public class EventsContext {

    boolean enabledForAccount;

    boolean enabledForRequest;

    String auctionId;

    Long auctionTimestamp;

    String integration;
}
