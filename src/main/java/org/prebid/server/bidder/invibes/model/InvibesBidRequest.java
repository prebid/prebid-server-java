package org.prebid.server.bidder.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class InvibesBidRequest {

    @JsonProperty("BidParamsJson")
    String bidParamsJson;

    @JsonProperty("Location")
    String location;

    @JsonProperty("Lid")
    String lid;

    @JsonProperty("IsTestBid")
    Boolean isTestBid;

    @JsonProperty("Kw")
    String kw;

    @JsonProperty("IsAMP")
    Boolean isAmp;

    @JsonProperty("Width")
    String width;

    @JsonProperty("Height")
    String height;

    @JsonProperty("GdprConsent")
    String gdprConsent;

    @JsonProperty("Gdpr")
    Boolean gdpr;

    @JsonProperty("Bvid")
    String bvid;

    @JsonProperty("InvibBVLog")
    Boolean invibBVLog;

    @JsonProperty("VideoAdDebug")
    Boolean videoAdDebug;
}
