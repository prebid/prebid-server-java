package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class GppSetuidSpec extends PrivacyBaseSpec {

    def "PBS setUid shouldn't reject request when gpp is invalid"() {
        given: "Set uid request with invalid GPP"
        def setUidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gpp = "Invalid_GPP_Consent_String"
            it.gppSid = null
            it.uid = UUID.randomUUID().toString()
        }

        when: "PBS processes cookie sync request"
        def response = privacyPbsService.sendSetUidRequest(setUidRequest, UidsCookie.defaultUidsCookie)

        then: "Privacy for bidder should not be enforced"
        assert response.uidsCookie.tempUIDs[GENERIC]
    }

    def "PBS setUid should reject request when gppSid is invalid"() {
        given: "Set uid request with invalid GPP SID"
        def setUidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gpp = null
            it.gppSid = PBSUtils.randomString
            it.uid = UUID.randomUUID().toString()
        }

        when: "PBS processes set uid request"
        privacyPbsService.sendSetUidRequest(setUidRequest, UidsCookie.defaultUidsCookie)

        then: "Response should respond with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: Request body cannot be parsed"
    }
}
