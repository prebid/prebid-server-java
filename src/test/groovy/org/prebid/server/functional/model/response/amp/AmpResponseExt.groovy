package org.prebid.server.functional.model.response.amp

import org.prebid.server.functional.model.response.BidderError
import org.prebid.server.functional.model.response.Debug
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.WarningEntry

class AmpResponseExt {

    Debug debug
    Map<ErrorType, List<BidderError>> errors
    Map<ErrorType, List<WarningEntry>> warnings
}
