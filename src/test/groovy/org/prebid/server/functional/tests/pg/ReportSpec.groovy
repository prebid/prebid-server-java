package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.DeliverySchedule
import org.prebid.server.functional.model.deals.lineitem.LineItem
import org.prebid.server.functional.model.deals.lineitem.Token
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static java.time.ZoneOffset.UTC
import static org.mockserver.model.HttpStatusCode.CONFLICT_409
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.model.deals.lineitem.LineItem.TIME_PATTERN
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_PASSWORD
import static org.prebid.server.functional.testcontainers.PbsPgConfig.PG_ENDPOINT_USERNAME
import static org.prebid.server.functional.util.HttpUtil.AUTHORIZATION_HEADER
import static org.prebid.server.functional.util.HttpUtil.CHARSET_HEADER_VALUE
import static org.prebid.server.functional.util.HttpUtil.CONTENT_TYPE_HEADER
import static org.prebid.server.functional.util.HttpUtil.CONTENT_TYPE_HEADER_VALUE
import static org.prebid.server.functional.util.HttpUtil.PG_TRX_ID_HEADER
import static org.prebid.server.functional.util.HttpUtil.UUID_REGEX

class ReportSpec extends BasePgSpec {

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS shouldn't send delivery statistics when PBS doesn't have reports to send"() {
        given: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        when: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "Delivery Statistics Service request count is not changed"
        assert deliveryStatistics.requestCount == initialRequestCount
    }

    def "PBS shouldn't send delivery statistics when delivery report batch is created but doesn't have reports to send"() {
        given: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        when: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "Delivery Statistics Service request count is not changed"
        assert deliveryStatistics.requestCount == initialRequestCount
    }

    def "PBS should send a report request with appropriate headers"() {
        given: "Initial report sent request count is taken"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "Line items are fetched"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(PBSUtils.randomString))
        updateLineItemsAndWait()

        and: "Delivery report batch is created"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        when: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        and: "PBS sends a report request to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        then: "Request headers corresponds to the payload"
        def deliveryRequestHeaders = deliveryStatistics.lastRecordedDeliveryRequestHeaders
        assert deliveryRequestHeaders

        and: "Request has an authorization header with a basic auth token"
        def basicAuthToken = HttpUtil.makeBasicAuthHeaderValue(PG_ENDPOINT_USERNAME, PG_ENDPOINT_PASSWORD)
        assert deliveryRequestHeaders.get(AUTHORIZATION_HEADER) == [basicAuthToken]

        and: "Request has a header with uuid value"
        def uuidHeader = deliveryRequestHeaders.get(PG_TRX_ID_HEADER)
        assert uuidHeader?.size() == 1
        assert (uuidHeader[0] =~ UUID_REGEX).matches()

        and: "Request has a content type header"
        assert deliveryRequestHeaders.get(CONTENT_TYPE_HEADER) == ["$CONTENT_TYPE_HEADER_VALUE;$CHARSET_HEADER_VALUE"]
    }

    def "PBS should send delivery statistics report when delivery progress report with one line item is created"() {
        given: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "Time before report is sent"
        def startTime = ZonedDateTime.now(UTC)

        and: "Set Planner response to return one line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(PBSUtils.randomString)
        def lineItem = plansResponse.lineItems[0]
        generalPlanner.initPlansResponse(plansResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        when: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report request to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        and: "Report request should correspond to the payload"
        def reportRequest = deliveryStatistics.lastRecordedDeliveryStatisticsReportRequest
        def endTime = ZonedDateTime.now(ZoneId.from(UTC))

        verifyAll(reportRequest) {
            (reportRequest.reportId =~ UUID_REGEX).matches()
            reportRequest.instanceId == pgConfig.hostId
            reportRequest.vendor == pgConfig.vendor
            reportRequest.region == pgConfig.region
            !reportRequest.clientAuctions

            reportRequest.reportTimeStamp.isBefore(endTime)
            reportRequest.dataWindowStartTimeStamp.isBefore(startTime)
            reportRequest.dataWindowEndTimeStamp.isAfter(startTime)
            reportRequest.dataWindowEndTimeStamp.isBefore(endTime)
            reportRequest.reportTimeStamp.isAfter(reportRequest.dataWindowEndTimeStamp)
        }

        and: "Report line items should have an appropriate to the initially set line items info"
        assert reportRequest.lineItemStatus?.size() == 1
        def lineItemStatus = reportRequest.lineItemStatus[0]

        verifyAll(lineItemStatus) {
            lineItemStatus.lineItemSource == lineItem.source
            lineItemStatus.lineItemId == lineItem.lineItemId
            lineItemStatus.dealId == lineItem.dealId
            lineItemStatus.extLineItemId == lineItem.extLineItemId
            !lineItemStatus.accountAuctions
            !lineItemStatus.domainMatched
            !lineItemStatus.targetMatched
            !lineItemStatus.targetMatchedButFcapped
            !lineItemStatus.targetMatchedButFcapLookupFailed
            !lineItemStatus.pacingDeferred
            !lineItemStatus.sentToBidder
            !lineItemStatus.sentToBidderAsTopMatch
            !lineItemStatus.receivedFromBidder
            !lineItemStatus.receivedFromBidderInvalidated
            !lineItemStatus.sentToClient
            !lineItemStatus.sentToClientAsTopMatch
            !lineItemStatus.lostToLineItems
            !lineItemStatus.events
            !lineItemStatus.readyAt
            !lineItemStatus.spentTokens
            !lineItemStatus.pacingFrequency

            lineItemStatus.deliverySchedule?.size() == 1
        }

        def timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN)
        def deliverySchedule = lineItemStatus.deliverySchedule[0]

        verifyAll(deliverySchedule) {
            deliverySchedule.planId == lineItem.deliverySchedules[0].planId
            timeFormatter.format(deliverySchedule.planStartTimeStamp) ==
                    timeFormatter.format(lineItem.deliverySchedules[0].startTimeStamp)
            timeFormatter.format(deliverySchedule.planUpdatedTimeStamp) ==
                    timeFormatter.format(lineItem.deliverySchedules[0].updatedTimeStamp)
            timeFormatter.format(deliverySchedule.planExpirationTimeStamp) ==
                    timeFormatter.format(lineItem.deliverySchedules[0].endTimeStamp)

            deliverySchedule.tokens?.size() == 1
        }

        verifyAll(deliverySchedule.tokens[0]) { tokens ->
            tokens.priorityClass == lineItem.deliverySchedules[0].tokens[0].priorityClass
            tokens.total == lineItem.deliverySchedules[0].tokens[0].total
            tokens.spent == 0
            tokens.totalSpent == 0
        }
    }

    def "PBS should send a correct delivery statistics report when auction with one line item is happened"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "Time before report is sent"
        def startTime = ZonedDateTime.now(UTC)

        and: "Set Planner response to return one line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)
        def lineItem = plansResponse.lineItems[0]
        def lineItemCount = plansResponse.lineItems.size() as Long

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        when: "Auction request to PBS is sent"
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report request to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        and: "Report request should be sent after the test start"
        def reportRequest = deliveryStatistics.lastRecordedDeliveryStatisticsReportRequest
        assert reportRequest.reportTimeStamp.isAfter(startTime)

        and: "Request should contain correct number of client auctions made"
        assert reportRequest.clientAuctions == 1

        and: "Report line items should have an appropriate to the initially set line item info"
        assert reportRequest.lineItemStatus?.size() == 1
        def lineItemStatus = reportRequest.lineItemStatus[0]

        verifyAll(lineItemStatus) {
            lineItemStatus.lineItemSource == lineItem.source
            lineItemStatus.lineItemId == lineItem.lineItemId
            lineItemStatus.dealId == lineItem.dealId
            lineItemStatus.extLineItemId == lineItem.extLineItemId
        }

        and: "Report should have the right PG metrics info"
        verifyAll(lineItemStatus) {
            lineItemStatus?.accountAuctions == lineItemCount
            lineItemStatus?.targetMatched == lineItemCount
            lineItemStatus?.sentToBidder == lineItemCount
            lineItemStatus?.sentToBidderAsTopMatch == lineItemCount
        }

        and: "Report line item should have a delivery schedule"
        assert lineItemStatus.deliverySchedule?.size() == 1
        assert lineItemStatus.deliverySchedule[0].planId == lineItem.deliverySchedules[0].planId
    }

    def "PBS should use line item token with the highest priority"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "Time before report is sent"
        def startTime = ZonedDateTime.now(UTC)

        and: "Set Planner response to return one line item"
        def highestPriorityToken = new Token(priorityClass: 1, total: 2)
        def lowerPriorityToken = new Token(priorityClass: 3, total: 2)
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            def tokens = [highestPriorityToken, lowerPriorityToken]
            lineItems[0].deliverySchedules[0].tokens = tokens
        }
        def tokens = plansResponse.lineItems[0].deliverySchedules[0].tokens
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        when: "Auction request to PBS is sent"
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report request to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        and: "Report request should be sent after the test start"
        def reportRequest = deliveryStatistics.lastRecordedDeliveryStatisticsReportRequest
        assert reportRequest.reportTimeStamp.isAfter(startTime)

        and: "Token with the highest priority was used"
        def reportTokens = reportRequest.lineItemStatus?.first()?.deliverySchedule?.first()?.tokens
        assert reportTokens
        assert reportTokens.size() == tokens.size()
        def usedToken = reportTokens.find { it.priorityClass == highestPriorityToken.priorityClass }
        assert usedToken?.total == highestPriorityToken.total
        assert usedToken?.spent == 1
        assert usedToken?.totalSpent == 1

        and: "Token with a lower priority wasn't used"
        def notUsedToken = reportTokens.find { it.priorityClass == lowerPriorityToken.priorityClass }
        assert notUsedToken?.total == lowerPriorityToken.total
        assert notUsedToken?.spent == 0
        assert notUsedToken?.totalSpent == 0
    }

    def "PBS shouldn't consider line item as used when bidder responds with non-deals specific info"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "Time before report is sent"
        def startTime = ZonedDateTime.now(UTC)

        and: "Set Planner response to return one line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Non-deals bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        when: "Auction request to PBS is sent"
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report request to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        and: "Report request should be sent after the test start"
        def reportRequest = deliveryStatistics.lastRecordedDeliveryStatisticsReportRequest
        assert reportRequest.reportTimeStamp.isAfter(startTime)

        and: "Line item token wasn't used"
        def reportTokens = reportRequest.lineItemStatus?.first()?.deliverySchedule?.first()?.tokens
        assert reportTokens?.size() == plansResponse.lineItems[0].deliverySchedules[0].tokens.size()
        assert reportTokens[0].spent == 0
        assert reportTokens[0].totalSpent == 0
    }

    def "PBS should send additional report when line items number exceeds PBS 'line-items-per-report' property"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Already recorded request count is reset"
        deliveryStatistics.resetRecordedRequests()
        deliveryStatistics.setResponse()

        and: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        and: "Set Planner response to return #lineItemsPerReport + 1 line items"
        def lineItemsPerReport = pgConfig.lineItemsPerReport
        def plansResponse = new PlansResponse(lineItems: (1..lineItemsPerReport + 1).collect {
            LineItem.getDefaultLineItem(accountId)
        })
        generalPlanner.initPlansResponse(plansResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        when: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends two report requests to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 2 }

        and: "Two reports are sent"
        def reportRequests = deliveryStatistics.recordedDeliveryStatisticsReportRequests
        assert reportRequests.size() == 2

        and: "Two reports were sent with #lineItemsPerReport and 1 number of line items"
        assert [reportRequests[-2].lineItemStatus.size(), reportRequests[-1].lineItemStatus.size()].sort() ==
                [lineItemsPerReport, 1].sort()
    }

    def "PBS should save reports for later sending when response from Delivery Statistics was unsuccessful"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Set Planner response to return 1 line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(accountId)
        generalPlanner.initPlansResponse(plansResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "Delivery Statistics Service response is set to return a bad status code"
        deliveryStatistics.reset()
        deliveryStatistics.setResponse(INTERNAL_SERVER_ERROR_500)

        when: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report to Delivery Statistics"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == 1 }

        when: "Delivery Statistics Service response is set to return a success response"
        deliveryStatistics.reset()
        deliveryStatistics.setResponse(OK_200)

        and: "PBS is requested to send a report to Delivery Statistics for the second time"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS for the second time sends the same report to the Delivery Statistics Service"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == 1 }
    }

    def "PBS shouldn't save reports for later sending when Delivery Statistics response is Conflict 409"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Set Planner response to return 1 line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(accountId)
        generalPlanner.initPlansResponse(plansResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "Delivery Statistics Service response is set to return a Conflict status code"
        deliveryStatistics.reset()
        deliveryStatistics.setResponse(CONFLICT_409)

        and: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount

        when: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report to Delivery Statistics"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        and: "PBS is requested to send a report to Delivery Statistics for the second time"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS doesn't request Delivery Statistics Service for the second time"
        assert deliveryStatistics.requestCount == initialRequestCount + 1
    }

    def "PBS should change active delivery plan when the current plan lifetime expires"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Initial Delivery Statistics Service request count"
        def initialRequestCount = deliveryStatistics.requestCount
        def auctionCount = 2

        and: "Current delivery plan which expires in 2 seconds"
        def currentPlanTimeToLive = 2
        def currentDeliverySchedule = new DeliverySchedule(planId: PBSUtils.randomNumber as String,
                startTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)),
                updatedTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)),
                endTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)).plusSeconds(currentPlanTimeToLive),
                tokens: [new Token(priorityClass: 1, total: 1000)])

        and: "Next delivery plan"
        def nextDeliverySchedule = new DeliverySchedule(planId: PBSUtils.randomNumber as String,
                startTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)).plusSeconds(currentPlanTimeToLive),
                updatedTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)).plusSeconds(currentPlanTimeToLive),
                endTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)).plusHours(1),
                tokens: [new Token(priorityClass: 1, total: 500)])

        and: "Set Planner response to return line item with two delivery plans"
        def plansResponse = PlansResponse.getDefaultPlansResponse(accountId).tap {
            lineItems[0].deliverySchedules = [currentDeliverySchedule, nextDeliverySchedule]
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "PBS requests Planner line items"
        updateLineItemsAndWait()

        and: "Auction request to PBS is sent for the first time"
        pgPbsService.sendAuctionRequest(bidRequest)

        when: "Current delivery plan lifetime is expired"
        PBSUtils.waitUntil({ ZonedDateTime.now(ZoneId.from(UTC)).isAfter(currentDeliverySchedule.endTimeStamp) },
                (currentPlanTimeToLive * 1000) + 1000)

        and: "PBS requests Planner line items which also forces current PBS line items to be updated"
        generalPlanner.initPlansResponse(plansResponse)
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Auction request to PBS is sent for the second time"
        bidder.setResponse(bidRequest.id, bidResponse)
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "PBS generates delivery report batch"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)

        and: "PBS is requested to send a report to Delivery Statistics"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)

        then: "PBS sends a report to Delivery Statistics"
        PBSUtils.waitUntil { deliveryStatistics.requestCount == initialRequestCount + 1 }

        and: "Report has info about 2 happened auctions"
        def reportRequest = deliveryStatistics.lastRecordedDeliveryStatisticsReportRequest
        assert reportRequest.clientAuctions == auctionCount
        assert reportRequest.lineItemStatus?.size() == plansResponse.lineItems.size()
        assert reportRequest.lineItemStatus[0].accountAuctions == auctionCount

        and: "One line item during each auction was sent to the bidder"
        assert reportRequest.lineItemStatus[0].sentToBidder == auctionCount

        and: "Report contains two delivery plans info"
        def reportDeliverySchedules = reportRequest.lineItemStatus[0].deliverySchedule
        assert reportDeliverySchedules?.size() == plansResponse.lineItems[0].deliverySchedules.size()

        and: "One token was used during the first auction by the first delivery plan"
        assert reportDeliverySchedules.find { it.planId == currentDeliverySchedule.planId }?.tokens[0].spent == 1

        and: "One token was used from another delivery plan during the second auction after first delivery plan lifetime expired"
        assert reportDeliverySchedules.find { it.planId == nextDeliverySchedule.planId }?.tokens[0].spent == 1
    }
}
