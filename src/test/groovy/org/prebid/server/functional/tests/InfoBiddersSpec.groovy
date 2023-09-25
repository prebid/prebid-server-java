package org.prebid.server.functional.tests

import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

class InfoBiddersSpec extends BaseSpec {

    private static final String baseAdaptersOnly = "baseAdaptersOnly"

    def "PBS should get info about active bidders when enabledOnly = #enabledOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest(["enabledonly": enabledOnly])

        then: "Response should contain only generic bidder"
        assert response == ["generic"]

        where:
        enabledOnly << (1..3).collect { PBSUtils.getRandomCase("true") }
    }

    def "PBS should get info about all bidders when enabledOnly = #enabledOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest(["enabledonly": enabledOnly])

        then: "Response should contain info about all bidders"
        assert response.size() > 1

        where:
        enabledOnly << (1..3).collect { PBSUtils.getRandomCase("false") }
    }

    def "PBS should get info about all bidders when enabledonly isn't passed"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest()

        then: "Response should contain info about all bidders"
        assert response.size() > 1
    }

    def "PBS should return error when enabledOnly is incorrect"() {
        when: "PBS processes bidders info request"
        defaultPbsService.sendInfoBiddersRequest(["enabledonly": enabledOnly])

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid value for 'enabledonly' query param, must be of boolean type"

        where:
        enabledOnly << [PBSUtils.randomString, ""]
    }

    def "PBS should get info only about base bidders when baseAdaptersOnly = #baseAdaptersOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest([baseAdaptersOnly: baseAdaptersOnly])

        then: "Response should contain info about base bidders only"
        assert response.size() > 1

        and: "Response shouldn't contain generic alias"
        assert !response.contains("genericAlias")

        where:
        baseAdaptersOnly << (1..3).collect { PBSUtils.getRandomCase("true") }
    }

    def "PBS should get info about all bidders when baseAdaptersOnly = #baseAdaptersOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest([baseAdaptersOnly: baseAdaptersOnly])

        then: "Response should contain info about all bidders"
        assert response.size() > 1

        and: "Response should contain generic alias"
        assert response.contains("genericAlias")

        where:
        baseAdaptersOnly << (1..3).collect { PBSUtils.getRandomCase("false") }
    }

    def "PBS should return error when baseAdaptersOnly is incorrect"() {
        when: "PBS processes bidders info request"
        defaultPbsService.sendInfoBiddersRequest([baseAdaptersOnly: baseAdaptersOnly])

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid value for 'baseAdaptersOnly' query param, must be of boolean type"

        where:
        baseAdaptersOnly << [PBSUtils.randomString, "", PBSUtils.randomNumber as String]
    }
}
