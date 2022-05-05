package org.prebid.server.functional.model.deals.lineitem

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

import static PeriodType.DAY

@ToString(includeNames = true, ignoreNulls = true)
class FrequencyCap {

    String fcapId
    Integer count
    Integer periods
    String periodType

    static getDefaultFrequencyCap() {
        new FrequencyCap(count: 1,
                fcapId: PBSUtils.randomString,
                periods: 1,
                periodType: DAY
        )
    }
}
