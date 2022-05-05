package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.deals.lineitem.FrequencyCap
import org.prebid.server.functional.model.deals.lineitem.LineItem
import org.prebid.server.functional.model.deals.lineitem.LineItemSize
import org.prebid.server.functional.model.deals.lineitem.Price
import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.Bidder
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.deals.lineitem.LineItemStatus.DELETED
import static org.prebid.server.functional.model.deals.lineitem.LineItemStatus.PAUSED
import static org.prebid.server.functional.model.deals.lineitem.RelativePriority.HIGH
import static org.prebid.server.functional.model.deals.lineitem.RelativePriority.LOW
import static org.prebid.server.functional.model.deals.lineitem.RelativePriority.MEDIUM
import static org.prebid.server.functional.model.deals.lineitem.RelativePriority.VERY_HIGH
import static org.prebid.server.functional.model.deals.lineitem.RelativePriority.VERY_LOW
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.IN
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.INTERSECTS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_MEDIA_TYPE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_SIZE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.DEVICE_REGION
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.util.HttpUtil.UUID_REGEX

class PgAuctionSpec extends BasePgSpec {

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should return base response after PG auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Auction response contains values according to the payload"
        verifyAll(auctionResponse) {
            auctionResponse.id == bidRequest.id
            auctionResponse.cur == pgConfig.currency
            !auctionResponse.bidid
            !auctionResponse.customdata
            !auctionResponse.nbr
        }

        and: "Seat bid corresponds to the request seat bid"
        assert auctionResponse.seatbid?.size() == bidRequest.imp.size()
        def seatBid = auctionResponse.seatbid[0]
        assert seatBid.seat == GENERIC

        assert seatBid.bid?.size() == 1

