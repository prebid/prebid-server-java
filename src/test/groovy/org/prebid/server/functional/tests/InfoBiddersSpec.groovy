package org.prebid.server.functional.tests

import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

class InfoBiddersSpec extends BaseSpec {

    def "PBS should get info about active bidders when enabledOnly = #enabledOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoEnabledOnlyBiddersRequest(enabledOnly)

        then: "Response should contain only generic bidder"
        assert response == ["generic"]

        where:
        enabledOnly << (1..3).collect { PBSUtils.getRandomCase("true")}
    }

    def "PBS should get info about all bidders when enabledOnly = #enabledOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoEnabledOnlyBiddersRequest(enabledOnly)

        then: "Response should contain info about all bidders"
        assert response.size() > 1

        where:
        enabledOnly << (1..3).collect { PBSUtils.getRandomCase("false")}
    }

    def "PBS should get info about all bidders when enabledonly isn't passed"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest()

        then: "Response should contain info about all bidders"
        assert response.size() > 1
    }

    def "PBS should return error when enabledOnly is incorrect"() {
        when: "PBS processes bidders info request"
        defaultPbsService.sendInfoEnabledOnlyBiddersRequest(enabledOnly)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid value for 'enabledonly' query param, must be of boolean type"

        where:
        enabledOnly << [PBSUtils.randomString, ""]
    }

    def "PBS should get info only about base bidders when baseAdaptersOnly = #baseAdaptersOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBaseAdaptersOnlyBiddersRequest(baseAdaptersOnly)

        then: "Response should contain info about base bidders only"
        assert response.size() > 1

        where:
        baseAdaptersOnly << (1..3).collect { PBSUtils.getRandomCase("true")}
    }

    def "PBS should get info about all bidders when baseAdaptersOnly = #baseAdaptersOnly"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBaseAdaptersOnlyBiddersRequest(baseAdaptersOnly)

        then: "Response should contain info about all bidders"
        assert response.size() > 1

        where:
        baseAdaptersOnly << (1..3).collect { PBSUtils.getRandomCase("false")}
    }

    def "PBS should get info only about base bidders without aliases when baseAdaptersOnly = #baseAdaptersOnly"() {
        given: "PBS config with additional aliases for generic"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.aliases.anyAliases.enabled": "true"])

        when: "PBS processes bidders info request"
        def response = pbsService.sendInfoBaseAdaptersOnlyBiddersRequest("true")

        then: "Response should contain info about all base bidders only"
        assert response.size() > 1

        and: "Response shouldn't contain any aliases"
        assert !response.contains("anyAliases")
    }
}
