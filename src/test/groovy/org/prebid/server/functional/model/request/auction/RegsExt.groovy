package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class RegsExt {

    @Deprecated(since = "enabling support of ortb 2.6")
    Integer gdpr
    @Deprecated(since = "enabling support of ortb 2.6")
    String usPrivacy
    String gpc
    Dsa dsa
}
