package org.prebid.server.bidder.criteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class CriteoUser {

    @JsonProperty("deviceid")
    String deviceId;

    @JsonProperty("deviceos")
    String deviceOs;

    @JsonProperty("deviceidtype")
    String deviceIdType;

    @JsonProperty("cookieuid")
    String cookieId;

    String uid;

    String ip;

    @JsonProperty("ipv6")
    String ipV6;

    @JsonProperty("ua")
    String userAgent;

    @JsonProperty("uspIab")
    String uspIab;
}
