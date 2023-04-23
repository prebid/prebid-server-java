package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Data
@NoArgsConstructor
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
