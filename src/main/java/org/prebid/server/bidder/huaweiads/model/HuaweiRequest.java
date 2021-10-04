package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HuaweiRequest {

    private String version;
    private List<HuaweiAdSlot> multislot;
    private HuaweiApp app;
    private HuaweiDevice device;
    private HuaweiNetwork network;
    private Regs regs;
    private Geo geo;
}
