package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.FetchStatus

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class AnalyticsTagActivityResult {

    AnalyticTagStatus status
    AnalyticsTagActivityValue values
    AppliedTo appliedTo
}
