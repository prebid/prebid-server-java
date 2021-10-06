package org.prebid.server.bidder.huaweiads.model;

import com.iab.openrtb.request.Asset;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class HuaweiNativeRequest {

    Integer ver;

    Integer plcmtCnt;

    Integer seq;

    List<Asset> assets;

    Integer aurlSupport;

    Integer durlSupport;

    List<EventTracker> eventTrackers;

    Integer privacy;

    ExtImp rawMessage;

    Integer layoutCode;

    Integer adUnit;

    Integer contextSubType;

    Integer placementType;
}

