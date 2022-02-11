package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.deals.lineitem.LineItem
import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Bidder
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Ignore

class PgBidderRequestSpec extends BasePgSpec {

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should be able to add given device info to the bidder request"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(ua: PBSUtils.randomString,
                    make: PBSUtils.randomString,
                    model: PBSUtils.randomString)
        }

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "User Service response is set"
        def userResponse = UserDetailsResponse.defaultUserResponse
        userData.setUserDataResponse(userResponse)

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS sent a request to the bidder with added device info"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) { bidderRequest ->
            bidderRequest.user?.ext?.fcapids == userResponse.user.ext.fcapIds
            bidderRequest.user.data?.size() == userResponse.user.data.size()
            bidderRequest.user.data[0].id == userResponse.user.data[0].name
            bidderRequest.user.data[0].segment?.size() == userResponse.user.data[0].segment.size()
            bidderRequest.user.data[0].segment[0].id == userResponse.user.data[0].segment[0].id
        }
    }

    def "PBS should be able to add pmp deals part to the bidder request when PG is enabled"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def accountId = bidRequest.site.publisher.id
        def plansResponse = new PlansResponse(lineItems: [LineItem.getDefaultLineItem(accountId), LineItem.getDefaultLineItem(accountId)])
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Sending auction request to PBS"
        pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS sent a request to the bidder with added deals"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.imp?.size() == bidRequest.imp.size()
        assert bidderRequest.imp[0].pmp?.deals?.size() == plansResponse.lineItems.size()
        assert bidderRequest.imp[0].pmp?.deals
        assert plansResponse.lineItems.each { lineItem ->
            def deal = bidderRequest.imp[0]?.pmp?.deals?.find { it.id == lineItem.dealId }

            assert deal
            verifyAll(deal) {
                deal?.ext?.line?.lineItemId == lineItem.lineItemId
                deal?.ext?.line?.extLineItemId == lineItem.extLineItemId
                deal?.ext?.line?.sizes?.size() == lineItem.sizes.size()
                deal?.ext?.line?.sizes[0].w == lineItem.sizes[0].w
                deal?.ext?.line?.sizes[0].h == lineItem.sizes[0].h
            }
        }
    }

    @Ignore(value = "https://jira.magnite-core.com/browse/HB-13821")
    def "PBS shouldn't add already top matched line item by first impression to the second impression deals bidder request section"() {
        given: "Bid request with two impressions"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder = new Bidder(generic: new Generic())
            imp << Imp.defaultImpression
            imp[1].ext.prebid.bidder = new Bidder(generic: new Generic())
        }

        and: "Planner Mock line items"
        def accountId = bidRequest.site.publisher.id
        def plansResponse = new PlansResponse(lineItems: [LineItem.getDefaultLineItem(accountId), LineItem.getDefaultLineItem(accountId)])
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Sending auction request to PBS"
        pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS sent a request to the bidder with two impressions"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp?.size() == bidRequest.imp.size()

        and: "First impression contains 2 deals according to the line items"
        def firstRequestImp = bidderRequest.imp.find { it.id == bidRequest.imp[0].id }
        assert firstRequestImp?.pmp?.deals?.size() == plansResponse.lineItems.size()
        assert plansResponse.lineItems.each { lineItem ->
            def deal = firstRequestImp.pmp.deals.find { it.id == lineItem.dealId }

            assert deal
            verifyAll(deal) {
                deal?.ext?.line?.lineItemId == lineItem.lineItemId
                deal?.ext?.line?.extLineItemId == lineItem.extLineItemId
                deal?.ext?.line?.sizes?.size() == lineItem.sizes.size()
                deal?.ext?.line?.sizes[0].w == lineItem.sizes[0].w
                deal?.ext?.line?.sizes[0].h == lineItem.sizes[0].h
            }
        }

        def topMatchLineItemId = firstRequestImp.pmp.deals.first().ext.line.lineItemId
        def secondRequestImp = bidderRequest.imp.find { it.id == bidRequest.imp[1].id }

        and: "Second impression contains only 1 deal excluding already top matched line items by the first impression"
        assert secondRequestImp.pmp.deals.size() == plansResponse.lineItems.size() - 1
        assert !(secondRequestImp.pmp.deals.collect { it.ext.line.lineItemId } in topMatchLineItemId)

        assert plansResponse.lineItems.findAll { it.lineItemId != topMatchLineItemId }.each { lineItem ->
            def deal = secondRequestImp.pmp.deals.find { it.id == lineItem.dealId }

            assert deal
            verifyAll(deal) {
                deal?.ext?.line?.lineItemId == lineItem.lineItemId
                deal?.ext?.line?.extLineItemId == lineItem.extLineItemId
                deal?.ext?.line?.sizes?.size() == lineItem.sizes.size()
                deal?.ext?.line?.sizes[0].w == lineItem.sizes[0].w
                deal?.ext?.line?.sizes[0].h == lineItem.sizes[0].h
            }
        }
    }
}
