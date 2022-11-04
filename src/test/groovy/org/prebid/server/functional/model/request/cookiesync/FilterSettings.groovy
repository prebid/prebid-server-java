package org.prebid.server.functional.model.request.cookiesync

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.cookiesync.FilterType.EXCLUDE

class FilterSettings {

    MethodFilter iframe
    MethodFilter image

    static FilterSettings getDefaultFilterSetting() {
        new FilterSettings().tap {
            image = MethodFilter.defaultMethodFilter
        }
    }
}
