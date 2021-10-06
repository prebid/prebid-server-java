package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class HuaweiRequest {

    String version;

    List<HuaweiAdSlot> multislot;

    HuaweiApp app;

    HuaweiDevice device;

    HuaweiNetwork network;

    Regs regs;

    Geo geo;
}

