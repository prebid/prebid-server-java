package org.prebid.server.functional.model.request.amp

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class AmpRequest {

    String tagId
    String debug
    Integer ow
    Integer oh
    Integer w
    Integer h
    String ms
    Long timeout
    String slot
    String curl
    Integer account
    String gdprConsent
    String consentString
    ConsentType consentType
    Boolean gdprApplies
    String addtlConsent

    static AmpRequest getDefaultAmpRequest() {
        def request = new AmpRequest()
        request.tagId = PBSUtils.randomString
        request.curl = PBSUtils.randomString
        request.account = PBSUtils.randomNumber
        request.debug = "1"
        request
    }
}
