//file:noinspection GroovyGStringKey
package org.prebid.server.functional.tests

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.cookiesync.FilterSettings
import org.prebid.server.functional.model.request.cookiesync.MethodFilter
import org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.ACEEX
import static org.prebid.server.functional.model.bidder.BidderName.ACUITYADS
import static org.prebid.server.functional.model.bidder.BidderName.ADKERNEL
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.AAX
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.cookiesync.FilterType.EXCLUDE
import static org.prebid.server.functional.model.request.cookiesync.FilterType.INCLUDE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.NO_COOKIE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.OK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.RUBICON_VENDOR_ID

class CookieSyncSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final String ALL_BIDDERS = "*"
    private static final Integer DEFAULT_PBS_BIDDERS_SIZE = 8

    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.redirect.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.redirect.support-cors": CORS_SUPPORT as String,
            "adapters.${GENERIC.value}.meta-info.vendor-id"           : GENERIC_VENDOR_ID as String]
    private static final Map<String, String> ACEEX_CONFIG = [
            "adapters.${ACEEX.value}.enabled"                       : "true",
            "adapters.${ACEEX.value}.usersync.cookie-family-name"   : ACEEX.value,
            "adapters.${ACEEX.value}.usersync.redirect.url"         : "https://test.redirect.endpoint.com={{redirect_url}}",
            "adapters.${ACEEX.value}.usersync.redirect.support-cors": CORS_SUPPORT as String]
    private static final Map<String, String> RUBICON_CONFIG = [
            "adapters.${RUBICON.value}.enabled"                       : "true",
            "adapters.${RUBICON.value}.meta-info.vendor-id"           : RUBICON_VENDOR_ID as String,
            "adapters.${RUBICON.value}.usersync.cookie-family-name"   : RUBICON.value,
            "adapters.${RUBICON.value}.usersync.redirect.url"         : "https://test.redirect.endpoint.com",
            "adapters.${RUBICON.value}.usersync.redirect.support-cors": CORS_SUPPORT as String,
            "adapters.${RUBICON.value}.usersync.iframe.url"           : "https://test.iframe.endpoint.com&redir={{redirect_url}}",
            "adapters.${RUBICON.value}.usersync.iframe.support-cors"  : CORS_SUPPORT as String]
    private static final Map<String, String> OPENX_CONFIG = [
            "adapters.${OPENX.value}.enabled"                       : "true",
            "adapters.${OPENX.value}.usersync.cookie-family-name"   : OPENX.value,
            "adapters.${OPENX.value}.usersync.redirect.url"         : USER_SYNC_URL,
            "adapters.${OPENX.value}.usersync.redirect.support-cors": CORS_SUPPORT as String,
            "adapters.${OPENX.value}.usersync.iframe.url"           : USER_SYNC_URL,
            "adapters.${OPENX.value}.usersync.iframe.support-cors"  : CORS_SUPPORT as String]
    private static final Map<String, String> APPNEXUS_CONFIG = [
            "adapters.${APPNEXUS.value}.enabled"                       : "true",
            "adapters.${APPNEXUS.value}.usersync.cookie-family-name"   : APPNEXUS.value,
            "adapters.${APPNEXUS.value}.usersync.redirect.url"         : "https://test.appnexus.redirect.com/getuid?{{redirect_url}}",
            "adapters.${APPNEXUS.value}.usersync.redirect.support-cors": CORS_SUPPORT as String]
    private static final Map<String, String> AAX_CONFIG = ["adapters.${AAX.value}.enabled": "true"]
    private static final Map<String, String> ACUITYADS_CONFIG = ["adapters.${ACUITYADS.value}.enabled": "true"]
    private static final Map<String, String> ADKERNEL_CONFIG = ["adapters.${ADKERNEL.value}.enabled": "true"]

    private static final Map<String, String> PBS_CONFIG = APPNEXUS_CONFIG + RUBICON_CONFIG + OPENX_CONFIG +
            GENERIC_CONFIG + ACEEX_CONFIG + AAX_CONFIG + ACUITYADS_CONFIG + ADKERNEL_CONFIG +
            ["cookie-sync.pri": "grid, ix, adkernel"]

    private final PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS cookie sync request should replace synced as family bidder and fill up response with enabled bidders to the limit in request"() {
        given: "PBS config with alias bidder without cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(GENERIC_CONFIG + APPNEXUS_CONFIG
                + ["adapters.${GENERIC.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                   "adapters.${GENERIC.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": null])

        and: "Default cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [bidderAlias]
            limit = requestLimit
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == requestLimit

        and: "Response shouldn't contain alias"
        assert !response.getBidderUserSync(bidderAlias)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request should replace bidder without config and fill up response with enabled bidders to the limit in request"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(RUBICON_CONFIG + APPNEXUS_CONFIG
                + ["adapters.${BOGUS.value}.enabled": "true"])

        and: "Default Cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BOGUS]
            limit = requestLimit
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == requestLimit

        and: "Response shouldn't contain bogus"
        assert !response.getBidderUserSync(BOGUS)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request should replace unknown bidder and fill up response with enabled bidders to the limit in request"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(RUBICON_CONFIG + APPNEXUS_CONFIG)

        and: "Cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BOGUS]
            limit = requestLimit
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == requestLimit

        and: "Response shouldn't contain bogus"
        assert !response.getBidderUserSync(BOGUS)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request should replace disabled bidder and fill up response with enabled bidders to the limit in request"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(RUBICON_CONFIG + APPNEXUS_CONFIG
                + ["adapters.${GENERIC.value}.enabled": "false",])

        and: "Default Cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
            limit = requestLimit
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == requestLimit

        and: "Response shouldn't contain generic"
        assert !response.getBidderUserSync(GENERIC)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request should replace filtered bidder and fill up response with enabled bidders to the limit in request"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(RUBICON_CONFIG + APPNEXUS_CONFIG)

        and: "Cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
            limit = requestLimit
            coopSync = true
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [GENERIC], filter: EXCLUDE))
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == requestLimit

        and: "Response shouldn't contain generic"
        assert !response.getBidderUserSync(GENERIC)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder disabled"() {
        given: "PBS bidder config "
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${GENERIC.value}.enabled": "false"])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't return error"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder without sync config"() {
        given: "PBS bidder config without cookie family name"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${GENERIC.value}.usersync.cookie-family-name": null])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't return error"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder family already in uids cookie"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response shouldn't return error"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder synced as family"() {
        given: "PBS config with alias bidder without cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(GENERIC_CONFIG
                + ["adapters.${GENERIC.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                   "adapters.${GENERIC.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": null,])

        and: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't return error"
        assert !response.getBidderUserSync(bidderAlias)
    }

    def "PBS cookie sync request should reflect error when coop-sync enabled and coop sync bidder with gdpr"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Cookie sync request with gdpr and gdprConsent"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = []
            coopSync = true
            gdpr = 1
            gdprConsent = validConsentString
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"

        and: "Metric should contain cookie_sync.FAMILY.tcf.blocked"
        def metric = this.prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 1
    }

    def "PBS cookie sync request should reflect error when coop-sync enabled and coop sync bidder with ccpa"() {
        given: "PBS bidder config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${bidderName.value}.ccpa-enforced": "true"] + GENERIC_CONFIG)

        and: "Cookie sync request with account and privacy"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = []
            account = PBSUtils.randomString
            usPrivacy = new CcpaConsent(optOutSale: ENFORCED)
            coopSync = true
        }

        and: "Save account config into DB"
        def ccpaConfig = new AccountCcpaConfig(enabled: true)
        def accountConfig = new AccountConfig(privacy: new AccountPrivacyConfig(ccpa: ccpaConfig))
        def account = new Account(uuid: cookieSyncRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(bidderName)
        assert bidderStatus.error == "Rejected by CCPA"
    }

    def "PBS cookie sync request should reflect error when coop-sync enabled and coop sync bidder filtered"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = []
            coopSync = true
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [GENERIC], filter: EXCLUDE))
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by request filter"

        and: "Metric should contain cookie_sync.generic.filtered"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.filtered"] == 1
    }

    def "PBS cookie sync request should reflect error even when response is full by account cookie sync config limit"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: 1)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain 1 valid bidder and 1 bidder with error"
        assert response.bidderStatus.size() == 2

        and: "Response should contain valid generic"
        assert response.getBidderUserSync(GENERIC)

        and: "Response should contain invalid bogus bidder"
        def bogusBidderStatus = response.getBidderUserSync(BOGUS)
        assert bogusBidderStatus?.error == "Unsupported bidder"
        assert bogusBidderStatus?.noCookie == null
        assert bogusBidderStatus?.userSync == null
    }

    def "PBS cookie sync request should reflect error even when response is full by PBS config limit"() {
        given: "PBS config with expanded limit"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": "1"] + PBS_CONFIG)

        and: "Default cookie sync request with coop-sync and without bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain 1 valid bidder and 1 bidder with error"
        assert response.bidderStatus.size() == 2

        and: "Response should contain generic"
        assert response.getBidderUserSync(GENERIC)

        and: "Response should contain error for bogus bidder"
        def bogusBidderStatus = response.getBidderUserSync(BOGUS)
        assert bogusBidderStatus?.error == "Unsupported bidder"
        assert bogusBidderStatus?.noCookie == null
        assert bogusBidderStatus?.userSync == null
    }

    def "PBS cookie sync request should reflect error even when response is full by request limit"() {
        given: "Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.getDefaultCookieSyncRequest().tap {
            bidders = [GENERIC, BOGUS]
            limit = 1
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain 1 valid bidder and 1 bidder with error"
        assert response.bidderStatus.size() == 2

        and: "Response should contain generic"
        assert response.getBidderUserSync(GENERIC)

        and: "Response should contain error for bogus bidder"
        def bogusBidderStatus = response.getBidderUserSync(BOGUS)
        assert bogusBidderStatus?.error == "Unsupported bidder"
        assert bogusBidderStatus?.noCookie == null
        assert bogusBidderStatus?.userSync == null
    }

    def "PBS cookie sync request with valid uids cookie should return status OK without user sync information"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response should contain information about bidder status"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Already in sync"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync request without uids cookie should return bidder user sync information"() {
        given: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain sync information for configured bidder"
        assert response.bidderStatus.size() == 1

        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert bidderStatus?.noCookie == true
    }

    def "PBS cookie sync request should not emit error for already synced bidder without debug flag"() {
        given: "Default cookie sync request without debug flag"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            debug = false
        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response should not contain information about bidder status"
        assert response.bidderStatus.size() == 0
    }

    def "PBS cookie sync should be able to define cookie family name"() {
        given: "PBS bidder config with defined cookie family name"
        def bidder = BOGUS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${GENERIC.value}.usersync.cookie-family-name": bidder.value])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain overridden bidder"
        assert response.bidderStatus.size() == 1
        def bidderStatus = response.getBidderUserSync(bidder)
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert bidderStatus?.noCookie == true
    }

    def "PBS cookie sync should be able to read custom cookie family name from uids cookie"() {
        given: "PBS bidder config with defined cookie family name"
        def bidder = BOGUS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["adapters.${GENERIC.value}.usersync.cookie-family-name": bidder.value])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.getDefaultUidsCookie(bidder)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response should contain information about bidder status"
        assert response.bidderStatus.size() == 1
        def bidderStatus = response.getBidderUserSync(bidder)
        assert bidderStatus.error == "Already in sync"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync request with unknown bidder respond with an error for that bidder"() {
        given: "Default cookie sync request with bogus bidder"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain information for all requested bidders"
        assert response.status == NO_COOKIE
        assert response.bidderStatus.size() == cookieSyncRequest.bidders.size()

        and: "Response should contain error for unknown bidder"
        def unknownBidderStatus = response.getBidderUserSync(BOGUS)
        assert unknownBidderStatus?.error == "Unsupported bidder"
        assert unknownBidderStatus?.noCookie == null
        assert unknownBidderStatus?.userSync == null

        and: "Response should contain sync information for configured bidder"
        def configuredBidderStatus = response.getBidderUserSync(GENERIC)
        assert configuredBidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert configuredBidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert configuredBidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert configuredBidderStatus?.noCookie == true
    }

    def "PBS cookie sync request with bidder without cookie family name should emit an error"() {
        given: "PBS bidder config without cookie family name"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${GENERIC.value}.usersync.cookie-family-name": null])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "No sync config"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync request with disabled bidder should emit an error"() {
        given: "PBS config with disabled bidder"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${GENERIC.value}.enabled": "false"])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "Disabled bidder"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync with enabled coop-sync should sync all enabled bidders"() {
        given: "PBS config with expanded limit"
        def countOfEnabledBidders = 3
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": countOfEnabledBidders.toString()] + PBS_CONFIG)

        and: "Default cookie sync request with coop-sync and without bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain all 3 enabled bidders"
        assert response.bidderStatus.size() == countOfEnabledBidders
    }

    def "PBS cookie sync request with alias bidder should sync as the source bidder when alias doesn't override cookie-family-name"() {
        given: "PBS config with alias bidder without cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${GENERIC.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                   "adapters.${GENERIC.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": null])

        and: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, bidderAlias]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def aliasBidderStatus = response.getBidderUserSync(bidderAlias)
        assert aliasBidderStatus.error == "synced as ${GENERIC.value}"

        and: "Response should contain sync information for main bidder"
        def mainBidderStatus = response.getBidderUserSync(GENERIC)
        assert mainBidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert mainBidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert mainBidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert mainBidderStatus?.noCookie == true
    }

    def "PBS cookie sync request with alias bidder should sync independently when alias provide cookie-family-name"() {
        given: "PBS config with alias bidder with cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${GENERIC.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                   "adapters.${GENERIC.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": bidderAlias.value])

        and: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, bidderAlias]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS should return sync for both bidders"
        assert response.bidderStatus.size() == cookieSyncRequest.bidders.size()
        response.bidderStatus.each {
            assert it.userSync?.url?.startsWith(USER_SYNC_URL)
            assert it.userSync?.type == USER_SYNC_TYPE
            assert it.userSync?.supportCORS == CORS_SUPPORT
            assert it.noCookie == true
        }
    }

    def "PBS cookie sync request with host cookie should return bidder sync with host cookie uid when there is no uids cookie"() {
        given: "PBS bidders config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : GENERIC.value,
                 "host-cookie.cookie-name": cookieName] + PBS_CONFIG)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
        }

        and: "Host cookie"
        def uid = UUID.randomUUID().toString()
        def cookies = [(cookieName): uid]

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain uid from cookies"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.decodeUrl(bidderStatus.userSync?.url).contains("uid=${uid}")
    }

    def "PBS cookie sync request with host cookie should return bidder sync with host cookie uid when uids are different"() {
        given: "PBS bidders config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : GENERIC.value,
                 "host-cookie.cookie-name": cookieName] + PBS_CONFIG)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs[GENERIC].uid = UUID.randomUUID().toString()
        }
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
        }

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = [(cookieName): hostCookieUid]

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain uid from cookies"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.decodeUrl(bidderStatus.userSync?.url).contains("uid=${hostCookieUid}")
    }

    def "PBS cookie sync request with host cookie should return an error when host cookie uid matches uids cookie uid for bidder"() {
        given: "PBS bidders config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : GENERIC.value,
                 "host-cookie.cookie-name": cookieName] + PBS_CONFIG)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        and: "Default uids cookie"
        def uid = UUID.randomUUID().toString()
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs[GENERIC].uid = uid
        }

        and: "Host cookie"
        def cookies = [(cookieName): uid]

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain already in sync bidder"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "Already in sync"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync request with host cookie shouldn't return bidder sync when host cookie doesn't match requested bidder"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : PBSUtils.randomString,
                 "host-cookie.cookie-name": PBSUtils.randomString] + PBS_CONFIG)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should contain already in sync bidder"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "Already in sync"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null

        where:
        hostCookieFamily      || hostCookieName
        PBSUtils.randomString || PBSUtils.randomString
        null                  || PBSUtils.randomString
        PBSUtils.randomString || null
    }

    def "PBS cookie sync request with host cookie shouldn't return bidder sync when host cookie doesn't have configured name"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : bidderName.value,
                 "host-cookie.cookie-name": null] + PBS_CONFIG)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [bidderName]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should contain already in sync bidder"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "Already in sync"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync without cookie-sync.default-limit config and with cookie sync account config limit should use limit from request"() {
        given: "Default cookie sync request with 3 bidders"
        def requestLimit = 1
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            limit = requestLimit
            bidders = [RUBICON, APPNEXUS, GENERIC]
            account = accountId
            debug = false
        }

        and: "Save account with cookie sync config"
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: 2)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain one synced bidder"
        assert response.bidderStatus.size() == requestLimit
    }

    def "PBS cookie sync with cookie-sync.default-limit config should use limit from cookie sync account config"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": "2"] + PBS_CONFIG)

        and: "Default cookie sync request with 3 bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [RUBICON, APPNEXUS, GENERIC]
            account = accountId
            debug = false
        }

        and: "Save account with cookie config"
        def accountDefaultLimit = 1
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: accountDefaultLimit)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain one synced bidder"
        assert response.bidderStatus.size() == accountDefaultLimit
    }

    def "PBS cookie sync with cookie-sync.default-limit config should use limit from request"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": "2"] + PBS_CONFIG)

        and: "Default cookie sync request with 3 bidders"
        def requestLimit = 1
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            limit = requestLimit
            bidders = [RUBICON, APPNEXUS, GENERIC]
            debug = false
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only two synced bidder"
        assert response.bidderStatus.size() == requestLimit
    }

    def "PBS cookie sync with cookie-sync.default-limit config should use limit from PBS config"() {
        given: "PBS config"
        def defaultLimit = 1
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": defaultLimit.toString()] + PBS_CONFIG)

        and: "Default cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, RUBICON]
            debug = false
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one synced bidder"
        assert response.bidderStatus.size() == defaultLimit
    }

    def "PBS cookie sync with cookie-sync.max-limit should use max-limit from PBS config"() {
        given: "PBS bidders config"
        def maxLimit = 2
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.max-limit"    : maxLimit.toString(),
                 "cookie-sync.default-limit": "1"] + PBS_CONFIG)

        and: "Default cookie sync request with 3 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, APPNEXUS, RUBICON]
            limit = 5
            debug = false
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one synced bidder"
        assert response.bidderStatus.size() == maxLimit
    }

    def "PBS cookie sync with cookie-sync.max-limit should use max-limit from cookie sync account config"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": "1",
                 "cookie-sync.max-limit"    : "1"] + PBS_CONFIG)

        and: "Default cookie sync request with 3 bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, APPNEXUS, RUBICON]
            limit = 5
            account = accountId
            debug = false
        }

        and: "Save account with cookie sync config"
        def maxLimit = 2
        def cookieSyncConfig = new AccountCookieSyncConfig(maxLimit: maxLimit)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only two synced bidder"
        assert response.bidderStatus.size() == maxLimit
    }

    def "PBS cookie sync with cookie-sync.pri and enabled coop-sync in config should sync bidder which present in cookie-sync.pri config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.coop-sync.default": "true",
                 "cookie-sync.pri"              : bidderName.value] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from cookie-sync.pri config"
        def genericBidder = response.getBidderUserSync(bidderName)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with cookie-sync.pri and disabled coop-sync in config shouldn't sync bidder which present in cookie-sync.pir config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.coop-sync.default": "false",
                 "cookie-sync.pri"              : bidderName.value] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder from cookie-sync.pri config"
        assert !response.getBidderUserSync(bidderName)
    }

    def "PBS cookie sync with cookie-sync.pri and in all places disabled coop sync in account shouldn't sync bidder which present in cookie-sync.pir config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri"              : bidderName.value,
                 "cookie-sync.coop-sync.default": "false"] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false))
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder which present in cookie-sync.pri config"
        assert !response.getBidderUserSync(bidderName)
    }

    def "PBS cookie sync should sync bidder by limit value"() {
        given: "PBS config with bidders usersync config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

        and: "Default cookie sync request with 2 bidders and limit of 1"
        def limit = 1
        def bidders = [GENERIC, RUBICON]
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.limit = limit
            it.bidders = bidders
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one valid user sync"
        def validBidderUserSyncs = getValidBidderUserSyncs(response)
        assert validBidderUserSyncs.size() == limit
        validBidderUserSyncs.every {
            it.value.url
            it.value.type
        }

        and: "Discarded bidder user sync should contain an error"
        def rejectedBidderUserSyncs = getRejectedBidderUserSyncs(response)
        assert rejectedBidderUserSyncs.size() == bidders.size() - limit
        assert rejectedBidderUserSyncs.every { it.value == "limit reached" }
    }

    def "PBS cookie sync with enabled coop-sync in request and when bidder invalid should log error: bidder is provided for prioritized coop-syncing but #reason"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(config)

        and: "Cookie sync request with coop-sync"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS log should contain message"
        def logs = prebidServerService.getLogsByTime(startTime)
        assert getLogsByText(logs, reason).size() == 1

        where:
        reason                                       | config
        "is invalid bidder name, ignoring"           | ["cookie-sync.pri": PBSUtils.randomString]
        "disabled in current pbs instance, ignoring" | ["adapters.generic.enabled": "false",
                                                        "cookie-sync.pri"         : GENERIC.value]
        "has no user-sync configuration, ignoring"   | ["adapters.generic.usersync.cookie-family-name": "null",
                                                        "cookie-sync.pri"                             : GENERIC.value]
    }

    def "PBS cookie sync with enabled coop-sync in PBS config and when bidder invalid should log error: bidder is provided for prioritized coop-syncing but #reason"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(config +
                ["cookie-sync.coop-sync.default": "true"])

        and: "Cookie sync request with coop-sync"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = null
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS log should contain message"
        def logs = prebidServerService.getLogsByTime(startTime)
        assert getLogsByText(logs, reason).size() == 1

        where:
        reason                                       | config
        "is invalid bidder name, ignoring"           | ["cookie-sync.pri": PBSUtils.randomString]
        "disabled in current pbs instance, ignoring" | ["adapters.generic.enabled": "false",
                                                        "cookie-sync.pri"         : GENERIC.value]
        "has no user-sync configuration, ignoring"   | ["adapters.generic.usersync.cookie-family-name": "null",
                                                        "cookie-sync.pri"                             : GENERIC.value]
    }

    def "PBS cookie sync with enabled coop-sync in account cookie sync and when bidder invalid should log error: bidder is provided for prioritized coop-syncing but #reason"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(config +
                ["cookie-sync.coop-sync.default": "false"])

        and: "Cookie sync request with disabled coop-sync"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false))
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS log should contain message"
        def logs = prebidServerService.getLogsByTime(startTime)
        assert getLogsByText(logs, reason).size() == 1

        where:
        reason                                       | config
        "is invalid bidder name, ignoring"           | ["cookie-sync.pri": PBSUtils.randomString]
        "disabled in current pbs instance, ignoring" | ["adapters.generic.enabled": "false",
                                                        "cookie-sync.pri"         : GENERIC.value]
        "has no user-sync configuration, ignoring"   | ["adapters.generic.usersync.cookie-family-name": "null",
                                                        "cookie-sync.pri"                             : GENERIC.value]
    }

    def "PBS cookie sync with cookie-sync.pri and enabled coop sync should sync bidder which present in cookie-sync.pri"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri"              : bidderName.value,
                 "cookie-sync.coop-sync.default": "false"] + GENERIC_CONFIG)

        and: "Default cookie sync request with coop-sync"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder"
        def genericBidder = response.getBidderUserSync(bidderName)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with pri and enabled coop sync in cookie sync account should sync bidder which present in pir account config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri"              : "null",
                 "cookie-sync.coop-sync.default": "false"] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(pri: [bidderName.value], coopSync: new AccountCoopSyncConfig(enabled: true))
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from pri account config"
        def genericBidder = response.getBidderUserSync(bidderName)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with coop-sync.default config and pri in cookie sync account should sync bidder which present in pir account config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri"              : "null",
                 "cookie-sync.coop-sync.default": "true"] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(pri: [bidderName.value])
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from pri account config"
        def genericBidder = response.getBidderUserSync(bidderName)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with cookie-sync.pri and enabled coop sync in account should sync bidder which present in cookie-sync.pir config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri"              : bidderName.value,
                 "cookie-sync.coop-sync.default": "false"] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: true))
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from cookie-sync.pri config"
        def genericBidder = response.getBidderUserSync(bidderName)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with cookie-sync.pri and disabled coop-sync in request shouldn't sync bidder which present in cookie-sync.pri config"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri"              : bidderName.value,
                 "cookie-sync.coop-sync.default": "false"] + GENERIC_CONFIG)

        and: "Default cookie sync request without coop-sync and bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = false
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(bidderName)
    }

    def "PBS cookie sync with filter setting should reject bidder sync"() {
        given: "Cookie sync request with filter settings"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [GENERIC], filter: EXCLUDE))
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by request filter"

        and: "Metric should contain cookie_sync.FAMILY.filtered"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.filtered"] == 1
    }

    def "PBS cookie sync with gdpr should reject bidder sync"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Cookie sync request with gdpr and gdprConsent"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            gdpr = 1
            gdprConsent = validConsentString
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"

        and: "Metric should contain cookie_sync.FAMILY.tcf.blocked"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 1
    }

    def "PBS cookie sync with ccpa should reject bidder sync"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${GENERIC.value}.ccpa-enforced": "true"] + GENERIC_CONFIG)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Cookie sync request with account and privacy"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = PBSUtils.randomString
            usPrivacy = new CcpaConsent(optOutSale: ENFORCED)
        }

        and: "Save account config into DB"
        def ccpaConfig = new AccountCcpaConfig(enabled: true)
        def accountConfig = new AccountConfig(privacy: new AccountPrivacyConfig(ccpa: ccpaConfig))
        def account = new Account(uuid: cookieSyncRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by CCPA"
    }

    def "PBS cookie sync should emit error when requested bidder rejected by limit"() {
        given: "PBS config with bidders usersync config"
        def limit = 1
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.max-limit"    : limit as String,
                 "cookie-sync.default-limit": limit as String] + PBS_CONFIG)

        and: "Default cookie sync request with 2 bidders"
        def bidders = [GENERIC, RUBICON]
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = bidders
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one valid user sync"
        def validBidderUserSyncs = getValidBidderUserSyncs(response)
        assert validBidderUserSyncs.size() == limit

        and: "Discarded bidder user sync should contain an error"
        def rejectedBidderUserSyncs = getRejectedBidderUserSyncs(response)
        assert rejectedBidderUserSyncs.size() == bidders.size() - limit
        assert rejectedBidderUserSyncs.every { it.value == "limit reached" }
    }

    def "PBS cookie sync shouldn't emit error limit reached when bidder coop-synced"() {
        given: "PBS config with bidders usersync config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.max-limit"    : "1",
                 "cookie-sync.default-limit": "1"] + PBS_CONFIG)

        and: "Default cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            bidders = [RUBICON]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request should successful pass when request body empty"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest()

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain '#formatParam' format parameter"
        def genericBidderStatus = response.getBidderUserSync(ACEEX)
        assert genericBidderStatus?.userSync?.type == REDIRECT
        assert HttpUtil.findUrlParameterValue(genericBidderStatus.userSync?.url, "f") == PIXEL.name

        and: "Response should contain '#formatParam' format parameter"
        def rubiconBidderStatus = response.getBidderUserSync(RUBICON)
        assert rubiconBidderStatus?.userSync?.type == IFRAME
        assert HttpUtil.findUrlParameterValue(rubiconBidderStatus.userSync?.url, "f") == BLANK.name

        and: "Response should contain coop-synced bidder"
        assert response.bidderStatus.bidder.containsAll(ADKERNEL, ACUITYADS, ACEEX, APPNEXUS, AAX, RUBICON, OPENX, GENERIC)
    }

    def "PBS cookie sync request should return bidder"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            image = new MethodFilter().tap {
                filter = EXCLUDE
                bidders = [APPNEXUS]
            }
        }
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.filterSettings = filterSettings
            it.limit = 0
            it.coopSync = false
            it.debug = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Bidder should be excluded by filter"
        assert !response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request should include all bidder due to filterSettings"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [RUBICON], filter: INCLUDE)
            image = new MethodFilter(bidders: [APPNEXUS], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Bidder should be include by filter"
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(RUBICON)
    }

    def "PBS cookie sync request should exclude all iframe bidders when asterisk present in bidders filterSettings"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: ALL_BIDDERS, filter: EXCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain only bidders with IMAGE"
        assert response?.bidderStatus?.every { it.getUserSync().type == REDIRECT }
    }

    def "PBS cookie sync request should exclude all image bidders when asterisk present in bidders filterSettings"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            image = new MethodFilter(bidders: ALL_BIDDERS, filter: EXCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contains all bidders with IFRAME"
        assert response?.bidderStatus?.every { it.getUserSync().type == IFRAME }
    }

    def "PBS cookie sync request shouldn't include bidder with invalid user sync type"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [GENERIC], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain bidders with Redirect type"
        def userSync = response.getBidderUserSync(GENERIC)
        assert userSync?.userSync?.type == REDIRECT
    }

    def "PBS cookie sync request shouldn't exclude bidder with invalid user sync type"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [GENERIC], filter: EXCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain bidders with Redirect type"
        def userSync = response.getBidderUserSync(GENERIC)
        assert userSync?.userSync?.type == REDIRECT
    }

    def "PBS cookie sync request should ignore iframe invalid bidder in method filter bidders"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [bidders], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response shouldn't contain invalid bidders"
        assert !response.getBidderUserSync(bidders)

        where:
        bidders << [BOGUS, null]
    }

    def "PBS cookie sync request should ignore image invalid bidder in method filter bidders"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            image = new MethodFilter(bidders: [bidders], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response shouldn't contain invalid bidders"
        assert !response.getBidderUserSync(bidders)

        where:
        bidders << [BOGUS, null]
    }

    def "PBS cookie sync request should successfully passed when account is absent"() {
        given: "Cookie sync request with empty account"
        def cookieSyncRequest = new CookieSyncRequest(account: null)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain configured bidders"
        assert response?.getBidderStatus()?.bidder?.sort() ==
                [GENERIC, RUBICON, APPNEXUS, OPENX, ACEEX, ACUITYADS, AAX, ADKERNEL].sort()
    }

    def "PBS cookie sync request should return url for all bidders when no uids cookie is present"() {
        given: "Cookie sync request with empty account"
        def cookieSyncRequest = new CookieSyncRequest()

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain info for all configured bidder"
        def bidderStatus = response?.bidderStatus?.userSync
        assert bidderStatus?.url
        assert bidderStatus?.type
        assert bidderStatus?.supportCORS?.every(it -> it == CORS_SUPPORT)
    }

    def "PBS cookie sync request shouldn't return sync url when active uids cookie is present for bidder"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest()

        and: "Set up uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request with uids cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response shouldn't contain bidder"
        assert !response?.getBidderUserSync(GENERIC)

        and: "Response should contain configured bidders"
        assert response?.getBidderStatus()?.bidder?.sort() ==
                [RUBICON, APPNEXUS, OPENX, ACEEX, ACUITYADS, AAX, ADKERNEL].sort()
    }

    def "PBS cookie sync request shouldn't return iframe sync url included by sync type bidders for bidder in cookie"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [GENERIC], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        and: "Set up uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request with uids cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request shouldn't return image sync url included by sync type bidders for bidder in cookie"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            image = new MethodFilter(bidders: [APPNEXUS], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        and: "Set up uids cookie with appnexus"
        def uidsCookie = UidsCookie.getDefaultUidsCookie(APPNEXUS)

        when: "PBS processes cookie sync request with generic uid cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request shouldn't return requested bidder when bidders present in uids cookie"() {
        given: "Cookie sync request with generic bidders"
        def cookieSyncRequest = new CookieSyncRequest(bidders: [GENERIC])

        and: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request with generic uid cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request should return all possible bidder"() {
        given: "Cookie sync request with non specified bidders and specified coop sync"
        def cookieSyncRequest = new CookieSyncRequest(bidders: null, coopSync: true)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain coop sync bidders"
        assert response.bidderStatus.size() == DEFAULT_PBS_BIDDERS_SIZE
        assert response.getBidderUserSync(AAX)
        assert response.getBidderUserSync(ACUITYADS)
        assert response.getBidderUserSync(ADKERNEL)
        assert response.getBidderUserSync(OPENX)
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(ACEEX)
    }

    def "PBS cookie sync request should contain request bidders and rest from coop sync"() {
        given: "PBS config with expanded limit"
        def defaultSyncLimit = 3
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": defaultSyncLimit as String] + PBS_CONFIG)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [APPNEXUS, GENERIC]
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        then: "Response should contain requested bidder and coop-synced"
        assert response.getBidderStatus().size() > cookieSyncRequest.bidders.size()
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request should contain only request bidders when coop sync off"() {
        given: "PBS config with expanded limit"
        def defaultSyncLimit = 3
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": defaultSyncLimit as String] + PBS_CONFIG)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [APPNEXUS, GENERIC]
            coopSync = false
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        then: "Response should contain requested bidder and coop-synced"
        assert response.getBidderStatus().size() == cookieSyncRequest.bidders.size()
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request should contain only request bidders with limit when coop sync off and limit specified"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [APPNEXUS, GENERIC, RUBICON]
            coopSync = false
            limit = 2
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain requested bidder limit"
        def rejectedBidderUserSyncs = getRejectedBidderUserSyncs(response)
        assert rejectedBidderUserSyncs.size() == cookieSyncRequest.bidders.size() - cookieSyncRequest.limit
        assert rejectedBidderUserSyncs.every { it.value == "limit reached" }
    }

    def "PBS cookie sync request should return only requested bidder and reduce image list by filter settings"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            bidders = [RUBICON, APPNEXUS, ACEEX]
            coopSync = false
            filterSettings = new FilterSettings().tap {
                image = new MethodFilter(bidders: [ACEEX], filter: EXCLUDE)
                iframe = new MethodFilter(bidders: [RUBICON], filter: INCLUDE)
            }
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain two requested bidders"
        assert response.getBidderStatus().size() == cookieSyncRequest.bidders.size() - 1
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)

        and: "Response shouldn't contain requested bidders due to filter"
        assert !response.getBidderUserSync(ACEEX)
    }

    def "PBS cookie sync request shouldn't return any bidder when coop sync off and bidder is invalid"() {
        given: "Cookie sync request body with bidders and disabled coop-sync"
        def cookieSyncRequest = new CookieSyncRequest(coopSync: false, bidders: givenBidder)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response shouldn't contain amount of invalid bidder"
        assert response.getBidderStatus().size() == 0

        where:
        givenBidder << [[BOGUS], []]
    }

    def "PBS cookie sync request shouldn't return all bidders when coop sync #coopSync and limit param specified"() {
        given: "Cookie sync request with bidders and limit"
        def cookieSyncRequest = new CookieSyncRequest(coopSync: coopSync, bidders: [APPNEXUS, RUBICON], limit: 1)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain amount of bidder that define in request"
        assert response.getBidderStatus().size() == cookieSyncRequest.limit

        where:
        coopSync << [true, false]
    }

    def "PBS cookie sync request shouldn't increase amount of bidder then define in request"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            limit = 1
            bidders = [RUBICON, GENERIC]
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [RUBICON, GENERIC], filter: INCLUDE))
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain amount of bidder that define in request"
        assert response.getBidderStatus().size() == cookieSyncRequest.limit
    }

    def "PBS cookie sync request should return eight users sync bidders when limit isn't define"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            limit = null
            bidders = null
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain eight bidder by default"
        assert response?.getBidderStatus()?.size() == DEFAULT_PBS_BIDDERS_SIZE
    }

    def "PBS cookie sync request should limit the number of returned bidders"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.limit = PBSUtils.getRandomNumber(1, 3)
            it.coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status"
        assert response.getBidderStatus().size() == cookieSyncRequest.limit
    }

    def "PBS cookie sync request should return bidders matched in bidders and filter settings"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.coopSync = false
            it.bidders = [GENERIC, RUBICON]
            it.filterSettings = new FilterSettings(image: new MethodFilter(bidders: [GENERIC, RUBICON, APPNEXUS], filter: INCLUDE))
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains the same size"
        assert response.bidderStatus.bidder.size() == cookieSyncRequest.bidders.size()

        and: "Should contain requested bidders"
        assert response.bidderStatus.bidder.containsAll(GENERIC, RUBICON)
    }

    def "PBS cookie sync request should fill response with all available coop sync bidder when limit is not specified"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.coopSync = true
            it.limit = null
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains all available coop sync bidders"
        assert response.bidderStatus.bidder.size() == DEFAULT_PBS_BIDDERS_SIZE
    }

    def "PBS cookie sync request should override general configuration limit"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.coopSync = true
            it.limit = PBSUtils.getRandomNumber(1, DEFAULT_PBS_BIDDERS_SIZE)
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains same count of bidder as specified limit"
        assert response.bidderStatus.size() == cookieSyncRequest.limit
    }

    def "PBS cookie sync request should return bidders without excluded by filter settings"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            coopSync = false
            bidders = [ACEEX, RUBICON]
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [ACEEX], filter: EXCLUDE))
            debug = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(ACEEX)
        assert bidderStatus.error == "Rejected by request filter"

        and: "Response should contain one valid bidder"
        assert response.getBidderUserSync(RUBICON)
    }

    def "PBS cookie sync request shouldn't include bidder when bidder specified in uids cookie"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            coopSync = false
            bidders = [APPNEXUS, RUBICON]
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [APPNEXUS, RUBICON], filter: INCLUDE))
            debug = false
        }

        and: "Given uid cookie"
        def cookie = UidsCookie.getDefaultUidsCookie(APPNEXUS)

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, cookie)

        then: "Response should contain valid bidder"
        assert response.getBidderUserSync(RUBICON)

        and: "Response shouldn't contain bidder that present in uids cookie"
        assert !response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request shouldn't limit bidders with zero value in config"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: 0)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain valid bidder status"
        assert response.bidderStatus.size() == cookieSyncRequest.bidders.size()
    }

    def "PBS cookie sync request should max limit override default limit"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            debug = false
        }

        and: "Save account with cookie config"
        def maxLimit = 1
        def cookieSyncConfig = new AccountCookieSyncConfig(maxLimit: maxLimit, defaultLimit: 2)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to config"
        assert response.bidderStatus.size() == maxLimit
    }

    def "PBS cookie sync request should max limit in account take precedence over request limit"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            limit = 2
            debug = false
        }

        and: "Save account with cookie config"
        def maxLimit = 1
        def cookieSyncConfig = new AccountCookieSyncConfig(maxLimit: maxLimit)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to config"
        assert response.bidderStatus.size() == maxLimit
    }

    def "PBS cookie sync request should capped to max limit"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            limit = limitRequest
            debug = false
        }

        and: "Save account with cookie config"
        def maxLimit = 1
        def cookieSyncConfig = new AccountCookieSyncConfig(maxLimit: maxLimit)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to config"
        assert response.bidderStatus.size() == maxLimit

        where:
        limitRequest << [0, 2, null]
    }

    def "PBS cookie sync request should account limit override general configuration limit when not limit in request"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            limit = null
            debug = false
        }

        and: "Save account with cookie config"
        def defaultLimit = 1
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: defaultLimit)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to config"
        assert response.bidderStatus.size() == defaultLimit
    }

    def "PBS cookie sync request should take precedence request limit over account and global config"() {
        given: "PBS config with expanded limit"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.default-limit": "3"] + PBS_CONFIG)

        and: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            limit = 1
            debug = false
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: 2)
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to request limit"
        assert response.bidderStatus.size() == cookieSyncRequest.limit
    }

    def "PBS cookie sync request should take presence coop sync over coop sync in config"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            coopSync = true
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: accountCoopSyncConfig))
        def accountConfig = new AccountConfig(status: AccountStatus.ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to global config"
        assert response.bidderStatus.size() == 9

        where:
        accountCoopSyncConfig << [false, true, null]
    }

    private static Map<BidderName, UserSyncInfo> getValidBidderUserSyncs(CookieSyncResponse cookieSyncResponse) {
        cookieSyncResponse.bidderStatus
                          .findAll { it.userSync }
                          .collectEntries { [it.bidder, it.userSync] }
    }

    private static Map<BidderName, String> getRejectedBidderUserSyncs(CookieSyncResponse cookieSyncResponse) {
        cookieSyncResponse.bidderStatus
                          .findAll { it.error }
                          .collectEntries { [it.bidder, it.error] }
    }
}
