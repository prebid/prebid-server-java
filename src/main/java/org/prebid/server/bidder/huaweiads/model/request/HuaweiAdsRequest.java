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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Network network;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Regs regs;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Geo geo;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String consent;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String clientAdRequestId;
}