        verifyAll(seatBid.bid[0]) { bid ->
            (bid.id =~ UUID_REGEX).matches()
            bid.impid == bidRequest.imp[0].id
            bid.price == bidResponse.seatbid[0].bid[0].price
            bid.crid == bidResponse.seatbid[0].bid[0].crid
            bid.ext?.prebid?.type == BANNER
            bid.ext?.origbidcpm == bidResponse.seatbid[0].bid[0].price
        }
    }

    def "PBS shouldn't process line item with #reason"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock non matched line item"
        generalPlanner.initPlansResponse(plansResponse.tap {
            it.lineItems[0].accountId = bidRequest.site.publisher.id
        })

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start processing PG deals"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        reason                  | plansResponse

        "non matched targeting" | PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].targeting = new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                            .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                            .addTargeting(DEVICE_REGION, IN, [14])
                                                            .build()
        }

        "empty targeting"       | PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].targeting = null
        }

        "non matched bidder"    | PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].source = PBSUtils.randomString
        }

        "inactive status"       | PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].status = DELETED
        }

        "paused status"         | PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].status = PAUSED
        }

        "expired lifetime"      | PlansResponse.getDefaultPlansResponse(PBSUtils.randomString).tap {
            lineItems[0].startTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusMinutes(2)
            lineItems[0].endTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusMinutes(1)
            lineItems[0].updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusMinutes(2)
        }
    }

    def "PBS shouldn't process line item with non matched publisher account id"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock non matched publisher account id line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(PBSUtils.randomNumber as String)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start processing PG deals"
        assert !auctionResponse.ext?.debug?.pgmetrics
    }

    def "PBS shouldn't start processing PG deals when there is no any line item"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock no line items"
        generalPlanner.initPlansResponse(new PlansResponse(lineItems: []))

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start processing PG deals"
        assert !auctionResponse.ext?.debug?.pgmetrics
    }

    def "PBS shouldn't allow line item with #reason delivery plan take part in auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line item with expired delivery schedule"
        def plansResponse = plansResponseClosure(bidRequest)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't allow line item take part in auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.pacingDeferred ==
                plansResponse.lineItems.collect { it.lineItemId } as Set

        where:
        reason    | plansResponseClosure

        "expired" | { BidRequest bidReq ->
            PlansResponse.getDefaultPlansResponse(bidReq.site.publisher.id).tap {
                lineItems[0].deliverySchedules[0].startTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusDays(2)
                lineItems[0].deliverySchedules[0].updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusDays(2)
                lineItems[0].deliverySchedules[0].endTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).minusDays(1)
            }
        }

        "not set" | { BidRequest bidReq ->
            PlansResponse.getDefaultPlansResponse(bidReq.site.publisher.id).tap {
                lineItems[0].deliverySchedules = []
            }
        }
    }

    def "PBS should process only first #maxDealsPerBidder line items among the matched ones"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Publisher account id"
        def accountId = bidRequest.site.publisher.id

        and: "Planner Mock line items to return #maxDealsPerBidder + 1 line items"
        def maxLineItemsToProcess = pgConfig.maxDealsPerBidder
        def plansResponse = new PlansResponse(lineItems: (1..maxLineItemsToProcess + 1).collect {
            LineItem.getDefaultLineItem(accountId)
        })
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "There are #maxLineItemsToProcess + 1 line items are matched and ready to serve"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == maxLineItemsToProcess + 1
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == maxLineItemsToProcess + 1

        and: "Only #maxLineItemsToProcess were sent to the bidder"
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)?.size() == maxLineItemsToProcess
    }

    def "PBS should send to bidder only the first line item among line items with identical deal ids"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Deal id"
        def dealId = PBSUtils.randomString

        and: "Planner Mock line items with identical deal ids"
        def plansResponse = new PlansResponse(lineItems: (1..2).collect {
            LineItem.getDefaultLineItem(bidRequest.site.publisher.id).tap { it.dealId = dealId }
        })
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemsNumber = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "There are 2 matched and ready to serve line items"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemsNumber
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == lineItemsNumber

        and: "Only 1 line item was sent to the bidder"
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)?.size() == lineItemsNumber - 1
    }

    def "PBS should allow line item with matched to the request bidder alias take part in auction"() {
        given: "Bid request with set bidder alias"
        def lineItemSource = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def prebid = new Prebid(aliases: [(lineItemSource): GENERIC], debug: 1)
            ext = new BidRequestExt(prebid: prebid)
        }

        and: "Planner Mock line items with changed line item source"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].source = lineItemSource
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemCount = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Line item was matched by alias bidder and took part in auction"
        def pgMetrics = auctionResponse.ext?.debug?.pgmetrics
        assert pgMetrics
        assert pgMetrics.sentToBidder?.get(lineItemSource)?.size() == lineItemCount
        assert pgMetrics.readyToServe?.size() == lineItemCount
        assert pgMetrics.matchedWholeTargeting?.size() == lineItemCount
    }

    def "PBS should abandon line items with matched user frequency capped ids take part in auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items with added frequency cap"
        def fcapId = PBSUtils.randomNumber as String
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].frequencyCaps = [FrequencyCap.defaultFrequencyCap.tap { it.fcapId = fcapId }]
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "User Service Response is set to return frequency capped id identical to the line item fcapId"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse.tap {
            user.ext.fcapIds = [fcapId]
        })

        and: "Cookies header"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS hasn't started processing PG deals as line item was recognized as frequency capped"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedTargetingFcapped?.size() == plansResponse.lineItems.size()

        cleanup:
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)
    }

    def "PBS should allow line items with unmatched user frequency capped ids take part in auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items with added frequency cap"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].frequencyCaps = [FrequencyCap.defaultFrequencyCap.tap { fcapId = PBSUtils.randomNumber as String }]
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "User Service Response is set to return frequency capped id not identical to the line item fcapId"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse.tap {
            user.ext.fcapIds = [PBSUtils.randomNumber as String]
        })

        and: "Cookies header"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS hasn't started processing PG deals as line item was recognized as frequency capped"
        assert !auctionResponse.ext?.debug?.pgmetrics?.matchedTargetingFcapped
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe

        cleanup:
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)
    }

    def "PBS shouldn't use already matched line items by the same bidder during one auction"() {
        given: "Bid request with two impressions"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder = new Bidder(generic: new Generic())
            imp << Imp.defaultImpression
            imp[1].ext.prebid.bidder = new Bidder(generic: new Generic())
        }
        def accountId = bidRequest.site.publisher.id

        and: "Planner Mock with two line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(accountId).tap {
            lineItems << LineItem.getDefaultLineItem(accountId)
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemCount = plansResponse.lineItems.size()
        def lineItemIds = plansResponse.lineItems.collect { it.lineItemId } as Set

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is requested"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Two line items are ready to be served"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == lineItemCount

        and: "Two (as the number of imps) different line items were sent to the bidder"
        def sentToBidder = auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == lineItemCount
        assert sentToBidder.sort() == lineItemIds.sort()

        def sentToBidderAsTopMatch = auctionResponse.ext?.debug?.pgmetrics?.sentToBidderAsTopMatch?.get(GENERIC.value)
        assert sentToBidderAsTopMatch?.size() == lineItemCount
        assert sentToBidderAsTopMatch.sort() == lineItemIds.sort()
    }

    def "PBS should send line items with the highest priority to the bidder during auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Planner Mock line items with different priorities"
        def lowerPriorityLineItems = [LineItem.getDefaultLineItem(accountId).tap { relativePriority = VERY_LOW },
                                      LineItem.getDefaultLineItem(accountId).tap { relativePriority = LOW }]
        def higherPriorityLineItems = [LineItem.getDefaultLineItem(accountId).tap { relativePriority = MEDIUM },
                                       LineItem.getDefaultLineItem(accountId).tap { relativePriority = HIGH },
                                       LineItem.getDefaultLineItem(accountId).tap { relativePriority = VERY_HIGH }]
        def lineItems = lowerPriorityLineItems + higherPriorityLineItems
        def plansResponse = new PlansResponse(lineItems: lineItems)
        def higherPriorityLineItemIds = higherPriorityLineItems.collect { it.lineItemId }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is requested"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "All line items are ready to be served"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == lineItems.size()

        and: "#maxDealsPerBidder[3] line items were send to bidder"
        def sentToBidder = auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == pgConfig.maxDealsPerBidder

        and: "Those line items with the highest priority were sent"
        assert sentToBidder.sort() == higherPriorityLineItemIds.sort()
    }

    def "PBS should send line items with the highest CPM to the bidder during auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Planner Mock line items with different CPMs"
        def currency = Price.defaultPrice.currency
        def lowerCpmLineItems = [LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: currency) },
                                 LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 2, currency: currency) }]
        def higherCpmLineItems = [LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 3, currency: currency) },
                                  LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 4, currency: currency) },
                                  LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 5, currency: currency) }]
        def lineItems = lowerCpmLineItems + higherCpmLineItems
        def plansResponse = new PlansResponse(lineItems: lineItems)
        def higherCpmLineItemIds = higherCpmLineItems.collect { it.lineItemId }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is requested"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "All line items are ready to be served"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == lineItems.size()

        and: "#maxDealsPerBidder[3] line items were send to bidder"
        def sentToBidder = auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == pgConfig.maxDealsPerBidder

        and: "Those line items with the highest CPM were sent"
        assert sentToBidder.sort() == higherCpmLineItemIds.sort()
    }

    def "PBS should send line items with the highest priority to the bidder during auction despite the price"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Planner Mock line items with different priorities and CPMs"
        def currency = Price.defaultPrice.currency
        def lowPriorityHighPriceLineItems =
                [LineItem.getDefaultLineItem(accountId).tap {
                    relativePriority = VERY_LOW
                    price = new Price(cpm: 5, currency: currency)
                },
                 LineItem.getDefaultLineItem(accountId).tap {
                     relativePriority = LOW
                     price = new Price(cpm: 5, currency: currency)
                 }]
        def highPriorityLowPriceLineItems =
                [LineItem.getDefaultLineItem(accountId).tap {
                    relativePriority = MEDIUM
                    price = new Price(cpm: 1, currency: currency)
                },
                 LineItem.getDefaultLineItem(accountId).tap {
                     relativePriority = HIGH
                     price = new Price(cpm: 1, currency: currency)
                 },
                 LineItem.getDefaultLineItem(accountId).tap {
                     relativePriority = VERY_HIGH
                     price = new Price(cpm: 1, currency: currency)
                 }]
        def lineItems = lowPriorityHighPriceLineItems + highPriorityLowPriceLineItems
        def plansResponse = new PlansResponse(lineItems: lineItems)
        generalPlanner.initPlansResponse(plansResponse)
        def higherPriorityLineItemIds = highPriorityLowPriceLineItems.collect { it.lineItemId }

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "All line items are ready to be served"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == lineItems.size()

        and: "#maxDealsPerBidder[3] line items were send to bidder"
        def sentToBidder = auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == pgConfig.maxDealsPerBidder

        and: "Those line items with the highest priority were sent"
        assert sentToBidder.sort() == higherPriorityLineItemIds.sort()
    }
}
