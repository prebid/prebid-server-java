package org.prebid.server.health.model;

import lombok.Value;

import java.time.ZonedDateTime;

@Value(staticConstructor = "of")
public class StatusResponse {

    String status;

    ZonedDateTime lastUpdated;
}
