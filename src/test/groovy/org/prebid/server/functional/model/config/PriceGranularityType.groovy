package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.model.request.auction.Range

enum PriceGranularityType {

    LOW(2, [Range.getDefault(5, 0.5)]),
    MEDIUM(2, [Range.getDefault(20, 0.1)]),
    MED(2, [Range.getDefault(20, 0.1)]),
    HIGH(2, [Range.getDefault(20, 0.01)]),
    AUTO(2, [Range.getDefault(5, 0.05), Range.getDefault(10, 0.1), Range.getDefault(20, 0.5)]),
    DENSE(2, [Range.getDefault(3, 0.01), Range.getDefault(8, 0.05), Range.getDefault(20, 0.5)]),
    UNKNOWN(null, [])

    final Integer precision
    final List<Range> ranges

    PriceGranularityType(Integer precision, List<Range> ranges) {
        this.precision = precision
        this.ranges = ranges
    }

    @JsonValue
    String toLowerCase() {
        return name().toLowerCase()
    }
}
