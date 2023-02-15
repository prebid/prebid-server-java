package org.prebid.server.functional.tests

import com.iab.gpp.encoder.section.TcfCaV1
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UspV1
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.auction.BidRequest

import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.privacy.GppConsent
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class GppCookieSyncSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    private static final Map<String, String> RUBICON_CONFIG = [
            "adapters.${RUBICON.value}.enabled"                    : "true",
            "adapters.${RUBICON.value}.usersync.cookie-family-name": RUBICON.value,]
    private static final Map<String, String> APPNEXUS_CONFIG = [
            "adapters.${APPNEXUS.value}.enabled"                    : "true",
            "adapters.${APPNEXUS.value}.usersync.cookie-family-name": APPNEXUS.value]
    private static final Map<String, String> PBS_CONFIG = APPNEXUS_CONFIG + RUBICON_CONFIG + GENERIC_CONFIG

    private PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS cookie sync request should reflect error when GPP is specified, but not comma-separate and gdpr is empty"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${PBSUtils.randomNumber}${PBSUtils.randomNumber}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        // TODO replace with error message
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Invalid GPP value"

        and: "Metric should contain cookie_sync.FAMILY.tcf.blocked"
        def metric = this.prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 1
    }

    def "PBS cookie sync request should set GBPR to 1 when GPP contains 2"() {
        given: "Request without GDPR and invalid GPP"

        def gppSid = "${PBSUtils.randomNumber},${PBSUtils.randomNumber}, 2"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = []
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain gdpr as 1"
        assert response.gdpr == 1
    }

    def "PBS cookie sync request should set GBPR to 0 when GPP contains not 2"() {
        given: "Request without GDPR and invalid GPP"

        def gppSid = "0, 1"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = []
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)
        def properties = response.properties
        then: "Response should contain gdpr as 0"
        assert response.gdpr == 0
    }

    def "PBS cookie sync request should reflect warning when GPP is specified as 2 values and gdpr is not 1"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${PBSUtils.randomNumber},${PBSUtils.randomNumber}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = 0
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        // TODO replace with error message
        assert response.ext.warnings == "GPP scope does not match TCF2 scope"

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        def metric = this.prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 0
    }

    def "PBS cookie sync request should reflect warning when GPP does not contains 2 values and gdpr is not 0"() {
        given: "Request without GDPR and invalid GPP"
        def gppSid = "${PBSUtils.randomNumber}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = null
            it.gppSid = gppSid
            it.gdpr = 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.ext.warnings == "GPP scope does not match TCF2 scope" // TODO replace with error message

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        assert prebidServerService.sendCollectedMetricsRequest() == [ "cookie_sync.generic.tcf.blocked": 0 ]
    }

    def "PBS cookie sync request should reflect warning when GPP string is invalid"() {
        given: "Request with invalid GPP"
        def invalidGpp = PBSUtils.getRandomString(20)
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = invalidGpp
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then:
        expect: "Response should contain warning"
        assert response.ext.warnings == "GPP string invalid: ERROR MESSAGE FROM GPP LIBRARY"

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        assert prebidServerService.sendCollectedMetricsRequest() == [ "cookie_sync.generic.tcf.blocked": 0 ]
    }

    def "PBS should copy regs.gpp to user.consent when gppSid contains 2, gpp is TCF2-EU and user.consent isn't specified without any warning from cookie sync request"() {
        given: "Standard cookie sync, bit and uids requests"
        def gppConsent = new GppConsent().setModelToDefaultTcfEuV2()
        def gppSid = [2]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = gppSid
            regs.gpp = gppConsent
            user = new User().tap {
                consent = null
            }
        }

        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gppConsent
            it.gppSid = gppSid.collect { it.toString() }.join(',')

        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes cookie sync request"
        def cookieSyncResponse = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Bidder request should contain user.consent from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.consent == gppConsent as String
        assert bidderRequest.regs.gpp == gppConsent as String

        and: "Response should NOT contain warning"
        assert cookieSyncResponse.ext.warnings == null

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        assert prebidServerService.sendCollectedMetricsRequest() == ["cookie_sync.generic.tcf.blocked": 0]
    }

    def "PBS should copy regs.gpp to user.consent when gppSid contains 2, gpp is TCF2-EU and user.consent are different with warning from cookie sync request"() {
        given: "Standard cookie sync, bit and uids requests"
        def gppConsent = new GppConsent().setFieldValue(TcfEuV2.NAME)
        def gppSid = [2]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = gppSid
            regs.gpp = gppConsent
            user = new User(consent: new GppConsent().setFieldValue(TcfCaV1.NAME))
        }

        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gppConsent
            it.gppSid = gppSid.collect { it.toString() }.join(',')

        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes cookie sync request"
        def cookieSyncResponse = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "CookieSync response should contain warning"
        assert cookieSyncResponse.ext.warnings == "GPP TCF2 string does not match gdpr_consent"

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        assert prebidServerService.sendCollectedMetricsRequest() == ["cookie_sync.generic.tcf.blocked": 0]
    }

    def "PBS should copy gpp to usPrivacy when gppSid contains 6, gpp is TCF2-EU and us_privacy isn't specified without warnings"() {
        given: "Standard cookie sync without usPrivacy, gpp_sid that include 6 and GPP as USP"
        def gppConsent = new GppConsent().setFieldValue(UspV1.NAME)
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gppConsent
            it.usPrivacy = null
            it.gppSid = [6]
        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def cookieSyncResponse = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "CookieSync response should not contain warning"
        assert cookieSyncResponse.ext.warnings == null

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        assert prebidServerService.sendCollectedMetricsRequest() == ["cookie_sync.generic.tcf.blocked": 0]
    }

    def "PBS should copy gpp to usPrivacy when gppSid contains 6, gpp is TCF2-EU and us_privacy is specified with warnings"() {
        given: "Standard cookie sync with: usPrivacy, gpp_sid that include 6 and GPP as USP"
        def gppConsent = new GppConsent().setFieldValue(UspV1.NAME)
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gppConsent
            it.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
            it.gppSid = [6]
        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def cookieSyncResponse = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "CookieSync response should contain warning"
        assert cookieSyncResponse.ext.warnings == "GPP TCF2 string does not match gdpr_consent"

        and: "Metric should not contain cookie_sync.FAMILY.tcf.blocked"
        assert prebidServerService.sendCollectedMetricsRequest() == ["cookie_sync.generic.tcf.blocked": 0]
    }
}
