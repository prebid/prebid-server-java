package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.iab.openrtb.request.EventTracker
import groovy.transform.ToString

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class CorrectNativeRequest implements NativeRequest {

    String ver
    Integer context
    Integer contextSubtype
    Integer plcmtType
    Integer plcmtcnt
    Integer seq
    List<Asset> assets
    Integer aurlSupport
    Integer durlSupport
    List<EventTracker> eventTrackers
    Integer privacy

    static CorrectNativeRequest getNativeRequest() {
        new CorrectNativeRequest().tap {
            context = 1
            plcmtType = 1
            it.addAsset(Asset.titleAsset)
            it.addAsset(Asset.imgAsset)
            it.addAsset(Asset.dataAsset)
        }
    }

    void addAsset(Asset asset) {
        if (assets == null) {
            assets = []
        }
        assets.add(asset)
    }
}
