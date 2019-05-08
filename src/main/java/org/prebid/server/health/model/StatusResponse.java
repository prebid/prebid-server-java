package org.prebid.server.health.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.ZonedDateTime;

@AllArgsConstructor(staticName = "of")
@Value
public class StatusResponse {

    String status;

    ZonedDateTime lastUpdated;
}
