package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

class CookieSyncSpec extends BaseSpec {

    @PendingFeature
    def "PBS should return an error for cookie_sync request when the timeout time is exceeded"() {
        given: "PBS with timeout configuration"
        // Using minimal allowed time for timeout (1ms) to get timeout error.
        def pbsService = pbsServiceFactory.getService(["cookie-sync.default-timeout-ms": "1"])

        and: "Default CookieSyncRequest with account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        cookieSyncRequest.account = PBSUtils.randomNumber.toString()
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Account in the DB"
        def account = new Account(uuid: cookieSyncRequest.account, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        pbsService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 500
        assert exception.responseBody.contains("Timed out while executing SQL query")
    }
}
