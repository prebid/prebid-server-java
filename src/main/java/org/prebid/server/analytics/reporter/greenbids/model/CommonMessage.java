package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class CommonMessage {

    String version;

    @JsonProperty("auctionId")
    String auctionId;

    String referrer;

    Double sampling;

    @JsonProperty("prebidServer")
    String prebidServer;

    @JsonProperty("greenbidsId")
    String greenbidsId;

    String pbuid;

    @JsonProperty("billingId")
    String billingId;

    @JsonProperty("adUnits")
    List<GreenbidsAdUnit> adUnits;

    @JsonProperty("auctionElapsed")
    Long auctionElapsed;
}
