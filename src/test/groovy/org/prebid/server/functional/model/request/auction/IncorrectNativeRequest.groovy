package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class IncorrectNativeRequest implements NativeRequest {

    Integer ver
    Boolean context
    String contextSubtype
    String plcmtType
    String plcmtcnt
    Boolean seq
    List<Asset> assets
    Boolean aurlSupport
    String durlSupport
    List<EventTracker> eventTrackers
    List<Boolean> privacy

    static IncorrectNativeRequest getNativeRequest() {
        new IncorrectNativeRequest().tap {
            context = true
            plcmtType = PBSUtils.randomString
            it.addAsset(Asset.titleAsset)
            it.addAsset(Asset.imgAsset)
            it.addAsset(Asset.dataAsset)
            privacy = [true, false]
        }
    }

    void addAsset(Asset asset) {
        if (assets == null) {
            assets = []
        }
        assets.add(asset)
    }
}
