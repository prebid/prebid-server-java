package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class HuaweiAdsRequest {

    String version;

    @JsonProperty("multislot")
    List<AdSlot30> multislot;

    App app;

    Device device;

    Network network;

    Regs regs;

    Geo geo;

    String consent;

    @JsonProperty("clientAdRequestId")
    String clientAdRequestId;

}
