package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.tests.LogAnalytics

@ToString(includeNames = true, ignoreNulls = true)
class AnalyticsModule {

    LogAnalytics logAnalytics
}
