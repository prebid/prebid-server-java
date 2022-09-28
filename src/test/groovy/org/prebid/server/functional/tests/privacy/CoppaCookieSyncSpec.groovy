package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN

class CoppaCookieSyncSpec extends PrivacyBaseSpec {

    def "PBS should return error when unsupported bidder in ccpa context"() {
        given: "Default CookieSyncRequest with UNKNOWN bidder"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [UNKNOWN]
            usPrivacy = PBSUtils.randomString
        }

        when: "PBS processes cookie sync request"
        def response = privacyPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(UNKNOWN)
        assert bidderStatus.error == "Unsupported bidder"

    }
}
