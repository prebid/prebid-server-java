package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class CommonMessage {

    @JsonProperty("version")
    private final String version;

    @JsonProperty("auctionId")
    private final String auctionId;

    @JsonProperty("referrer")
    private final String referrer;

    @JsonProperty("sampling")
    private final double sampling;

    @JsonProperty("prebidServer")
    private final String prebidServer;

    @JsonProperty("greenbidsId")
    private final String greenbidsId;

    @JsonProperty("pbuid")
    private final String pbuid;

    @JsonProperty("billingId")
    private final String billingId;

    @JsonProperty("adUnits")
    private final List<GreenbidsAdUnit> adUnits;

    @JsonProperty("auctionElapsed")
    private final Long auctionElapsed;
}
