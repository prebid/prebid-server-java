package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Builder
@Value
public class AlertEvent {

    String id;

    String action;

    AlertPriority priority;

    ZonedDateTime updatedAt;

    String name;

    String details;

    AlertSource source;
}
