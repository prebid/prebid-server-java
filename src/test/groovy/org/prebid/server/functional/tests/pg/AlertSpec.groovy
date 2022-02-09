package org.prebid.server.functional.tests.pg

import org.mockserver.matchers.Times
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404
import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204
import static org.prebid.server.functional.model.deals.alert.Action.RAISE
import static org.prebid.server.functional.model.deals.alert.AlertPriority.LOW
import static org.prebid.server.functional.model.deals.alert.AlertPriority.MEDIUM
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_PASSWORD
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_USERNAME
import static org.prebid.server.functional.util.HttpUtil.AUTHORIZATION_HEADER
import static org.prebid.server.functional.util.HttpUtil.CONTENT_TYPE_HEADER
import static org.prebid.server.functional.util.HttpUtil.CONTENT_TYPE_HEADER_VALUE
import static org.prebid.server.functional.util.HttpUtil.PG_TRX_ID_HEADER
import static org.prebid.server.functional.util.HttpUtil.UUID_REGEX

class AlertSpec extends BasePgSpec {

    private static final String PBS_REGISTER_CLIENT_ERROR = "pbs-register-client-error"
    private static final String PBS_PLANNER_CLIENT_ERROR = "pbs-planner-client-error"
    private static final String PBS_PLANNER_EMPTY_RESPONSE = "pbs-planner-empty-response-error"
    private static final String PBS_DELIVERY_CLIENT_ERROR = "pbs-delivery-stats-client-error"
    private static final Integer DEFAULT_ALERT_PERIOD = 15

    def "PBS should send alert request when the threshold is reached"() {
        given: "Changed Planner Register endpoint response to return bad status code"
        generalPlanner.initRegisterResponse(NOT_FOUND_404)

        and: "PBS alert counter is reset"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.resetAlertCountRequest)

        and: "Initial Alert Service request count is taken"
        def initialRequestCount = alert.requestCount

