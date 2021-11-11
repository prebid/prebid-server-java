package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = false)
class PrebidSchain {

    List<String> bidders
    Schain schain
}
