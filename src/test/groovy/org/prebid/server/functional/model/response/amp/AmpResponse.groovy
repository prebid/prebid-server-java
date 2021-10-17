package org.prebid.server.functional.model.response.amp

import org.prebid.server.functional.model.response.BidderError
import org.prebid.server.functional.model.response.Debug

class AmpResponse {

    Map<String, String> targeting
    Debug debug
    Map<String, List<BidderError>> errors
}
