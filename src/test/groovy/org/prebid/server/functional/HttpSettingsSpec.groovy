package org.prebid.server.functional

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.mock.services.httpsettings.HttpAccountsResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.scaffolding.HttpSettings
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.util.ResourceUtil
import spock.lang.Shared

@PBSTest
class HttpSettingsSpec extends BaseSpec {
// Check that PBS actually applied account config only possible by relying on side effects.

    @Shared
    HttpSettings httpSettings = new HttpSettings(Dependencies.networkServiceContainer, mapper)

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(pbsServiceFactory.httpSettings())

    def "PBS should take account information from http data source on auction request"() {
        given: "Get basic BidRequest with generic bidder and set gdpr = 1"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.regs.ext.gdpr = 1

        and: "Prepare default account response with gdpr = 0"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(bidRequest?.site?.publisher?.id)
        httpSettings.setResponse(bidRequest?.site?.publisher?.id, httpSettingsResponse)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Response should contain basic fields"
        assert response.id
        assert response.seatbid?.size() == 1
        assert response.seatbid.first().seat == "generic"
        assert response.seatbid?.first()?.bid?.size() == 1

        and: "There should be only one call to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1

        and: "There should be only one account request"
        assert httpSettings.getRequestCount(bidRequest?.site?.publisher?.id) == 1
    }

    def "PBS should take account information from http data source on AMP request"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Get basic stored request and set gdpr = 1"
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account
        ampStoredRequest.regs.ext.gdpr = 1

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Prepare default account response with gdpr = 0"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(ampRequest.account.toString())
        httpSettings.setResponse(ampRequest.account.toString(), httpSettingsResponse)

        when: "PBS processes amp request"
        def response = prebidServerService.sendAmpRequest(ampRequest)

        then: "Response should contain httpcalls"
        assert !response.ext?.debug?.httpcalls?.isEmpty()

        and: "There should be only one account request"
        assert httpSettings.getRequestCount(ampRequest.account.toString()) == 1

        then: "Response should contain targeting"
        assert !response.ext?.debug?.httpcalls?.isEmpty()
    }

    def "PBS should take account information from http data source on event request"() {
        given: "Default EventRequest"
        def eventRequest = EventRequest.defaultEventRequest

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(eventRequest.accountId.toString())
        httpSettings.setResponse(eventRequest.accountId.toString(), httpSettingsResponse)

        when: "PBS processes event request"
        def responseBody = prebidServerService.sendEventRequest(eventRequest)

        then: "Event response should contain and corresponding content-type"
        assert responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        and: "There should be only one account request"
        assert httpSettings.getRequestCount(eventRequest.accountId.toString()) == 1
    }

    def "PBS should take account information from http data source on setuid request"() {
        given: "Get default SetuidRequest and set account, gdpr=1 "
        def request = SetuidRequest.defaultSetuidRequest
        request.gdpr = 1
        request.account = PBSUtils.randomNumber.toString()
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(request.account)
        httpSettings.setResponse(request.account, httpSettingsResponse)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie.bday
        assert !response.uidsCookie.tempUIDs
        assert !response.uidsCookie.uids
        assert response.responseBody == ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        and: "There should be only one account request"
        assert httpSettings.getRequestCount(request.account) == 1
    }

    def "PBS should take account information from http data source on vtrack request"() {
        given: "Default VtrackRequest"
        String payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(mapper.encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber.toString()

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(accountId)
        httpSettings.setResponse(accountId, httpSettingsResponse)

        when: "PBS processes vtrack request"
        def response = prebidServerService.sendVtrackRequest(request, accountId)

        then: "Response should contain uid"
        assert response.responses[0]?.uuid

        and: "There should be only one account request and pbc request"
        assert httpSettings.getRequestCount(accountId.toString()) == 1
        assert prebidCache.getXmlRequestCount(payload) == 1

        and: "VastXml that was send to PrebidCache must contain event url"
        def prebidCacheRequest = prebidCache.getXmlRecordedRequestsBody(payload)[0]
        assert prebidCacheRequest.contains("/event?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")
    }

    def "PBS should return error if account settings isn't found"() {
        given: "Default EventRequest"
        def eventRequest = EventRequest.defaultEventRequest

        when: "PBS processes event request"
        prebidServerService.sendEventRequest(eventRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 401
        assert exception.responseBody.contains("Account '$eventRequest.accountId' doesn't support events")
    }
}
