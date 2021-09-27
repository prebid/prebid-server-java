package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Builder
@Setter
@Getter
public class HuaweiNativeRequest {
    private int ver;
    private int plcmtCnt;
    private int seq;
    private List<Asset> assets;
    private int aurlSupport;
    private int durlSupport;
    private List<EventTracker> eventTrackers;
    private int privacy;
    private ExtImp rawMessage;

    private int layoutCode;
    private int adUnit;
    private int contextSubType;
    private int placementType;
}
