package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import lombok.*;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Setter
@Getter
@NoArgsConstructor
public class HuaweiAdsRequest {
    private String version;
    private List<Adslot> multislot;
    private HuaweiAdsApp app;
    private HuaweiAdsDevice device;
    private Network network;
    private Regs regs;
    private Geo geo;
}
