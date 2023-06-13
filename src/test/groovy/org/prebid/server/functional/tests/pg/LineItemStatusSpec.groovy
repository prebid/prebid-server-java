package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.DeliverySchedule
import org.prebid.server.functional.model.deals.lineitem.LineItem
import org.prebid.server.functional.model.deals.lineitem.Token
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.PBSUtils

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import static java.time.ZoneOffset.UTC

class LineItemStatusSpec extends BasePgSpec implements ObjectMapperWrapper {

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should return a bad request exception when no 'id' query parameter is provided"() {
        when: "Requesting endpoint without 'id' parameter"
        pgPbsService.sendLineItemStatusRequest(null)

        then: "PBS throws an exception"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody.contains("id parameter is required")
    }

    def "PBS should return a bad request exception when endpoint is requested with not existing line item id"() {
        given: "Not existing line item id"
        def notExistingLineItemId = PBSUtils.randomString

        when: "Requesting endpoint"
        pgPbsService.sendLineItemStatusRequest(notExistingLineItemId)

        then: "PBS throws an exception"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody.contains("LineItem not found: $notExistingLineItemId")
    }

    def "PBS should return an empty line item status response when line item doesn't have a scheduled delivery"() {
        given: "Line item with no delivery schedule"
        def plansResponse = PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].deliverySchedules = null
        }
        def lineItemId = plansResponse.lineItems[0].lineItemId
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Requesting endpoint"
        def lineItemStatusReport = pgPbsService.sendLineItemStatusRequest(lineItemId)

        then: "Empty line item status report is returned"
        verifyAll(lineItemStatusReport) {
            it.lineItemId == lineItemId
            !it.deliverySchedule
            !it.spentTokens
            !it.readyToServeTimestamp
            !it.pacingFrequency
            !it.accountId
            !it.target
        }
    }

    def "PBS should return filled line item status report when line item has a scheduled delivery"() {
        given: "Line item with a scheduled delivery"
        def plansResponse = new PlansResponse(lineItems: [LineItem.getDefaultLineItem(PBSUtils.randomString).tap {
            deliverySchedules = [DeliverySchedule.defaultDeliverySchedule]
        }])
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemId = plansResponse.lineItems[0].lineItemId
        def lineItem = plansResponse.lineItems[0]
        def deliverySchedule = lineItem.deliverySchedules[0]

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Requesting endpoint"
        def lineItemStatusReport = pgPbsService.sendLineItemStatusRequest(lineItemId)

        then: "Line item status report is returned"
        def reportTimeZone = lineItemStatusReport.deliverySchedule?.planStartTimeStamp?.zone
        assert reportTimeZone

        verifyAll(lineItemStatusReport) {
            it.lineItemId == lineItemId
            it.deliverySchedule?.planId == deliverySchedule.planId

            it.deliverySchedule.planStartTimeStamp ==
                    timeToReportFormat(deliverySchedule.startTimeStamp, reportTimeZone)
            it.deliverySchedule?.planExpirationTimeStamp ==
                    timeToReportFormat(deliverySchedule.endTimeStamp, reportTimeZone)
            it.deliverySchedule?.planUpdatedTimeStamp ==
                    timeToReportFormat(deliverySchedule.updatedTimeStamp, reportTimeZone)

            it.deliverySchedule?.tokens?.size() == deliverySchedule.tokens.size()
            it.deliverySchedule?.tokens?.first()?.priorityClass == deliverySchedule.tokens[0].priorityClass
            it.deliverySchedule?.tokens?.first()?.total == deliverySchedule.tokens[0].total
            !it.deliverySchedule?.tokens?.first()?.spent
            !it.deliverySchedule?.tokens?.first()?.totalSpent

            it.spentTokens == 0
            it.readyToServeTimestamp.isBefore(ZonedDateTime.now())
            it.pacingFrequency == getDeliveryRateMs(deliverySchedule)
            it.accountId == lineItem.accountId
            encode(it.target) == encode(lineItem.targeting)
        }
    }

    def "PBS should return line item status report with an active scheduled delivery"() {
        given: "Line item with an active and expired scheduled deliveries"
        def inactiveDeliverySchedule = DeliverySchedule.defaultDeliverySchedule.tap {
            startTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusHours(12)
            updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusHours(12)
            endTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusHours(6)
        }
        def activeDeliverySchedule = DeliverySchedule.defaultDeliverySchedule.tap {
            startTimeStamp = ZonedDateTime.now(ZoneId.from(UTC))
            updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC))
            endTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).plusHours(12)
        }
        def plansResponse = new PlansResponse(lineItems: [LineItem.getDefaultLineItem(PBSUtils.randomString).tap {
            deliverySchedules = [inactiveDeliverySchedule, activeDeliverySchedule]
        }])
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemId = plansResponse.lineItems[0].lineItemId

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Requesting endpoint"
        def lineItemStatusReport = pgPbsService.sendLineItemStatusRequest(lineItemId)

        then: "Line item status report is returned with an active delivery"
        assert lineItemStatusReport.lineItemId == lineItemId
        assert lineItemStatusReport.deliverySchedule?.planId == activeDeliverySchedule.planId
    }

    def "PBS should return line item status report with increased spent token number when PG auction has happened"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Line item with a scheduled delivery"
        def plansResponse = new PlansResponse(lineItems: [LineItem.getDefaultLineItem(bidRequest.site.publisher.id).tap {
            deliverySchedules = [DeliverySchedule.defaultDeliverySchedule.tap {
                tokens = [Token.defaultToken]
            }]
        }])
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemId = plansResponse.lineItems[0].lineItemId
        def spentTokensNumber = 1

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "PG bid response is set"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "PBS PG auction is requested"
        pgPbsService.sendAuctionRequest(bidRequest)

        when: "Requesting line item status endpoint"
        def lineItemStatusReport = pgPbsService.sendLineItemStatusRequest(lineItemId)

        then: "Spent token number in line item status report is increased"
        assert lineItemStatusReport.lineItemId == lineItemId
        assert lineItemStatusReport.spentTokens == spentTokensNumber
        assert lineItemStatusReport.deliverySchedule?.tokens?.first()?.spent == spentTokensNumber
    }

    private ZonedDateTime timeToReportFormat(ZonedDateTime givenTime, ZoneId reportTimeZone) {
        givenTime.truncatedTo(ChronoUnit.MILLIS).withZoneSameInstant(reportTimeZone)
    }

    private Integer getDeliveryRateMs(DeliverySchedule deliverySchedule) {
        deliverySchedule.tokens[0].total > 0
                ? (deliverySchedule.endTimeStamp.toInstant().toEpochMilli()
                - deliverySchedule.startTimeStamp.toInstant().toEpochMilli()) /
                deliverySchedule.tokens[0].total
                : null
    }
}
