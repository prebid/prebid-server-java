package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.ModuleName
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.mock.services.httpsettings.HttpAccountsResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.testcontainers.scaffolding.HttpSettings
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.util.ResourceUtil

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class HttpSettingsSpec extends BaseSpec {
// Check that PBS actually applied account config only possible by relying on side effects.

    static PrebidServerService prebidServerService
    static PrebidServerService prebidServerServiceWithRfc

    private static final HttpSettings httpSettings = new HttpSettings(networkServiceContainer)
    private static final Map<String, String> PBS_CONFIG_WITH_RFC = new HashMap<>(PbsConfig.httpSettingsConfig) +
            ['settings.http.endpoint': "${networkServiceContainer.rootUri}${HttpSettings.rfcEndpoint}".toString(),
            'settings.http.rfc3986-compatible': 'true']

    def setupSpec() {
        prebidServerService = pbsServiceFactory.getService(PbsConfig.httpSettingsConfig)
        prebidServerServiceWithRfc = pbsServiceFactory.getService(PBS_CONFIG_WITH_RFC)
        bidder.setResponse()
        vendorList.setResponse()
    }

    def cleanupSpec() {
        prebidServerService = pbsServiceFactory.removeContainer(PbsConfig.httpSettingsConfig)
        prebidServerService = pbsServiceFactory.removeContainer(PBS_CONFIG_WITH_RFC)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(PbsConfig.httpSettingsConfig)
    }

    def "PBS should take account information from http data source on auction request"() {
        given: "Get basic BidRequest with generic bidder and set gdpr = 1"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.regs.gdpr = 1

        and: "Prepare default account response with gdpr = 0"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(bidRequest.accountId)
        httpSettings.setResponse(bidRequest.accountId, httpSettingsResponse)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Response should contain basic fields"
        assert response.id
        assert response.seatbid?.size() == 1
        assert response.seatbid.first().seat == GENERIC
        assert response.seatbid?.first()?.bid?.size() == 1

        and: "There should be only one call to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1

        and: "There should be only one account request"
        assert httpSettings.getRequestCount(bidRequest.accountId) == 1
    }

    def "PBS should take account information from http data source on auction request when rfc3986 enabled"() {
        given: "Get basic BidRequest with generic bidder and set gdpr = 1"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.regs.gdpr = 1

        and: "Prepare default account response with gdpr = 0"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(bidRequest.accountId)
        httpSettings.setRfcResponse(bidRequest.accountId, httpSettingsResponse)

        when: "PBS processes auction request"
        def response = prebidServerServiceWithRfc.sendAuctionRequest(bidRequest)

        then: "Response should contain basic fields"
        assert response.id
        assert response.seatbid?.size() == 1
        assert response.seatbid.first().seat == GENERIC
        assert response.seatbid?.first()?.bid?.size() == 1

        and: "There should be only one call to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1

        and: "There should be only one account request"
        assert httpSettings.getRfcRequestCount(bidRequest.accountId) == 1
    }

    def "PBS should take account information from http data source on AMP request"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Get basic stored request and set gdpr = 1"
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account
        ampStoredRequest.regs.gdpr = 1

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
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

    def "PBS should take account information from http data source on AMP request when rfc3986 enabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Get basic stored request and set gdpr = 1"
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account
        ampStoredRequest.regs.gdpr = 1

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Prepare default account response with gdpr = 0"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(ampRequest.account.toString())
        httpSettings.setRfcResponse(ampRequest.account.toString(), httpSettingsResponse)

        when: "PBS processes amp request"
        def response = prebidServerServiceWithRfc.sendAmpRequest(ampRequest)

        then: "Response should contain httpcalls"
        assert !response.ext?.debug?.httpcalls?.isEmpty()

        and: "There should be only one account request"
        assert httpSettings.getRfcRequestCount(ampRequest.account.toString()) == 1

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

    def "PBS should take account information from http data source on event request when rfc3986 enabled"() {
        given: "Default EventRequest"
        def eventRequest = EventRequest.defaultEventRequest

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(eventRequest.accountId.toString())
        httpSettings.setRfcResponse(eventRequest.accountId.toString(), httpSettingsResponse)

        when: "PBS processes event request"
        def responseBody = prebidServerServiceWithRfc.sendEventRequest(eventRequest)

        then: "Event response should contain and corresponding content-type"
        assert responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        and: "There should be only one account request"
        assert httpSettings.getRfcRequestCount(eventRequest.accountId.toString()) == 1
    }

    def "PBS should take account information from http data source on setuid request"() {
        given: "Pbs config with adapters.generic.usersync.redirect.*"
        def pbsConfig = PbsConfig.httpSettingsConfig +
                ["adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Get default SetuidRequest and set account, gdpr=1 "
        def request = SetuidRequest.defaultSetuidRequest
        request.gdpr = 1
        request.account = PBSUtils.randomNumber.toString()
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(request.account)
        httpSettings.setResponse(request.account, httpSettingsResponse)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain tempUIDs cookie"
        assert !response.uidsCookie.uids
        assert response.uidsCookie.tempUIDs
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        and: "There should be only one account request"
        assert httpSettings.getRequestCount(request.account) == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should take account information from http data source on setuid request when rfc3986 enabled"() {
        given: "Pbs config with adapters.generic.usersync.redirect.*"
        def pbsConfig = new HashMap<>(PbsConfig.httpSettingsConfig) +
                ['settings.http.endpoint': "${networkServiceContainer.rootUri}${HttpSettings.rfcEndpoint}".toString(),
                 'settings.http.rfc3986-compatible': 'true',
                 'adapters.generic.usersync.redirect.url'            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 'adapters.generic.usersync.redirect.support-cors'   : 'false',
                 'adapters.generic.usersync.redirect.format-override': 'blank']
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Get default SetuidRequest and set account, gdpr=1 "
        def request = SetuidRequest.defaultSetuidRequest
        request.gdpr = 1
        request.account = PBSUtils.randomNumber.toString()
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(request.account)
        httpSettings.setRfcResponse(request.account, httpSettingsResponse)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain tempUIDs cookie"
        assert !response.uidsCookie.uids
        assert response.uidsCookie.tempUIDs
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        and: "There should be only one account request"
        assert httpSettings.getRfcRequestCount(request.account) == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should take account information from http data source on vtrack request"() {
        given: "Default VtrackRequest"
        String payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber.toString()

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(accountId)
        httpSettings.setResponse(accountId, httpSettingsResponse)

        when: "PBS processes vtrack request"
        def response = prebidServerService.sendPostVtrackRequest(request, accountId)

        then: "Response should contain uid"
        assert response.responses[0]?.uuid

        and: "There should be only one account request and pbc request"
        assert httpSettings.getRequestCount(accountId.toString()) == 1
        assert prebidCache.getXmlRequestCount(payload) == 1

        and: "VastXml that was send to PrebidCache must contain event url"
        def prebidCacheRequest = prebidCache.getXmlRecordedRequestsBody(payload)[0]
        assert prebidCacheRequest.contains("/event?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")
    }

    def "PBS should take account information from http data source on vtrack request when rfc3986 enabled"() {
        given: "Default VtrackRequest"
        String payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber.toString()

        and: "Prepare default account response"
        def httpSettingsResponse = HttpAccountsResponse.getDefaultHttpAccountsResponse(accountId)
        httpSettings.setRfcResponse(accountId, httpSettingsResponse)

        when: "PBS processes vtrack request"
        def response = prebidServerServiceWithRfc.sendPostVtrackRequest(request, accountId)

        then: "Response should contain uid"
        assert response.responses[0]?.uuid

        and: "There should be only one account request and pbc request"
        assert httpSettings.getRfcRequestCount(accountId.toString()) == 1
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

    def "PBS should return error if account settings isn't found when rfc3986 enabled"() {
        given: "Default EventRequest"
        def eventRequest = EventRequest.defaultEventRequest

        when: "PBS processes event request"
        prebidServerServiceWithRfc.sendEventRequest(eventRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 401
        assert exception.responseBody.contains("Account '$eventRequest.accountId' doesn't support events")
    }
}
