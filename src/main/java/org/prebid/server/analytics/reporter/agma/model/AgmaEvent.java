package org.prebid.server.analytics.reporter.agma.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Value
@Builder
public class AgmaEvent {

    @JsonProperty("type")
    String eventType;

    @JsonProperty("id")
    String requestId;

    @JsonProperty("code")
    String accountCode;

    Site site;

    App app;

    Device device;

    User user;

    //format 2023-02-01T00:00:00Z
    @JsonProperty("created_at")
    ZonedDateTime startTime;

}
