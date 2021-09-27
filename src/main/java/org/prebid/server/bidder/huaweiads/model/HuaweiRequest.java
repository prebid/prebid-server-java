package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import lombok.*;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Setter
@Getter
@NoArgsConstructor
@Builder
public class HuaweiRequest {
    private String version;
    private List<HuaweiAdSlot> multislot;
    private HuaweiApp app;
    private HuaweiDevice device;
    private HuaweiNetwork network;
    private Regs regs;
    private Geo geo;
}
