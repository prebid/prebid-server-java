package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.config.PriceGranularityType

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class PriceGranularity {

    Integer precision
    List<Range> ranges

    static PriceGranularity getDefault(PriceGranularityType granularity) {
        new PriceGranularity(precision: granularity.precision, ranges: granularity.ranges)
    }

    static PriceGranularity getDefault() {
        getDefault(PriceGranularityType.MED)
    }
}
