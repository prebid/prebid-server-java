package org.prebid.server.functional.tests

import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

class InfoBiddersSpec extends BaseSpec {

    def "PBS should get info about active bidders when enabledonly = #enabledonly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest("true")

        then: "Response should contain only generic bidder"
        assert response == ["generic"]

        where:
        enabledonly << ["true", "True", "truE"]
    }

    def "PBS should get info about all bidders when enabledonly = #enabledonly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest("false")

        then: "Response should contain info about all bidders"
        assert response.size() > 1

        where:
        enabledonly << ["false", "False", "falsE"]
    }

    def "PBS should get info about all bidders when enabledonly isn't passed"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest()

        then: "Response should contain info about all bidders"
        assert response.size() > 1
    }

    def "PBS should return error when enabledonly is incorrect"() {
        when: "PBS processes bidders info request"
        defaultPbsService.sendInfoBiddersRequest(enabledonly)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid value for 'enabledonly' query param, must be of boolean type"

        where:
        enabledonly << [PBSUtils.randomString, ""]
    }
}
