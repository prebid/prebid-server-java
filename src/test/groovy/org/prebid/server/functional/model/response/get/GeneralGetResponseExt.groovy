package org.prebid.server.functional.model.response.get

import groovy.transform.ToString
import org.prebid.server.functional.model.response.BidderError
import org.prebid.server.functional.model.response.Debug
import org.prebid.server.functional.model.response.auction.ErrorType

@ToString(includeNames = true, ignoreNulls = true)
class GeneralGetResponseExt {

    Debug debug
    Map<ErrorType, List<BidderError>> errors
    Map<ErrorType, List<BidderError>> warnings
}
