package org.prebid.server.bidder.adgeneration.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value(staticConstructor = "of")
public class AdgenerationResult {

    String ad;

    String beacon;

    @JsonProperty("beaconurl")
    String beaconUrl;

    BigDecimal cpm;

    @JsonProperty("creativeid")
    String creativeId;

    @JsonProperty("dealid")
    String dealId;

    Integer h;

    Integer w;

    Integer ttl;

    String vastxml;

    @JsonProperty("landing_url")
    String landingUrl;

    @JsonProperty("scheduleid")
    String scheduleId;

    List<String> adomain;

    @JsonProperty("native")
    JsonNode xNative;
}
