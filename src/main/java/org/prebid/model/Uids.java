package org.prebid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public final class Uids {

    @JsonProperty("uids")
    Map<String, String> uidsLegacy;

    @JsonProperty("tempUIDs")
    Map<String, UidWithExpiry> uids; // transition to new UIDs format

    Boolean optout;

    ZonedDateTime bday;
}
