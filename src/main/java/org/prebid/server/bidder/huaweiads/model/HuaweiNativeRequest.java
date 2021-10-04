package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Builder
@Value
public class HuaweiNativeRequest {

    int ver;

    int plcmtCnt;

    int seq;

    List<Asset> assets;

    int aurlSupport;

    int durlSupport;

    List<EventTracker> eventTrackers;

    int privacy;

    ExtImp rawMessage;

    int layoutCode;

    int adUnit;

    int contextSubType;

    int placementType;
}
