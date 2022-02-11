package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_PASSWORD
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_USERNAME
import static org.prebid.server.functional.util.HttpUtil.AUTHORIZATION_HEADER
import static org.prebid.server.functional.util.HttpUtil.PG_TRX_ID_HEADER
import static org.prebid.server.functional.util.HttpUtil.UUID_REGEX

class RegisterSpec extends BasePgSpec {

    def setupSpec() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should be able to register its instance in Planner on demand"() {
        given: "Properties values from PBS config"
        def host = pgConfig.hostId
        def vendor = pgConfig.vendor
        def region = pgConfig.region

        and: "Initial Planner request count"
        def initialRequestCount = generalPlanner.requestCount

        when: "PBS sends request to Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "Request counter is increased"
        PBSUtils.waitUntil { generalPlanner.requestCount == initialRequestCount + 1 }

        and: "PBS instance is healthy"
        def registerRequest = generalPlanner.lastRecordedRegisterRequest
        assert registerRequest.healthIndex >= 0 && registerRequest.healthIndex <= 1

        and: "Host, vendor and region are appropriate to the config"
        assert registerRequest.hostInstanceId == host
        assert registerRequest.vendor == vendor
        assert registerRequest.region == region

        and: "Delivery Statistics Report doesn't have delivery specific data"
        verifyAll(registerRequest.status.dealsStatus) { delStatsReport ->
            (delStatsReport.reportId =~ UUID_REGEX).matches()
            delStatsReport.instanceId == host
            delStatsReport.vendor == vendor
            delStatsReport.region == region
            !delStatsReport.lineItemStatus
            !delStatsReport.dataWindowStartTimeStamp
            !delStatsReport.dataWindowEndTimeStamp
            delStatsReport.reportTimeStamp.isBefore(ZonedDateTime.now(ZoneId.from(UTC)))
        }
    }

    def "PBS should send a register request with appropriate headers"() {
        when: "Initiating PBS to register its instance"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "Request with headers is sent"
        def registerRequestHeaders = generalPlanner.lastRecordedRegisterRequestHeaders
        assert registerRequestHeaders

        and: "Request has an authorization header with a basic auth token"
        def basicAuthToken = HttpUtil.makeBasicAuthHeaderValue(PG_ENDPOINT_USERNAME, PG_ENDPOINT_PASSWORD)
        assert registerRequestHeaders.get(AUTHORIZATION_HEADER) == [basicAuthToken]

        and: "Request has a header with uuid value"
        def uuidHeader = registerRequestHeaders.get(PG_TRX_ID_HEADER)
        assert uuidHeader?.size() == 1
        assert (uuidHeader[0] =~ UUID_REGEX).matches()
    }

    def "PBS should be able to register its instance in Planner providing active PBS line items info"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        def lineItem = plansResponse.lineItems[0]
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial Planner request count"
        def initialRequestCount = generalPlanner.requestCount

        when: "PBS sends request to Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "Request counter is increased"
        PBSUtils.waitUntil { generalPlanner.requestCount == initialRequestCount + 1 }

        and: "Delivery Statistics Report has active line item data"
        def registerRequest = generalPlanner.lastRecordedRegisterRequest
        def delStatsReport = registerRequest.status?.dealsStatus
        assert delStatsReport
        def lineItemStatus = delStatsReport.lineItemStatus

        assert lineItemStatus?.size() == plansResponse.lineItems.size()
        verifyAll(lineItemStatus) {
            lineItemStatus[0].lineItemSource == lineItem.source
            lineItemStatus[0].lineItemId == lineItem.lineItemId
            lineItemStatus[0].dealId == lineItem.dealId
            lineItemStatus[0].extLineItemId == lineItem.extLineItemId
        }

        and: "Line item wasn't used in auction"
        verifyAll(lineItemStatus) {
            !lineItemStatus[0].accountAuctions
            !lineItemStatus[0].targetMatched
            !lineItemStatus[0].sentToBidder
            !lineItemStatus[0].spentTokens
        }

        cleanup:
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should be able to register its instance in Planner providing line items status after auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        def lineItem = plansResponse.lineItems[0]
        def lineItemCount = plansResponse.lineItems.size() as Long
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial Planner request count"
        def initialRequestCount = generalPlanner.requestCount

        and: "Auction is requested"
        pgPbsService.sendAuctionRequest(bidRequest)

        when: "PBS sends request to Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "Request counter is increased"
        PBSUtils.waitUntil { generalPlanner.requestCount == initialRequestCount + 1 }

        and: "Delivery Statistics Report has info about auction"
        def registerRequest = generalPlanner.lastRecordedRegisterRequest
        def delStatsReport = registerRequest.status.dealsStatus
        assert delStatsReport

        and: "Delivery Statistics Report has correct line item status data"
        def lineItemStatus = delStatsReport.lineItemStatus

        assert lineItemStatus?.size() as Long == lineItemCount
        verifyAll(lineItemStatus) {
            lineItemStatus[0].lineItemSource == lineItem.source
            lineItemStatus[0].lineItemId == lineItem.lineItemId
            lineItemStatus[0].dealId == lineItem.dealId
            lineItemStatus[0].extLineItemId == lineItem.extLineItemId
        }

        and: "Line item was used in auction"
        verifyAll(lineItemStatus) {
            lineItemStatus[0].accountAuctions == lineItemCount
            lineItemStatus[0].targetMatched == lineItemCount
            lineItemStatus[0].sentToBidder == lineItemCount
            lineItemStatus[0].spentTokens == lineItemCount
        }

        cleanup:
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should update auction count when register its instance in Planner after auction"() {
        given: "Initial auction count"
        def initialRequestCount = generalPlanner.requestCount
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)
        PBSUtils.waitUntil { generalPlanner.requestCount == initialRequestCount + 1 }
        def initialAuctionCount = generalPlanner.lastRecordedRegisterRequest?.status?.dealsStatus?.clientAuctions

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial Planner request count"
        initialRequestCount = generalPlanner.requestCount

        and: "Auction is requested"
        pgPbsService.sendAuctionRequest(bidRequest)

        when: "PBS sends request to Planner"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.registerInstanceRequest)

        then: "Request counter is increased"
        PBSUtils.waitUntil { generalPlanner.requestCount == initialRequestCount + 1 }

        and: "Delivery Statistics Report has info about auction"
        def registerRequest = generalPlanner.lastRecordedRegisterRequest
        assert registerRequest.status?.dealsStatus?.clientAuctions == initialAuctionCount + 1

        cleanup:
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }
}
