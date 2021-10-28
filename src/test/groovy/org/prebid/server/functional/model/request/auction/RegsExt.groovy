package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy)
class RegsExt {

    Integer gdpr
    String usPrivacy
}
