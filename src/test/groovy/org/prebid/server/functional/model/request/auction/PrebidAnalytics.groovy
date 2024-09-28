package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.config.LogAnalytics

@ToString(includeNames = true, ignoreNulls = true)
class PrebidAnalytics {

    AnalyticsOptions options
    LogAnalytics logAnalytics
}
