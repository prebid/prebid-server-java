package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class HuaweiAdsRequest {

    String version;

    List<AdSlot30> multislot;

    App app;

    Device device;

    Network network;

    Regs regs;

    Geo geo;

    String consent;

    String clientAdRequestId;
}
