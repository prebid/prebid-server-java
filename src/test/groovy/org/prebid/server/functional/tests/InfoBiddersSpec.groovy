package org.prebid.server.functional.tests

import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

class InfoBiddersSpec extends BaseSpec {

    def "PBS should get info about active bidders when enabledOnly = #enabledOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest(enabledOnly)

        then: "Response should contain only generic bidder"
        assert response == ["generic"]

        where:
        enabledOnly << ["true", "True", "truE"]
    }

    def "PBS should get info about all bidders when enabledOnly = #enabledOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest(enabledOnly)

        then: "Response should contain info about all bidders"
        assert response.size() > 1

        where:
        enabledOnly << ["false", "False", "falsE"]
    }

    def "PBS should get info about all bidders when enabledonly isn't passed"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest()

        then: "Response should contain info about all bidders"
        assert response.size() > 1
    }

    def "PBS should return error when enabledOnly is incorrect"() {
        when: "PBS processes bidders info request"
        defaultPbsService.sendInfoBiddersRequest(enabledOnly)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid value for 'enabledonly' query param, must be of boolean type"

        where:
        enabledOnly << [PBSUtils.randomString, ""]
    }
}
