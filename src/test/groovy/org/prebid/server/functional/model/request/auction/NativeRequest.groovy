package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.iab.openrtb.request.EventTracker
import groovy.transform.ToString
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import groovy.transform.ToString
import org.prebid.server.functional.testcontainers.Dependencies

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class Request {

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

    static Request getRequest() {
        new Request().tap {
            context = 1
            plcmttype = 1
            it.addAsset(Asset.assetTitle)
            it.addAsset(Asset.assetImg)
            it.addAsset(Asset.assetData)
        }
    }

    void addAsset(Asset asset) {
        if (assets == null) {
            assets = []
        }
        assets.add(asset)
    }
}
