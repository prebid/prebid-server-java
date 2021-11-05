package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.response.BidderError
import org.prebid.server.functional.model.response.Debug

@ToString(includeNames = true, ignoreNulls = true)
class BidResponseExt {

    Debug debug
    Map<ErrorType, List<BidderError>> errors
    Map<String, Integer> responsetimemillis
    Long tmaxrequest
    Map<String, ResponseSyncData> usersync
    BidResponsePrebid prebid
    Map<ErrorType, List<WarningEntry>> warnings
}
