package org.prebid.server.functional.model.bidder

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class IxDiag {

    String pbsv
    String pbsp
    String pbjsv
    String multipleSiteIds
}
