package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

class SetuidSpec extends BaseSpec {

    @PendingFeature
    def "PBS should return an error for setuid request when the timeout time is exceeded"() {
        given: "PBS with timeout configuration"
        // Using minimal allowed time for timeout (1ms) to get timeout error.
        def pbsService = pbsServiceFactory.getService(["setuid.default-timeout-ms": "1"])

        and: "Default setuid request with account"
        def request = SetuidRequest.defaultSetuidRequest
        def uidsCookie = UidsCookie.defaultUidsCookie
        request.account = PBSUtils.randomNumber.toString()

        and: "Account in the DB"
        def account = new Account(uuid: request.account, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes setuid request"
        pbsService.sendSetUidRequest(request, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 500
        assert exception.responseBody.contains("Timed out while executing SQL query")

    }
}
