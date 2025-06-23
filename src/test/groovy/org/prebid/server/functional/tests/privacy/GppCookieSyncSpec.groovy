package org.prebid.server.functional.tests.privacy

import io.netty.handler.codec.http.HttpResponseStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.gpp.TcfEuV2Consent
import org.prebid.server.functional.util.privacy.gpp.UsV1Consent
import spock.lang.IgnoreRest

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.Purpose.P1
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.DEVICE_ACCESS

class GppCookieSyncSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final GppSectionId FIRST_GPP_SECTION = PBSUtils.getRandomEnum(GppSectionId.class)
    private static final GppSectionId SECOND_GPP_SECTION = PBSUtils.getRandomEnum(GppSectionId.class, [FIRST_GPP_SECTION])

    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.meta-info.vendor-id"                          : GENERIC_VENDOR_ID as String,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    private static final Map<String, String> GENERIC_WITH_SKIP_CONFIG = [
            "adapters.${GENERIC.value}.meta-info.vendor-id"                          : GENERIC_VENDOR_ID as String,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
            "adapters.${GENERIC.value}.usersync.skipwhen.gdpr"                       : 'true',
            "adapters.${GENERIC.value}.usersync.skipwhen.gpp_sid"                    : "${FIRST_GPP_SECTION.value}, ${SECOND_GPP_SECTION.value}".toString(),
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]

    private static PrebidServerService prebidServerService = pbsServiceFactory.getService(GENERIC_CONFIG)
    private static PrebidServerService prebidServerServiceWithSkipConfig = pbsServiceFactory.getService(GENERIC_WITH_SKIP_CONFIG + GENERIC_ALIAS_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(GENERIC_CONFIG)
        pbsServiceFactory.removeContainer(GENERIC_WITH_SKIP_CONFIG + GENERIC_ALIAS_CONFIG)
    }

    def "PBS cookie sync request should set GDPR to 1 when gpp_sid contains 2"() {
        given: "Request without GDPR and GPP SID"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = gppSid
            it.gpp = null
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS should respond with an error requiring consent string"
        def error = thrown(PrebidServerException)
        assert error.statusCode == HttpResponseStatus.BAD_REQUEST.code()
        assert error.responseBody == "Invalid request format: gdpr_consent is required if gdpr is 1"

        where:
        gppSid << [TCF_EU_V2.value, "$PBSUtils.randomNumber, $TCF_EU_V2.value"]
    }

    def "PBS cookie sync request should set GDPR to 0 when gpp_sid doesn't contain 2"() {
        given: "Request without GDPR and GPP SID"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = PBSUtils.getRandomNumberWithExclusion(TCF_EU_V2.intValue)
            it.gpp = null
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain warnings"
        assert response.warnings == null

        and: "Privacy for bidder should not be enforced"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == null
        assert bidderStatus?.noCookie == true
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
    }

    def "PBS cookie sync request should respond with a warning when gpp_sid contains 2 and gdpr is 0"() {
        given: "Request without GDPR and GPP SID"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.value
            it.gpp = null
            it.gdpr = 0
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain a warning"
        assert response.warnings == ["GPP scope does not match TCF2 scope"]

        and: "Privacy for bidder should not be enforced"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == null
        assert bidderStatus?.noCookie == true
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
    }

    def "PBS cookie sync request should respond with a warning when gpp_sid doesn't contain 2 and gdpr is 1"() {
        given: "Request without GDPR and invalid GPP"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = PBSUtils.getRandomNumberWithExclusion(TCF_EU_V2.intValue)
            it.gpp = null
            it.gdpr = 1
            it.gdprConsent = new TcfConsent.Builder().build() // mandatory parameter when gdpr is resolved to 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain a warning"
        assert response.warnings == ["GPP scope does not match TCF2 scope"]

        then: "Privacy for bidder should be enforced"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"
    }

    def "PBS cookie sync request should respond with a warning when GPP string is invalid"() {
        given: "Request with invalid GPP"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = "Invalid_GPP_Consent_String"
            it.gdpr = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.warnings.size() == 1
        assert response.warnings[0].startsWith("GPP string invalid: ")

        and: "Privacy for bidder should not be enforced"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == null
        assert bidderStatus?.noCookie == true
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
    }

    def "PBS cookie sync request should respond with an error when gpp_sid is invalid"() {
        given: "Request with invalid GPP SID"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = PBSUtils.randomString
            it.gpp = null
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should respond with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: Request body cannot be parsed"
    }

    def "PBS cookie sync request should return a warning when gpp_sid contains 2, gpp and gdpr_consent are different"() {
        given: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.value
            it.gpp = new TcfEuV2Consent.Builder().build()
            it.gdpr = null
            it.gdprConsent = new TcfConsent.Builder().setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain warning"
        assert response.warnings == ["GPP TCF2 string does not match user.consent"]

        and: "Bidder status should contain rejected by TCF error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"
    }

    def "PBS cookie sync request should return a warning when gpp_sid contains 6, gpp and usp are different"() {
        given: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_V1.value
            it.gpp = new UsV1Consent.Builder().build()
            it.gdpr = null
            it.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Cookie sync response should contain warning"
        assert response.warnings == ["USP string does not match regs.us_privacy"]

        and: "Bidder status should contain rejected by CCPA error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by CCPA"
    }

    def "PBS should return empty gpp and gppSid in usersync url when gpp and gppSid is not present in request"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def pbsConfig = ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                         "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest without gpp and gppSid"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            gpp = null
            gppSid = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url shouldn't contain gpp and gpp_sid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp").isEmpty()
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp_sid").isEmpty()

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat << [REDIRECT, IFRAME]
    }

    def "PBS should populate gpp and gppSid in usersync url when gpp and gppSid is present in request"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def pbsConfig = ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                         "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest with gpp and gppSid"
        def gpp = PBSUtils.randomString
        def gppSid = "${PBSUtils.randomNumber},${PBSUtils.randomNumber}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gpp
            it.gppSid = gppSid
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain gpp and gppSid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp") == gpp
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp_sid") == gppSid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat << [REDIRECT, IFRAME]
    }

    def "PBS should emit proper error message when request contain gdpr config and global skip gdpr config for adapter"() {
        given: "Default CookieSyncRequest with gdpr config"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.intValue
            it.gdpr = 1
            it.gdprConsent = new TcfConsent.Builder().build()
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url shouldn't contain cookies and userSync"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert !bidderStatus.userSync
        assert !bidderStatus.noCookie

        and: "Response should contain proper error message"
        assert bidderStatus.error == "Rejected by regulation scope"
    }

    def "PBS should emit proper error message when alias request contain gdpr config and global skip gdpr config for adapter"() {
        given: "Default CookieSyncRequest with gdpr config"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = [ALIAS]
            it.gppSid = TCF_EU_V2.intValue
            it.gdpr = 1
            it.gdprConsent = new TcfConsent.Builder().build()
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url shouldn't contain cookies and userSync"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert !bidderStatus.userSync
        assert !bidderStatus.noCookie

        and: "Response should contain proper error message"
        assert bidderStatus.error == "Rejected by regulation scope"
    }

    def "PBS should emit proper error message when request contain gpp config and specific global skip gpp config for adapter"() {
        given: "Default CookieSyncRequest with gpp and gppSid"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.intValue
            it.gpp = new UsV1Consent.Builder().build()
            it.gppSid = gppSid
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url shouldn't contain cookies and userSync"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert !bidderStatus.userSync
        assert !bidderStatus.noCookie

        and: "Response should contain proper error message"
        assert bidderStatus.error == "Rejected by regulation scope"

        where:
        gppSid << ["${FIRST_GPP_SECTION.value}",
                   "${SECOND_GPP_SECTION.value}",
                   "${FIRST_GPP_SECTION.value}, ${SECOND_GPP_SECTION.value}",
                   "${SECOND_GPP_SECTION.value}, ${FIRST_GPP_SECTION.value}",
                   "${SECOND_GPP_SECTION.value}, ${FIRST_GPP_SECTION.value}",
                   "${PBSUtils.getRandomEnum(GppSectionId.class, [FIRST_GPP_SECTION, SECOND_GPP_SECTION]).value}, ${SECOND_GPP_SECTION.value}",
                   "${FIRST_GPP_SECTION.value}, ${PBSUtils.getRandomEnum(GppSectionId.class, [FIRST_GPP_SECTION, SECOND_GPP_SECTION]).value}"
        ]
    }

    def "PBS should emit proper error message when alias request contain gpp config and specific global skip gpp config for adapter"() {
        given: "Default CookieSyncRequest with gpp and gppSid"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.intValue
            it.bidders = [ALIAS]
            it.gpp = new UsV1Consent.Builder().build()
            it.gppSid = gppSid
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url shouldn't contain cookies and userSync"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert !bidderStatus.userSync
        assert !bidderStatus.noCookie

        and: "Response should contain proper error message"
        assert bidderStatus.error == "Rejected by regulation scope"

        where:
        gppSid << ["${FIRST_GPP_SECTION.value}",
                   "${SECOND_GPP_SECTION.value}",
                   "${FIRST_GPP_SECTION.value}, ${SECOND_GPP_SECTION.value}",
                   "${SECOND_GPP_SECTION.value}, ${FIRST_GPP_SECTION.value}",
                   "${SECOND_GPP_SECTION.value}, ${FIRST_GPP_SECTION.value}",
                   "${PBSUtils.getRandomEnum(GppSectionId.class, [FIRST_GPP_SECTION, SECOND_GPP_SECTION]).value}, ${SECOND_GPP_SECTION.value}",
                   "${FIRST_GPP_SECTION.value}, ${PBSUtils.getRandomEnum(GppSectionId.class, [FIRST_GPP_SECTION, SECOND_GPP_SECTION]).value}"
        ]
    }

    def "PBS shouldn't emit error message when request doesn't contain gdpr config and global skip gdpr config for adapter"() {
        given: "Default CookieSyncRequest with gdpr config"
        def gppSid = TCF_EU_V2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = gppSid.intValue
            it.gdpr = 0
            it.gdprConsent = new TcfConsent.Builder().build()
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain cookies and userSync"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp") == ""
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp_sid") == gppSid.value

        and: "Response shouldn't contains any error"
        assert !bidderStatus.error
    }

    def "PBS shouldn't emit error message when request does contain gdpr config and global skip gdpr config disabled for adapter"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def pbsConfig = [
                "adapters.${GENERIC.value}.meta-info.vendor-id"                          : GENERIC_VENDOR_ID as String,
                "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
                "adapters.${GENERIC.value}.usersync.skipwhen.gdpr"                       : 'false',
                "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest with gdpr config"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.value
            it.gdpr = 1
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
            it.account = PBSUtils.randomNumber
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P1): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def privacyConfig = new AccountPrivacyConfig(gdpr: accountGdprConfig)
        def account = new Account(uuid: cookieSyncRequest.account, config: new AccountConfig(privacy: privacyConfig))
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper userSync url"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.userSync?.url == USER_SYNC_URL

        and: "Response shouldn't contains any error"
        assert !bidderStatus.error

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't emit error message when request does contain gdpr config and global skip gdpr config default for adapter"() {
        given: "Default CookieSyncRequest with gdpr config"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.value
            it.gdpr = 1
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
            it.account = PBSUtils.randomNumber
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P1): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def privacyConfig = new AccountPrivacyConfig(gdpr: accountGdprConfig)
        def account = new Account(uuid: cookieSyncRequest.account, config: new AccountConfig(privacy: privacyConfig))
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper userSync url"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.userSync?.url == USER_SYNC_URL

        and: "Response shouldn't contains any error"
        assert !bidderStatus.error
    }

    def "PBS shouldn't emit error message when request doesn't contain matched gpp config and specific global skip gpp config for adapter"() {
        given: "Default CookieSyncRequest with gpp and gppSid"
        def gpp = new UsV1Consent.Builder().build()
        def gppSid = "${PBSUtils.getRandomEnum(GppSectionId.class, [FIRST_GPP_SECTION, SECOND_GPP_SECTION]).value}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = TCF_EU_V2.intValue
            it.gpp = gpp
            it.gppSid = gppSid
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain gpp and gppSid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp") == gpp.toString()
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp_sid") == gppSid

        and: "Response shouldn't contains any error"
        assert !bidderStatus.error
    }

    def "PBS should also include validation warning when request matches skip config and has validation issue at same time"() {
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = PBSUtils.getRandomNumberWithExclusion(TCF_EU_V2.intValue)
            it.gdpr = 1
            it.gdprConsent = new TcfConsent.Builder().build()
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerServiceWithSkipConfig.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain a warning"
        assert response.warnings == ["GPP scope does not match TCF2 scope"]

        then: "Privacy for bidder should be enforced"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by regulation scope"
    }
}
