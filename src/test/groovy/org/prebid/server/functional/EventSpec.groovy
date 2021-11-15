package org.prebid.server.functional

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.service.PrebidServerException

class EventSpec extends BaseSpec {

    def "PBS should return an error for event request when the timeout time is exceeded"() {
        given: "PBS with timeout configuration"
        // Using minimal allowed time for timeout (1ms) to get timeout error.
        def pbsService = pbsServiceFactory.getService(["event.default-timeout-ms": "1"])

        and: "Default EventRequest"
        def eventRequest = EventRequest.defaultEventRequest

        and: "Account in the DB"
        def account = new Account(uuid: eventRequest.accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes event request"
        pbsService.sendEventRequest(eventRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 500
        assert exception.responseBody.contains("Timed out while executing SQL query")
    }
}
