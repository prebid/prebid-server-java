package org.prebid.server.functional.model.response.amp

import org.prebid.server.functional.model.response.BidderError
import org.prebid.server.functional.model.response.Debug

class AmpResponseExt {

    Debug debug
    Map<String, List<BidderError>> errors
}
