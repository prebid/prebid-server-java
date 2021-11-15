package org.prebid.server.functional

import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

class VtrackSpec extends BaseSpec {

    def "PBS should return an error for vtrack request when the timeout time is exceeded"() {
        given: "PBS with timeout configuration"
        // Using minimal allowed time for timeout (1ms) to get timeout error.
        def pbsService = pbsServiceFactory.getService(["vtrack.default-timeout-ms": "1"])

        and: "Default VtrackRequest"
        def payload = PBSUtils.randomNumber.toString()
        def request = VtrackRequest.getDefaultVtrackRequest(mapper.encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber.toString()

        when: "PBS processes vtrack request"
        pbsService.sendVtrackRequest(request, accountId)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 500
        assert exception.responseBody.contains("Timed out while executing SQL query")
    }
}
