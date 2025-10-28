package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.User

@ToString(includeNames = true, ignoreNulls = true)
class TargetingOrtb {

    User user
}
