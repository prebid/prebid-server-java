package org.prebid.server.functional

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.model.request.logging.httpinteraction.HttpInteractionRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.setuid.UidsCookie
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.util.ResourceUtil

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.status.Status.OK

@PBSTest
class SmokeSpec extends BaseSpec {

    def "PBS should return BidResponse when there are valid bids"() {
        given: "Default basic  BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain basic fields"
        assert response.id == bidRequest.id
        assert response.seatbid?.size() == 1
        assert response.seatbid[0]?.seat == "generic"
        assert response.seatbid[0]?.bid?.size() == 1
        assert response.seatbid[0]?.bid[0]?.impid == bidRequest.imp[0].id

        and: "Only declared bidders should be called"
        def requestBidders = bidRequest.requestBidders
        def responseBidders = response.ext?.debug?.bidders
        assert responseBidders.keySet() == requestBidders.toSet()

        and: "There should be only one call to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1
    }

    def "PBS should return AMP response"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain targeting and httpcalls"
        assert response.targeting
        assert response.debug.httpcalls

        and: "httpcalls should send request for bidders from storedRequest"
        def storedRequestBidders = ampStoredRequest.requestBidders
        def responseBidders = response.debug?.bidders
        assert responseBidders.keySet() == storedRequestBidders.toSet()
    }

    def "Call PBS /cookie_sync without uids cookie should return element.usersync.url"() {
        given: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = defaultPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain all bidders"
        assert response.status == CookieSyncResponse.Status.NO_COOKIE
        assert response.bidderStatus?.size() == cookieSyncRequest.bidders.size()
        def bidderStatus = response.getBidderUsersync(GENERIC)
        assert bidderStatus?.usersync?.url
        assert bidderStatus?.usersync?.type
    }

    def "Call PBS /cookie_sync with valid uids cookie should return status OK"() {
        given: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = defaultPbsService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should contain have status 'OK'"
        assert response.status == CookieSyncResponse.Status.OK

        and: "Response should contain all bidders"
        assert !response.getBidderUsersync(GENERIC)
    }

    def "PBS should set uids cookie"() {
        given: "Default SetuidRequest"
        def request = SetuidRequest.defaultSetuidRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes setuid request"
        def response = defaultPbsService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert !response.responseBody?.isEmpty()
    }

    def "PBS should get uids cookie"() {
        given: "Default uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes getuid request"
        def response = defaultPbsService.sendGetUidRequest(uidsCookie)

        then: "Response should contain bidder uids"
        assert response.buyeruids?.size() == uidsCookie.tempUIDs.size()
        assert response.buyeruids.every { bidder, uid -> uidsCookie.tempUIDs[bidder].uid == uid }
    }

    def "PBS should return tracking pixel on event request"() {
        given: "Default EventRequest"
        def eventRequest = EventRequest.defaultEventRequest

        and: "Account in the DB"
        def account = new Account(uuid: eventRequest.accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes event request"
        def responseBody = defaultPbsService.sendEventRequest(eventRequest)

        then: "Event response should contain and corresponding content-type"
        assert responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")
    }

    def "PBS should return PBC response on vtrack request"() {
        given: "Default VtrackRequest"
        def payload = PBSUtils.randomNumber.toString()
        def request = VtrackRequest.getDefaultVtrackRequest(mapper.encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber.toString()

        when: "PBS processes vtrack request"
        def response = defaultPbsService.sendVtrackRequest(request, accountId)

        then: "Response should contain uid"
        assert response.responses[0]?.uuid
    }

    def "status responds with 200 OK"() {
        when: "PBS processes status request"
        def response = defaultPbsService.sendStatusRequest()

        then: "Response should contain status OK"
        assert response.application?.status == OK
    }

    def "PBS should get info about active bidders"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendInfoBiddersRequest()

        then: "Response should contain bidders info"
        assert !response.isEmpty()
    }

    def "PBS should get info about requested bidder"() {
        when: "PBS processes bidders info request"
        def response = defaultPbsService.sendBidderInfoRequest(GENERIC)

        then: "Response should contain bidder info"
        assert response.maintainer?.email == "maintainer@example.com"
        assert response.capabilities?.app?.mediaTypes == ["banner", "video", "native", "audio"]
        assert response.capabilities?.site?.mediaTypes == ["banner", "video", "native", "audio"]
    }

    def "PBS should get bidders params"() {
        when: "PBS processes bidders params request"
        def response = defaultPbsService.sendBiddersParamsRequest()

        then: "Response should contain bidders params"
        assert response.parameters.size() > 0
    }

    def "PBS should return currency rates"() {
        when: "PBS processes bidders params request"
        def response = defaultPbsService.sendCurrencyRatesRequest()

        then: "Response should contain bidders params"
        assert response.rates?.size() > 0
    }

    def "PBS should return empty body on httpinteraction request"() {
        given: "Default httpInteractionRequest"
        def request = HttpInteractionRequest.defaultHttpInteractionRequest

        when: "PBS processes bidders params request"
        def response = defaultPbsService.sendLoggingHttpInteractionRequest(request)

        then: "Response should contain bidders params"
        assert response.isEmpty()
    }
}
