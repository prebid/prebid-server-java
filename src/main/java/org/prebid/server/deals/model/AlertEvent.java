package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Builder
@Value
public class AlertEvent {

    private String id;

    private String action;

    private AlertPriority priority;

    private ZonedDateTime updatedAt;

    private String name;

    private String details;

    private AlertSource source;
}
