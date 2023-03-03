package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.gpp.TcfEuV2Consent
import org.prebid.server.functional.util.privacy.gpp.UspV1Consent

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.GppSectionId.US_PV_V1
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class GppCookieSyncSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$Dependencies.networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]

    private PrebidServerService prebidServerService = pbsServiceFactory.getService(GENERIC_CONFIG)

    def "PBS cookie sync request should respond with an error when GPP is specified and not comma-separated and gdpr is empty"() {
        given: "Cookie sync request"
        def gppSid = "${PBSUtils.randomString},${PBSUtils.randomString}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = gppSid
            it.gpp = null
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Request fails with proper message"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: Request body cannot be parsed"
    }

    def "PBS cookie sync request should respond with a warning when GPP SID contains 2 and gdpr is not 1"() {
        given: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = TCF_EU_V2.value
            it.gdpr = 0
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warnings"
        assert response.warnings.size() == 1
        assert response.warnings[0] == "GPP scope does not match TCF2 scope"

        and: "Response should contain sync information for configured bidder"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert bidderStatus?.noCookie == true
    }

    def "PBS cookie sync request should respond with a warning when GPP SID not contains 2 and gdpr is not 0"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = US_PV_V1.value
            it.gdpr = 1
            it.gdprConsent = validConsentString
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warnings"
        assert response.warnings.size() == 1
        assert response.warnings[0] == "GPP scope does not match TCF2 scope"

        and: "Bidder status error should contain reject due to TCF"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"
    }

    def "PBS cookie sync request should respond with warning when GPP string is invalid"() {
        given: "Request with invalid GPP"
        def invalidGpp = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = invalidGpp
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Cookie sync response should contain warning"
        assert response.warnings.size() == 1
        assert response.warnings[0].startsWith("GPP string invalid:")

        and: "Response should contain sync information for configured bidder"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert bidderStatus?.noCookie == true
    }

    def "PBS should return warning when gppSid contains 6, gpp and usp are different"() {
        given: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = new UspV1Consent.Builder().build()
            it.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
            it.gppSid = US_PV_V1.value
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Cookie sync response should contain warning"
        assert response.warnings.size() == 1
        assert response.warnings[0] == "USP string does not match regs.us_privacy"

        and: "Bidder status error should contain reject due to CCPA"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by CCPA"
    }

    def "PBS should return warning when gppSid contains 2, gpp and gdpr_consent are different"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = new TcfEuV2Consent.Builder().build()
            it.gppSid = TCF_EU_V2.value
            it.gdpr = 1
            it.gdprConsent = validConsentString
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.warnings.size() == 1
        assert response.warnings[0] == "GPP TCF2 string does not match user.consent"

        and: "Bidder status error should contain reject due to TCF"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"
    }
}