        when: "Initiating PBS to register its instance through the bad Planner for the first time"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "PBS sends an alert request to the Alert Service for the first time"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 1 }

        when: "Initiating PBS to register its instance through the bad Planner until the period threshold of alerts is reached"
        (2..DEFAULT_ALERT_PERIOD).forEach {
            pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)
        }

        then: "PBS sends an alert request to the Alert Service for the second time"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 2 }

        and: "Request has the right number of failed register attempts"
        def alertRequest = alert.recordedAlertRequest
        assert alertRequest.details.startsWith("Service register failed to send request $DEFAULT_ALERT_PERIOD " +
                "time(s) with error message")

        cleanup: "Return initial Planner response status code"
        generalPlanner.initRegisterResponse()
    }

    def "PBS should send an alert request with appropriate headers"() {
        given: "Changed Planner Register endpoint response to return bad status code"
        generalPlanner.initRegisterResponse(NOT_FOUND_404)

        and: "PBS alert counter is reset"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.resetAlertCountRequest)

        and: "Initial Alert Service request count is taken"
        def initialRequestCount = alert.requestCount

        when: "Initiating PBS to register its instance through the bad Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        and: "PBS sends an alert request to the Alert Service"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 1 }

        then: "Request headers correspond to the payload"
        def alertRequestHeaders = alert.lastRecordedAlertRequestHeaders
        assert alertRequestHeaders

        and: "Request has an authorization header with a basic auth token"
        def basicAuthToken = HttpUtil.makeBasicAuthHeaderValue(PG_ENDPOINT_USERNAME, PG_ENDPOINT_PASSWORD)
        assert alertRequestHeaders.get(AUTHORIZATION_HEADER) == [basicAuthToken]

        and: "Request has a header with uuid value"
        def uuidHeaderValue = alertRequestHeaders.get(PG_TRX_ID_HEADER)
        assert uuidHeaderValue?.size() == 1
        assert (uuidHeaderValue[0] =~ UUID_REGEX).matches()

        and: "Request has a content type header"
        assert alertRequestHeaders.get(CONTENT_TYPE_HEADER) == [CONTENT_TYPE_HEADER_VALUE]

        cleanup: "Return initial Planner response status code"
        generalPlanner.initRegisterResponse()
    }

    @Unroll
    def "PBS should send an alert when fetching line items response status wasn't OK ('#httpStatusCode')"() {
        given: "Changed Planner line items endpoint response to return bad status code"
        // PBS will make 2 requests to the planner: 1 normal, 2 - recovery request
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString), httpStatusCode, Times.exactly(2))

        and: "PBS alert counter is reset"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.resetAlertCountRequest)

        and: "Initial Alert Service request count is taken"
        def initialRequestCount = alert.requestCount

        when: "Initiating PBS to fetch line items through the bad Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        then: "PBS sends an alert request to the Alert Service"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 1 }

        and: "Alert request should correspond to the payload"
        verifyAll(alert.recordedAlertRequest) { alertRequest ->
            (alertRequest.id =~ UUID_REGEX).matches()
            alertRequest.action == RAISE
            alertRequest.priority == MEDIUM
            alertRequest.updatedAt.isBefore(ZonedDateTime.now(ZoneId.from(UTC)))
            alertRequest.name == PBS_PLANNER_CLIENT_ERROR
            alertRequest.details == "Service planner failed to send request 1 time(s) with error message :" +
                    " Failed to retrieve line items from GP. Reason: Failed to fetch data from Planner, HTTP status code ${httpStatusCode.code()}"

            alertRequest.source.env == pgConfig.env
            alertRequest.source.dataCenter == pgConfig.dataCenter
            alertRequest.source.region == pgConfig.region
            alertRequest.source.system == pgConfig.system
            alertRequest.source.subSystem == pgConfig.subSystem
            alertRequest.source.hostId == pgConfig.hostId
        }

        cleanup: "Return initial Planner response status code"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString))

        where: "Bad status codes"
        httpStatusCode << [NO_CONTENT_204, NOT_FOUND_404, INTERNAL_SERVER_ERROR_500]
    }

    @Unroll
    def "PBS should send an alert when register PBS instance response status wasn't OK ('#httpStatusCode')"() {
        given: "Changed Planner register endpoint response to return bad status code"
        generalPlanner.initRegisterResponse(httpStatusCode)

        and: "PBS alert counter is reset"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.resetAlertCountRequest)

        and: "Initial Alert Service request count is taken"
        def initialRequestCount = alert.requestCount

        when: "Initiating PBS to register its instance through the bad Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "PBS sends an alert request to the Alert Service"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 1 }

        and: "Alert request should correspond to the payload"
        verifyAll(alert.recordedAlertRequest) { alertRequest ->
            (alertRequest.id =~ UUID_REGEX).matches()
            alertRequest.action == RAISE
            alertRequest.priority == MEDIUM
            alertRequest.updatedAt.isBefore(ZonedDateTime.now(ZoneId.from(UTC)))
            alertRequest.name == PBS_REGISTER_CLIENT_ERROR
            alertRequest.details.startsWith("Service register failed to send request 1 time(s) with error message :" +
                    " Planner responded with non-successful code ${httpStatusCode.code()}")

            alertRequest.source.env == pgConfig.env
            alertRequest.source.dataCenter == pgConfig.dataCenter
            alertRequest.source.region == pgConfig.region
            alertRequest.source.system == pgConfig.system
            alertRequest.source.subSystem == pgConfig.subSystem
            alertRequest.source.hostId == pgConfig.hostId
        }

        cleanup: "Return initial Planner response status code"
        generalPlanner.initRegisterResponse()

        where: "Bad status codes"
        httpStatusCode << [NOT_FOUND_404, INTERNAL_SERVER_ERROR_500]
    }

    @Unroll
    def "PBS should send an alert when send delivery statistics report response status wasn't OK ('#httpStatusCode')"() {
        given: "Changed Delivery Statistics endpoint response to return bad status code"
        deliveryStatistics.reset()
        deliveryStatistics.setResponse(httpStatusCode)

        and: "Set line items response"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString))

        and: "PBS alert counter is reset"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.resetAlertCountRequest)

        and: "Initial Alert Service request count is taken"
        def initialRequestCount = alert.requestCount

        and: "Report to send is generated by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        when: "Initiating PBS to send delivery statistics report through the bad Delivery Statistics Service"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends an alert request to the Alert Service"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 1 }

        and: "Alert request should correspond to the payload"
        verifyAll(alert.recordedAlertRequest) { alertRequest ->
            (alertRequest.id =~ UUID_REGEX).matches()
            alertRequest.action == RAISE
            alertRequest.priority == MEDIUM
            alertRequest.updatedAt.isBefore(ZonedDateTime.now(ZoneId.from(UTC)))
            alertRequest.name == PBS_DELIVERY_CLIENT_ERROR
            alertRequest.details.startsWith("Service deliveryStats failed to send request 1 time(s) with error message : " +
                    "Report was not send to delivery stats service with a reason: Delivery stats service responded with " +
                    "status code = ${httpStatusCode.code()} for report with id = ")

            alertRequest.source.env == pgConfig.env
            alertRequest.source.dataCenter == pgConfig.dataCenter
            alertRequest.source.region == pgConfig.region
            alertRequest.source.system == pgConfig.system
            alertRequest.source.subSystem == pgConfig.subSystem
            alertRequest.source.hostId == pgConfig.hostId
        }

        cleanup: "Return initial Delivery Statistics response status code"
        deliveryStatistics.reset()
        deliveryStatistics.setResponse()

        where: "Bad status codes"
        httpStatusCode << [NOT_FOUND_404, INTERNAL_SERVER_ERROR_500]
    }

    def "PBS should send an alert when Planner returns empty response"() {
        given: "Changed Planner get plans response to return no plans"
        generalPlanner.initPlansResponse(new PlansResponse(lineItems: []))

        and: "PBS alert counter is reset"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.resetAlertCountRequest)

        and: "Initial Alert Service request count is taken"
        def initialRequestCount = alert.requestCount

        when: "Initiating PBS to fetch line items through the Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        then: "PBS sends an alert request to the Alert Service"
        PBSUtils.waitUntil { alert.requestCount == initialRequestCount + 1 }

        and: "Alert request should correspond to the payload"
        verifyAll(alert.recordedAlertRequest) { alertRequest ->
            (alertRequest.id =~ UUID_REGEX).matches()
            alertRequest.action == RAISE
            alertRequest.priority == LOW
            alertRequest.updatedAt.isBefore(ZonedDateTime.now(ZoneId.from(UTC)))
            alertRequest.name == PBS_PLANNER_EMPTY_RESPONSE
            alertRequest.details.startsWith("Service planner failed to send request 1 time(s) with error message : " +
                    "Response without line items was received from planner")

            alertRequest.source.env == pgConfig.env
            alertRequest.source.dataCenter == pgConfig.dataCenter
            alertRequest.source.region == pgConfig.region
            alertRequest.source.system == pgConfig.system
            alertRequest.source.subSystem == pgConfig.subSystem
            alertRequest.source.hostId == pgConfig.hostId
        }

        cleanup: "Return initial Planner response"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString))
    }
}
