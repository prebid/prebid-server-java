package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_PASSWORD
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_USERNAME
import static org.prebid.server.functional.util.HttpUtil.AUTHORIZATION_HEADER
import static org.prebid.server.functional.util.HttpUtil.PG_TRX_ID_HEADER
import static org.prebid.server.functional.util.HttpUtil.UUID_REGEX

class PlansSpec extends BasePgSpec {

    def "PBS should be able to send a request to General Planner"() {
        given: "General Planner response is set"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString), OK_200)

        when: "PBS sends request to General Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        then: "Request is sent"
        PBSUtils.waitUntil { generalPlanner.recordedPlansRequestCount == 1 }
    }

    def "PBS should retry request to General Planner when first request fails"() {
        given: "Bad General Planner response"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString), INTERNAL_SERVER_ERROR_500)

        when: "PBS sends request to General Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        then: "Request is sent two times"
        PBSUtils.waitUntil { generalPlanner.recordedPlansRequestCount == 2 }
    }

    def "PBS should send appropriate headers when requests plans from General Planner"() {
        when: "PBS sends request to General Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        then: "Request with headers is sent"
        def plansRequestHeaders = generalPlanner.lastRecordedPlansRequestHeaders
        assert plansRequestHeaders

        and: "Request has an authorization header with a basic auth token"
        def basicAuthToken = HttpUtil.makeBasicAuthHeaderValue(PG_ENDPOINT_USERNAME, PG_ENDPOINT_PASSWORD)
        assert plansRequestHeaders.get(AUTHORIZATION_HEADER) == [basicAuthToken]

        and: "Request has a header with uuid value"
        def uuidHeader = plansRequestHeaders.get(PG_TRX_ID_HEADER)
        assert uuidHeader?.size() == 1
        assert (uuidHeader[0] =~ UUID_REGEX).matches()
    }
}
