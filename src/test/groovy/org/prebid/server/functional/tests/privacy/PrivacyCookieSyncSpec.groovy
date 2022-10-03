package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

class PrivacyCookieSyncSpec extends PrivacyBaseSpec {

    def "PBS should return error when non-existing bidder is requested in CCPA context"() {
        given: "Default CookieSyncRequest with non-existing bidder"
        def accountId = PBSUtils.randomNumber as String
        def bidder = BOGUS
        def ccpaConsent = new CcpaConsent(optOutSale: ENFORCED)
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = accountId

            bidders = [bidder]
            usPrivacy = ccpaConsent
        }

        and: "Account with enabled CCPA privacy in the DB"
        def ccpaConfig = new AccountCcpaConfig(enabled: true)
        accountDao.save(getAccountWithCcpa(accountId, ccpaConfig))

        when: "PBS processes cookie sync request"
        def response = privacyPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error for non-existing bidder"
        def bidderStatus = response.getBidderUserSync(BOGUS)
        assert bidderStatus?.error == "Unsupported bidder"
    }
}
