package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class HuaweiAdsRequest {

    @JsonProperty("version")
    String version;

    @JsonProperty("multislot")
    List<AdSlot30> multislot;

    @JsonProperty("app")
    App app;

    @JsonProperty("device")
    Device device;

    @JsonProperty("network")
    Network network;

    @JsonProperty("regs")
    Regs regs;

    @JsonProperty("geo")
    Geo geo;

    @JsonProperty("consent")
    String consent;

    @JsonProperty("clientAdRequestId")
    String clientAdRequestId;

}
