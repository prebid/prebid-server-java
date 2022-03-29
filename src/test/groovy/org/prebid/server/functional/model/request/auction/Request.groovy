package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.iab.openrtb.request.EventTracker
import groovy.transform.ToString

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
}
