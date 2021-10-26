package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class PriceGranularity {

    Integer precision
    List<Range> ranges
}
