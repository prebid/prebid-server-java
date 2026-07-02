//file:noinspection GroovyGStringKey
package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.PrivacySandbox
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.cookiesync.FilterSettings
import org.prebid.server.functional.model.request.cookiesync.MethodFilter
import org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.Metrics
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent

import java.util.concurrent.TimeUnit

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.AAX
import static org.prebid.server.functional.model.bidder.BidderName.ACEEX
import static org.prebid.server.functional.model.bidder.BidderName.ACUITYADS
import static org.prebid.server.functional.model.bidder.BidderName.ADKERNEL
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
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
import static org.prebid.server.functional.util.HttpUtil.SET_COOKIE_HEADER
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.RUBICON_VENDOR_ID

class CookieSyncSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final Integer COOKIE_SYNC_DEFAULT_LIMIT = 2
    private static final Integer COOKIE_SYNC_MAX_LIMIT = 3
    private static final String RANDOM_COOKIE_NAME = PBSUtils.randomString

    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final String ALL_BIDDERS = "*"
    private static final List<BidderName> DEFAULT_PBS_BIDDERS = [GENERIC, ACEEX, RUBICON, OPENX, APPNEXUS, AAX, ADKERNEL]

    private static final Map<String, String> COOKIE_SYNC_CONFIG = [
            "cookie-sync.max-limit"    : COOKIE_SYNC_MAX_LIMIT.toString(),
            "cookie-sync.default-limit": COOKIE_SYNC_DEFAULT_LIMIT.toString()]
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
    private static final Map<String, String> ALIAS_NO_FAMILY_CONFIG = [
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.enabled"                    : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.usersync.cookie-family-name": null]
    private static final Map<String, String> BOGUS_CONFIG = ["adapters.${BOGUS.value}.enabled": "true"]
    private static final Map<String, String> AAX_CONFIG = ["adapters.${AAX.value}.enabled": "true"]
    private static final Map<String, String> ACUITYADS_CONFIG = ["adapters.${ACUITYADS.value}.enabled": "false"]
    private static final Map<String, String> ADKERNEL_CONFIG = ["adapters.${ADKERNEL.value}.enabled": "true"]
    private static final Map<String, String> PBS_CONFIG = APPNEXUS_CONFIG + RUBICON_CONFIG + OPENX_CONFIG +
            GENERIC_CONFIG + ACEEX_CONFIG + AAX_CONFIG + ACUITYADS_CONFIG + ADKERNEL_CONFIG + BOGUS_CONFIG +
            ["cookie-sync.pri": "grid, ix, adkernel"]

    private final PrebidServerService pbsWithoutLimitService = pbsServiceFactory.getService(PBS_CONFIG)
    private final PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + COOKIE_SYNC_CONFIG)
    private final PrebidServerService pbsWithAliasService = pbsServiceFactory.getService(PBS_CONFIG + ALIAS_NO_FAMILY_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(PBS_CONFIG)
        pbsServiceFactory.removeContainer(PBS_CONFIG + COOKIE_SYNC_CONFIG)
        pbsServiceFactory.removeContainer(PBS_CONFIG + ALIAS_NO_FAMILY_CONFIG)
    }

    def "PBS cookie sync request should replace synced as family bidder and fill up response with enabled bidders to the limit in request"() {
        given: "Default cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [ALIAS]
            limit = requestLimit
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == requestLimit

        and: "Response shouldn't contain alias"
        assert !response.getBidderUserSync(ALIAS)

        and: "Response should contain coop-synced bidder"
        assert DEFAULT_PBS_BIDDERS.containsAll(response.bidderStatus.bidder)
    }

    def "PBS cookie sync request should replace bidder without config and fill up response with enabled bidders to the limit in request"() {
        given: "Default Cookie sync request"
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
        assert DEFAULT_PBS_BIDDERS.containsAll(response.bidderStatus.bidder)
    }

    def "PBS cookie sync request should replace unknown bidder and fill up response with enabled bidders to the limit in request"() {
        given: "Cookie sync request"
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
        assert DEFAULT_PBS_BIDDERS.containsAll(response.bidderStatus.bidder)
    }

    def "PBS cookie sync request should replace disabled bidder and fill up response with enabled bidders to the limit in request"() {
        given: "PBS bidder config"
        def pbsConfig = RUBICON_CONFIG + APPNEXUS_CONFIG + ["adapters.${GENERIC.value}.enabled": "false"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        assert DEFAULT_PBS_BIDDERS.containsAll(response.bidderStatus.bidder)
    }

    def "PBS cookie sync request should replace filtered bidder and fill up response with enabled bidders to the limit in request"() {
        given: "Cookie sync request"
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
        assert DEFAULT_PBS_BIDDERS.containsAll(response.bidderStatus.bidder)
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder disabled"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't return error"
        assert response.bidderStatus.error.every { it == null }
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder without sync config"() {
        given: "PBS bidder config without cookie family name"
        def pbsConfig = ["adapters.${GENERIC.value}.usersync.cookie-family-name": null]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        given: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't return error"
        assert !response.getBidderUserSync(ALIAS)
    }

    def "PBS cookie sync request should reflect error when coop-sync enabled and coop sync bidder with gdpr"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .setDisclosedVendors([GENERIC_VENDOR_ID])
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
        assert metric[Metrics.CookieSync.tcfBlocked(GENERIC)] == 1
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
        assert metric[Metrics.CookieSync.filtered(GENERIC)] == 1
    }

    def "PBS cookie sync request should reflect error even when response is full by account cookie sync config limit"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
        }

        and: "Save account with cookie config"
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

        where:
        accountConfig << [new AccountConfig(status: ACTIVE, cookieSync: new AccountCookieSyncConfig(defaultLimit: 1)),
                          new AccountConfig(status: ACTIVE, cookieSyncSnakeCase: new AccountCookieSyncConfig(defaultLimit: 1))]
    }

    def "PBS cookie sync request should reflect error even when response is full by PBS config limit"() {
        given: "Default cookie sync request with coop-sync and without bidders"
        def cookieSyncBidders = [GENERIC, RUBICON, APPNEXUS]
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = cookieSyncBidders
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain valid bidders and 1 bidder with error"
        assert verifyAll(response.bidderStatus) {
            it.bidder.sort() == cookieSyncBidders.sort()
            it.error.sort() == [null, null, "limit reached"].sort()
        }
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
        def pbsConfig = PBS_CONFIG + ["adapters.${GENERIC.value}.usersync.cookie-family-name": bidder.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        def pbsConfig = PBS_CONFIG +
                ["adapters.${GENERIC.value}.usersync.cookie-family-name": bidder.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        def pbsConfig = ["adapters.${GENERIC.value}.usersync.cookie-family-name": null]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.getDefaultCookieSyncRequest([ACUITYADS])

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(ACUITYADS)
        assert bidderStatus?.error == "Disabled bidder"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync with enabled coop-sync should sync all enabled bidders"() {
        given: "Default cookie sync request with coop-sync and without bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain all 2 enabled bidders"
        assert response.bidderStatus.size() == COOKIE_SYNC_DEFAULT_LIMIT
    }

    def "PBS cookie sync request with alias bidder should sync as the source bidder when alias doesn't override cookie-family-name"() {
        given: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, ALIAS]
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def aliasBidderStatus = response.getBidderUserSync(ALIAS)
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
        def pbsConfig = PBS_CONFIG +
                ["adapters.${GENERIC.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                 "adapters.${GENERIC.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": bidderAlias.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        def pbsConfig = ["host-cookie.family"     : GENERIC.value,
                         "host-cookie.cookie-name": RANDOM_COOKIE_NAME] + PBS_CONFIG
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
        }

        and: "Host cookie"
        def uid = UUID.randomUUID().toString()
        def cookies = [(RANDOM_COOKIE_NAME): uid]

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain uid from cookies"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.decodeUrl(bidderStatus.userSync?.url).contains("uid=${uid}")
    }

    def "PBS cookie sync request with host cookie should return bidder sync with host cookie uid when uids are different"() {
        given: "PBS bidders config"
        def pbsConfig = ["host-cookie.family"     : GENERIC.value,
                         "host-cookie.cookie-name": RANDOM_COOKIE_NAME] + PBS_CONFIG
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs[GENERIC].uid = UUID.randomUUID().toString()
        }
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
        }

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = [(RANDOM_COOKIE_NAME): hostCookieUid]

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain uid from cookies"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.decodeUrl(bidderStatus.userSync?.url).contains("uid=${hostCookieUid}")
    }

    def "PBS cookie sync request with host cookie should return an error when host cookie uid matches uids cookie uid for bidder"() {
        given: "PBS bidders config"
        def pbsConfig = ["host-cookie.family"     : GENERIC.value,
                         "host-cookie.cookie-name": RANDOM_COOKIE_NAME] + PBS_CONFIG
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        and: "Default uids cookie"
        def uid = UUID.randomUUID().toString()
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs[GENERIC].uid = uid
        }

        and: "Host cookie"
        def cookies = [(RANDOM_COOKIE_NAME): uid]

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain already in sync bidder"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "Already in sync"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync request with host cookie shouldn't return bidder sync when host cookie doesn't have configured name"() {
        given: "PBS bidders config"
        def bidderName = GENERIC
        def pbsConfig = ["host-cookie.family"     : bidderName.value,
                         "host-cookie.cookie-name": null] + PBS_CONFIG
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [bidderName]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should contain already in sync bidder"
        def bidderStatus = response.getBidderUserSync(bidderName)
        assert bidderStatus?.error == "Already in sync"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync with cookie-sync.default-limit config should use limit from cookie sync config"() {
        given: "Default cookie sync request with 3 bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = [RUBICON, APPNEXUS, GENERIC]
            it.account = accountId
            it.limit = requestLimit
            it.debug = false
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: accountLimit)
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain one synced bidder"
        assert response.bidderStatus.size() == 1

        where:
        requestLimit | accountLimit
        1            | null
        null         | 1
    }

    def "PBS cookie sync request should take precedence request limit over account and global config"() {
        given: "Default cookie sync request with 3 bidders"
        def accountId = PBSUtils.randomNumber
        def requestLimit = 1
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = [RUBICON, APPNEXUS, GENERIC]
            it.account = accountId
            it.limit = requestLimit
            it.debug = false
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: requestLimit + 1)
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain one synced bidder"
        assert response.bidderStatus.size() == requestLimit
    }

    def "PBS cookie sync with cookie-sync.max-limit should use max-limit from PBS config"() {
        given: "Default cookie sync request with 3 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, APPNEXUS, RUBICON]
            limit = COOKIE_SYNC_MAX_LIMIT + 1
            debug = false
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one synced bidder"
        assert response.bidderStatus.size() == COOKIE_SYNC_MAX_LIMIT
    }

    def "PBS cookie sync with cookie-sync.max-limit should use max-limit from cookie sync account config"() {
        given: "Default cookie sync request with 3 bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = DEFAULT_PBS_BIDDERS
            limit = COOKIE_SYNC_MAX_LIMIT + 2
            account = accountId
            debug = false
        }

        and: "Save account with cookie sync config"
        def cookieSyncConfig = new AccountCookieSyncConfig(maxLimit: accountMaxLimit, maxLimitSnakeCase: accountMaxLimitSnakeCase)
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only two synced bidder"
        assert response.bidderStatus.size() == COOKIE_SYNC_MAX_LIMIT + 1

        where:
        accountMaxLimit           | accountMaxLimitSnakeCase
        COOKIE_SYNC_MAX_LIMIT + 1 | null
        null                      | COOKIE_SYNC_MAX_LIMIT + 1
    }


    def "PBS cookie sync should sync bidder by limit value"() {
        given: "Default cookie sync request with 2 bidders and limit of 1"
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
        assert metric[Metrics.CookieSync.filtered(GENERIC)] == 1
    }

    def "PBS cookie sync with gdpr should reject bidder sync"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .setDisclosedVendors([GENERIC_VENDOR_ID])
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
        assert metric[Metrics.CookieSync.tcfBlocked(GENERIC)] == 1
    }

    def "PBS cookie sync with ccpa should reject bidder sync"() {
        given: "PBS bidder config"
        def pbsConfig = ["adapters.${GENERIC.value}.ccpa-enforced": "true"] + GENERIC_CONFIG
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Cookie sync request with account and privacy"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = PBSUtils.randomString
            it.usPrivacy = new CcpaConsent(optOutSale: ENFORCED)
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
        given: "Default cookie sync request with 2 bidders"
        def limit = 1
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = DEFAULT_PBS_BIDDERS
            it.limit = limit
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one valid user sync"
        def validBidderUserSyncs = getValidBidderUserSyncs(response)
        assert validBidderUserSyncs.size() == limit

        and: "Discarded bidder user sync should contain an error"
        def rejectedBidderUserSyncs = getRejectedBidderUserSyncs(response)
        assert rejectedBidderUserSyncs.size() == DEFAULT_PBS_BIDDERS.size() - limit
        assert rejectedBidderUserSyncs.every { it.value == "limit reached" }
    }

    def "PBS cookie sync shouldn't emit error limit reached when bidder coop-synced"() {
        given: "Default cookie sync request with single bidder"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.coopSync = true
            it.bidders = [RUBICON]
            it.limit = 1
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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

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
        assert response.bidderStatus.bidder.containsAll(DEFAULT_PBS_BIDDERS)
    }

    def "PBS cookie sync request shouldn't return bidder due to filter setting request"() {
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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain configured bidders"
        assert response?.getBidderStatus()?.bidder?.sort() == DEFAULT_PBS_BIDDERS.sort()
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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response shouldn't contain bidder"
        assert !response?.getBidderUserSync(GENERIC)

        and: "Response should contain configured bidders"
        assert response?.getBidderStatus()?.bidder?.sort() == (DEFAULT_PBS_BIDDERS - GENERIC).sort()
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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain coop sync bidders"
        assert response.bidderStatus.size() == DEFAULT_PBS_BIDDERS.size()
        assert response.getBidderUserSync(AAX)
        assert response.getBidderUserSync(ADKERNEL)
        assert response.getBidderUserSync(OPENX)
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(ACEEX)
    }

    def "PBS cookie sync request should contain request bidders and rest from coop sync"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [APPNEXUS, GENERIC]
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        then: "Response should contain requested bidder and coop-synced"
        assert response.getBidderStatus().size() > cookieSyncRequest.bidders.size()
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request should contain only request bidders when coop sync off"() {
        given: "Default cookie sync request"
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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain eight bidder by default"
        assert response?.getBidderStatus()?.size() == DEFAULT_PBS_BIDDERS.size()
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
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains all available coop sync bidders"
        assert response.bidderStatus.bidder.size() == DEFAULT_PBS_BIDDERS.size()
    }

    def "PBS cookie sync request should override general configuration limit"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.coopSync = true
            it.limit = PBSUtils.getRandomNumber(1, DEFAULT_PBS_BIDDERS.size())
        }

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

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
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
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
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
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
            bidders = [GENERIC, APPNEXUS, ADKERNEL]
            account = accountId
            limit = null
            debug = false
        }

        and: "Save account with cookie config"
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to config"
        assert response.bidderStatus.size() == 2

        where:
        accountConfig << [new AccountConfig(status: ACTIVE, cookieSyncSnakeCase: new AccountCookieSyncConfig(maxLimit: 2)),
                          new AccountConfig(status: ACTIVE, cookieSync: new AccountCookieSyncConfig(maxLimit: 2))]
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
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
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
        def cookieSyncConfig = new AccountCookieSyncConfig(defaultLimit: accountDefaultLimit, defaultLimitSnakeCase: accountDefaultLimitSnakeCase)
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to config"
        assert response.bidderStatus.size() == 1

        where:
        accountDefaultLimit | accountDefaultLimitSnakeCase
        1                   | null
        null                | 1
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
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to global config"
        assert response.bidderStatus.size() == (DEFAULT_PBS_BIDDERS + BOGUS).size()

        where:
        cookieSyncConfig << [new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: true)),
                             new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false)),
                             new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: null)),
                             new AccountCookieSyncConfig(coopSyncSnakeCase: new AccountCoopSyncConfig(enabled: true)),
                             new AccountCookieSyncConfig(coopSyncSnakeCase: new AccountCoopSyncConfig(enabled: false)),
                             new AccountCookieSyncConfig(coopSyncSnakeCase: new AccountCoopSyncConfig(enabled: null))]
    }

    def "PBS cookie sync request should respond with an error when gdpr param is 1 and consent isn't specified"() {
        given: "Cookie sync request body with gdpr = 1 and gdprConsent = null"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            gdpr = 1
            gdprConsent = null
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def serverException = thrown(PrebidServerException)
        assert serverException.responseBody == "Invalid request format: gdpr_consent is required if gdpr is 1"
    }

    def "PBS shouldn't set cookie deprecation header from the account when privacySandbox is #privacySandbox"() {
        given: "Default cookie sync request with account"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = accountId
        }

        and: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Save account with cookie and privacySandbox configs"
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequestRaw(cookieSyncRequest, uidsCookie)

        then: "Response shouldn't contain cookie header"
        assert !response.headers[SET_COOKIE_HEADER]

        where:
        privacySandbox << [null,
                           PrivacySandbox.getDefaultPrivacySandbox(null),
                           PrivacySandbox.getDefaultPrivacySandbox(false)]
    }

    def "PBS shouldn't set cookie deprecation header from the account when cookies is included in original request"() {
        given: "Default cookie sync request with account"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = accountId
        }

        and: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Save account with cookie and privacySandbox configs"
        def privacySandbox = PrivacySandbox.defaultPrivacySandbox
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def setCookieDefaultHeader = ['receive-cookie-deprecation': '1']
        def response = prebidServerService.sendCookieSyncRequestRaw(cookieSyncRequest, uidsCookie, setCookieDefaultHeader)

        then: "Response shouldn't contain cookie header"
        assert !response.headers[SET_COOKIE_HEADER]
    }

    def "PBS should set cookie deprecation header from the account when cookies is not included in original request"() {
        given: "Default cookie sync request with account"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = accountId
        }

        and: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Save account with cookie and privacySandbox configs"
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequestRaw(cookieSyncRequest, uidsCookie)

        then: "Response should contain cookie header"
        assert removeExpiresValue(response.headers[SET_COOKIE_HEADER]) ==
                ["receive-cookie-deprecation=1; Max-Age=${privacySandbox.cookieDeprecation.ttlSeconds}; Expires=*; Path=/; Secure; HTTPOnly; SameSite=None; Partitioned"]

        where:
        privacySandbox << [PrivacySandbox.defaultPrivacySandbox, PrivacySandbox.getDefaultPrivacySandbox(true, -PBSUtils.randomNumber)]
    }

    def "PBS should set cookie deprecation header on default value of week when ttlSec is not specified in privacy sandbox settings"() {
        given: "Default cookie sync request with account"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = accountId
        }

        and: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Save account with cookie and privacySandbox configs"
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: PrivacySandbox.getDefaultPrivacySandbox(true, null))
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequestRaw(cookieSyncRequest, uidsCookie)

        then: "Response should contain cookie header"
        assert removeExpiresValue(response.headers[SET_COOKIE_HEADER]) ==
                ["receive-cookie-deprecation=1; Max-Age=${TimeUnit.DAYS.toSeconds(7)}; Expires=*; Path=/; Secure; HTTPOnly; SameSite=None; Partitioned"]
    }

    def "PBS should set cookie deprecation header from the default account when default account contain privacy sandbox and request account is empty"() {
        given: "Pbs with PF configuration with privacySandbox"
        def privacySandbox = PrivacySandbox.defaultPrivacySandbox
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            auction = new AccountAuctionConfig(privacySandbox: privacySandbox)
        }
        def pbsService = pbsServiceFactory.getService(PBS_CONFIG +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Cookie sync request body"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = null
        }

        when: "PBS processes cookie sync request"
        def response = pbsService.sendCookieSyncRequestRaw(cookieSyncRequest, uidsCookie)

        then: "Response should contain cookie header"
        assert removeExpiresValue(response.headers[SET_COOKIE_HEADER]) ==
                ["receive-cookie-deprecation=1; Max-Age=${privacySandbox.cookieDeprecation.ttlSeconds}; Expires=*; Path=/; Secure; HTTPOnly; SameSite=None; Partitioned"]
    }

    def "PBS shouldn't set cookie deprecation header when cookie sync request doesn't contain account"() {
        given: "Set up generic uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Cookie sync request body"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            account = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequestRaw(cookieSyncRequest, uidsCookie)

        then: "Response shouldn't contain cookie header"
        assert !response.headers[SET_COOKIE_HEADER]
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

    private static List<String> removeExpiresValue(List<String> cookies) {
        cookies.collect { it.replaceFirst(/Expires=[^;]+;/, "Expires=*;") }
    }
}
