package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.deals.lineitem.FrequencyCap
import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.mock.services.httpsettings.HttpAccountsResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.scaffolding.HttpSettings
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared
import spock.lang.Unroll

import java.time.format.DateTimeFormatter

import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404
import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.deals.lineitem.LineItem.TIME_PATTERN

class UserDetailsSpec extends BasePgSpec {

    private static final String USER_SERVICE_NAME = "userservice"

    @Shared
    HttpSettings httpSettings = new HttpSettings(Dependencies.networkServiceContainer, mapper)

    def "PBS should send user details request to the User Service during deals auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id))

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial user details request count is taken"
        def initialRequestCount = userData.recordedUserDetailsRequestCount

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS sends a request to the User Service"
        def updatedRequestCount = userData.recordedUserDetailsRequestCount
        assert updatedRequestCount == initialRequestCount + 1

        and: "Request corresponds to the payload"
        def userDetailsRequest = userData.recordedUserDetailsRequest
        assert userDetailsRequest.time?.isAfter(uidsCookie.bday)
        assert userDetailsRequest.ids?.size() == 1
        assert userDetailsRequest.ids[0].id == uidsCookie.tempUIDs.get(GENERIC.value).uid
        assert userDetailsRequest.ids[0].type == pgConfig.userIdType
    }

    @Unroll
    def "PBS should validate bad user details response status code ('#statusCode')"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial user details request count is taken"
        def initialRequestCount = userData.recordedUserDetailsRequestCount

        and: "User Service response is set"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse, statusCode)

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS sends a request to the User Service during auction"
        assert userData.recordedUserDetailsRequestCount == initialRequestCount + 1
        def userServiceCall = auctionResponse.ext?.debug?.httpcalls?.get(USER_SERVICE_NAME)
        assert userServiceCall?.size() == 1

        assert !userServiceCall[0].status
        assert !userServiceCall[0].responsebody

        cleanup:
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        where:
        statusCode << [NO_CONTENT_204, NOT_FOUND_404, INTERNAL_SERVER_ERROR_500]
    }

    @Unroll
    def "PBS should invalidate user details response body when response has absent #fieldName field"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial user details request count is taken"
        def initialRequestCount = userData.recordedUserDetailsRequestCount

        and: "User Service response is set"
        userData.setUserDataResponse(userDataResponse)

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS sends a request to the User Service"
        assert userData.recordedUserDetailsRequestCount == initialRequestCount + 1

        and: "Call to the user service was made"
        assert auctionResponse.ext?.debug?.httpcalls?.get(USER_SERVICE_NAME)?.size() == 1

        and: "Data from the user service response wasn't added to the bid request by PBS"
        assert !auctionResponse.ext?.debug?.resolvedrequest?.user?.data
        assert !auctionResponse.ext?.debug?.resolvedrequest?.user?.ext?.fcapids

        cleanup:
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        where:
        fieldName   | userDataResponse
        "user"      | new UserDetailsResponse(user: null)
        "user.data" | UserDetailsResponse.defaultUserResponse.tap { user.data = null }
        "user.ext"  | UserDetailsResponse.defaultUserResponse.tap { user.ext = null }
    }

    def "PBS should abandon line items with user fCap ids take part in auction when user details response failed"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items with added frequency cap"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].frequencyCaps = [FrequencyCap.defaultFrequencyCap.tap { fcapId = PBSUtils.randomNumber as String }]
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Bad User Service Response is set"
        userData.setUserDataResponse(new UserDetailsResponse(user: null))

        and: "Cookies header"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "PBS hasn't started processing PG deals as line item targeting frequency capped lookup failed"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedTargetingFcapLookupFailed?.size() ==
                plansResponse.lineItems.size()

        cleanup:
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)
    }

    def "PBS should send win notification request to the User Service on bidder wins"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        def lineItemId = plansResponse.lineItems[0].lineItemId
        def lineItemUpdateTime = plansResponse.lineItems[0].updatedTimeStamp
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial win notification request count"
        def initialRequestCount = userData.requestCount

        and: "Enabled event request"
        def winEventRequest = EventRequest.defaultEventRequest.tap {
            it.lineItemId = lineItemId
            analytics = 0
        }

        and: "Default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(winEventRequest.accountId.toString())
        httpSettings.setResponse(winEventRequest.accountId.toString(), httpSettingsResponse)

        and: "Cookies header"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS where the winner is instantiated"
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "Sending event request to PBS"
        pgPbsService.sendEventRequest(winEventRequest, cookieHeader)

        then: "PBS sends a win notification to the User Service"
        PBSUtils.waitUntil { userData.requestCount == initialRequestCount + 1 }

        and: "Win request corresponds to the payload"
        def timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN)

        verifyAll(userData.recordedWinEventRequest) { winNotificationRequest ->
            winNotificationRequest.bidderCode == GENERIC.value
            winNotificationRequest.bidId == winEventRequest.bidId
            winNotificationRequest.lineItemId == lineItemId
            winNotificationRequest.region == pgConfig.region
            winNotificationRequest.userIds?.size() == 1
            winNotificationRequest.userIds[0].id == uidsCookie.tempUIDs.get(GENERIC.value).uid
            winNotificationRequest.userIds[0].type == pgConfig.userIdType
            timeFormatter.format(winNotificationRequest.lineUpdatedDateTime) == timeFormatter.format(lineItemUpdateTime)
            winNotificationRequest.winEventDateTime.isAfter(winNotificationRequest.lineUpdatedDateTime)
            !winNotificationRequest.frequencyCaps
        }
    }

    @Unroll
    def "PBS shouldn't send win notification request to the User Service when #reason line item id is given"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id))

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial win notification request count"
        def initialRequestCount = userData.requestCount

        and: "Enabled event request"
        def eventRequest = EventRequest.defaultEventRequest.tap {
            it.lineItemId = lineItemId
            analytics = 0
        }

        and: "Default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(eventRequest.accountId.toString())
        httpSettings.setResponse(eventRequest.accountId.toString(), httpSettingsResponse)

        and: "Cookies header"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(mapper, uidsCookie)

        when: "Sending auction request to PBS where the winner is instantiated"
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "Sending event request to PBS"
        pgPbsService.sendEventRequest(eventRequest, cookieHeader)

        then: "PBS hasn't sent a win notification to the User Service"
        assert userData.requestCount == initialRequestCount

        where:
        reason         | lineItemId
        "null"         | null
        "non-existent" | PBSUtils.randomNumber as String
    }

    @Unroll
    def "PBS shouldn't send win notification request to the User Service when #reason cookies header was given"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        def lineItemId = plansResponse.lineItems[0].lineItemId
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        and: "Initial win notification request count"
        def initialRequestCount = userData.requestCount

        and: "Enabled event request"
        def eventRequest = EventRequest.defaultEventRequest.tap {
            it.lineItemId = lineItemId
            analytics = 0
        }

        and: "Default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(eventRequest.accountId.toString())
        httpSettings.setResponse(eventRequest.accountId.toString(), httpSettingsResponse)

        when: "Sending auction request to PBS where the winner is instantiated"
        pgPbsService.sendAuctionRequest(bidRequest)

        and: "Sending event request to PBS"
        pgPbsService.sendEventRequest(eventRequest, HttpUtil.getCookieHeader(mapper, uidsCookie))

        then: "PBS hasn't sent a win notification to the User Service"
        assert userData.requestCount == initialRequestCount

        where:
        reason              | uidsCookie

        "empty cookie"      | new UidsCookie()

        "empty uids cookie" | UidsCookie.defaultUidsCookie.tap {
            uids = null
            tempUIDs = null
        }
    }
}
