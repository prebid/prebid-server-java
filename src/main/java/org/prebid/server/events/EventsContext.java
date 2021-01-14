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

    Long auctionTimestamp;

    String integration;
}
