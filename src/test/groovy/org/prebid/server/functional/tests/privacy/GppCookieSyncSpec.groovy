package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.auction.BidRequest

import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.gpp.TcfEuV2Consent
import org.prebid.server.functional.util.privacy.gpp.UspV1Consent

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.GppSectionId.*
import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT

class GppCookieSyncSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$Dependencies.networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]

    private PrebidServerService prebidServerService = pbsServiceFactory.getService(GENERIC_CONFIG)

    def "PBS cookie sync request should respond with an error when GPP is specified and not comma-separated and gdpr is empty"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${PBSUtils.randomNumber}${PBSUtils.randomNumber}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Request fails with proper message"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid GPP value" // TODO replace with error message

        and: "Metric should contain cookie_sync.FAMILY.tcf.blocked"
        def metric = this.prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 1
    }

    def "PBS cookie sync request should set GDPR to 1 and respond with no errors or warning when GPP SID contains 2"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${PBSUtils.randomNumber},${PBSUtils.randomNumber}, ${TCF_EU_V2.value}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain gdpr as 1"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == null
        assert response.warnings == null
    }

    def "PBS cookie sync request should set GDPR to 0 and respond with no errors or warning when GPP SID doesn't contain 2"() {
        given: "Request without GDPR and invalid GPP"

        def gppSid = "${PBSUtils.randomNumber},${US_PV_V1.value}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain gdpr as 0"
        assert bidderStatus.error == null
        assert response.warnings == null
    }

    def "PBS cookie sync request should respond with a warning when GPP SID contains 2 and gdpr is not 1"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${TCF_EU_V2.value},${US_PV_V1.value}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = 0
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.warnings == "GPP scope does not match TCF2 scope"
    }

    def "PBS cookie sync request should respond with warning when GPP does not contains 2 and gdpr is not 0"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${PBSUtils.randomNumber},${US_PV_V1.value}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.warnings == "GPP scope does not match TCF2 scope"
    }

    def "PBS cookie sync request should respond with warning when GPP string is invalid"() {
        given: "Request with invalid GPP"
        def invalidGpp = PBSUtils.getRandomString()
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = invalidGpp
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.warnings == "GPP string invalid: ERROR MESSAGE FROM GPP LIBRARY"
    }

    def "PBS should copy gpp to usPrivacy when gppSid contains 6, gpp is TCF2-EU and us_privacy is specified with warnings"() {
        given: "Standard cookie sync with: usPrivacy, gpp_sid that include 6 and GPP as USP"
        def gppConsent = new UspV1Consent.Builder().build()
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gppConsent
            it.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
            it.gppSid = [US_PV_V1.value]
        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def cookieSyncResponse = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "CookieSync response should contain warning"
        assert cookieSyncResponse.warnings == "GPP TCF2 string does not match gdpr_consent"
    }
}
