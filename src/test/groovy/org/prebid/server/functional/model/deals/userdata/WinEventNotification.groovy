package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString
import org.prebid.server.functional.model.deals.lineitem.FrequencyCap

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class WinEventNotification {

    String bidderCode
    String bidId
    String lineItemId
    String region
    List<UserId> userIds
    ZonedDateTime winEventDateTime
    ZonedDateTime lineUpdatedDateTime
    List<FrequencyCap> frequencyCaps
}
