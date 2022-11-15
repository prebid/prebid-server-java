package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.cookiesync.FilterSettings
import org.prebid.server.functional.model.request.cookiesync.MethodFilter
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import spock.lang.Ignore

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.cookiesync.FilterType.EXCLUDE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.NO_COOKIE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.OK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class CookieSyncSpec extends BaseSpec {

    private static final BidderName BIDDER = GENERIC
    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"

    private static Map<String, String> PBS_CONFIG = [
            "adapters.${RUBICON.value}.enabled"                                     : "true",
            "adapters.${RUBICON.value}.usersync.cookie-family-name"                 : RUBICON.value,
            "adapters.${APPNEXUS.value}.enabled"                                    : "true",
            "adapters.${APPNEXUS.value}.usersync.cookie-family-name"                : APPNEXUS.value,
            "adapters.${BIDDER.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${BIDDER.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]

    private PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS cookie sync request with valid uids cookie should return status OK without user sync information"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response should contain information about bidder status"
        def bidderStatus = response.getBidderUserSync(BIDDER)
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

        def bidderStatus = response.getBidderUserSync(BIDDER)
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
                + ["adapters.${BIDDER.value}.usersync.cookie-family-name": bidder.value])

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
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${BIDDER.value}.usersync.cookie-family-name": bidder.value])

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
            bidders = [BIDDER, BOGUS]
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
        def configuredBidderStatus = response.getBidderUserSync(BIDDER)
        assert configuredBidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert configuredBidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert configuredBidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert configuredBidderStatus?.noCookie == true
    }

    def "PBS cookie sync request with bidder without cookie family name should emit an error"() {
        given: "PBS bidder config without cookie family name"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${BIDDER.value}.usersync.cookie-family-name": null])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(BIDDER)
        assert bidderStatus?.error == "No sync config"
        assert bidderStatus?.noCookie == null
        assert bidderStatus?.userSync == null
    }

    def "PBS cookie sync request with disabled bidder should emit an error"() {
        given: "PBS config with disabled bidder"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${BIDDER.value}.enabled": "false"])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(BIDDER)
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
        assert response.bidderStatus*.bidder.sort() == [RUBICON, APPNEXUS, GENERIC].sort()
    }

    def "PBS cookie sync request with alias bidder should sync as the source bidder when alias doesn't override cookie-family-name"() {
        given: "PBS config with alias bidder without cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${BIDDER.value}.aliases.${bidderAlias.value}.enabled"           : "true",
                   "adapters.${BIDDER.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": null,])

        and: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BIDDER, bidderAlias]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def aliasBidderStatus = response.getBidderUserSync(bidderAlias)
        assert aliasBidderStatus.error == "synced as ${BIDDER.value}"

        and: "Response should contain sync information for main bidder"
        def mainBidderStatus = response.getBidderUserSync(BIDDER)
        assert mainBidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert mainBidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert mainBidderStatus?.userSync?.supportCORS == CORS_SUPPORT
        assert mainBidderStatus?.noCookie == true
    }

    def "PBS cookie sync request with alias bidder should sync independently when alias provide cookie-family-name"() {
        given: "PBS config with alias bidder with cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${BIDDER.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                   "adapters.${BIDDER.value}.aliases.${bidderAlias.value}.usersync.cookie-family-name": bidderAlias.value])

        and: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BIDDER, bidderAlias]
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
                ["host-cookie.family"     : BIDDER.value,
                 "host-cookie.cookie-name": cookieName] + PBS_CONFIG)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BIDDER]
        }

        and: "Host cookie"
        def uid = UUID.randomUUID().toString()
        def cookies = [(cookieName): uid]

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain uid from cookies"
        def bidderStatus = response.getBidderUserSync(BIDDER)
        assert HttpUtil.decodeUrl(bidderStatus.userSync?.url).contains("uid=${uid}")
    }

    def "PBS cookie sync request with host cookie should return bidder sync with host cookie uid when uids are different"() {
        given: "PBS bidders config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : BIDDER.value,
                 "host-cookie.cookie-name": cookieName] + PBS_CONFIG)

        and: "Default cookie sync request"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs[BIDDER].uid = UUID.randomUUID().toString()
        }
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BIDDER]
        }

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = [(cookieName): hostCookieUid]

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain uid from cookies"
        def bidderStatus = response.getBidderUserSync(BIDDER)
        assert HttpUtil.decodeUrl(bidderStatus.userSync?.url).contains("uid=${hostCookieUid}")
    }

    def "PBS cookie sync request with host cookie should return an error when host cookie uid matches uids cookie uid for bidder"() {
        given: "PBS bidders config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(
                ["host-cookie.family"     : BIDDER.value,
                 "host-cookie.cookie-name": cookieName] + PBS_CONFIG)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        and: "Default uids cookie"
        def uid = UUID.randomUUID().toString()
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs[BIDDER].uid = uid
        }

        and: "Host cookie"
        def cookies = [(cookieName): uid]

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie, cookies)

        then: "Response should contain already in sync bidder"
        def bidderStatus = response.getBidderUserSync(BIDDER)
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
        def bidderStatus = response.getBidderUserSync(BIDDER)
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

    def "PBS cookie sync with limit and coop-sync.pri should sync bidder which present in coop-sync.pri"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.pri": RUBICON.value] + PBS_CONFIG)

        and: "Default cookie sync request with coop-sync and limit and without requested bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
            limit = 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain rubicon bidder from coop-sync.pri config"
        def rubiconBidder = response.getBidderUserSync(RUBICON)
        assert rubiconBidder?.userSync?.url
        assert rubiconBidder?.userSync?.type
    }

    def "PBS cookie sync with limit and coop-sync.pri should return requested bidder"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.coop-sync.pri": "rubicon"] + PBS_CONFIG)

        and: "Default cookie sync request with coop-sync and limit"
        def requestedBidder = BIDDER
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [requestedBidder]
            coopSync = true
            limit = 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain precedence generic bidder"
        def genericBidder = response.getBidderUserSync(BIDDER)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type

    }

    def "PBS cookie sync with cookie-sync.max-limit config should sync bidder by limit value"() {
        given: "PBS config with bidders usersync config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.max-limit"    : "1",
                 "cookie-sync.default-limit": "1",
                 "adapters.rubicon.enabled" : "true"] + PBS_CONFIG)

        and: "Default cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BIDDER, RUBICON]
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one synced bidder"
        assert response.bidderStatus.size() == 1
    }

    def "PBS cookie sync with coop-sync and config should log: bidder is provided for prioritized coop-syncing but #reason"() {
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
        "disabled in current pbs instance, ignoring" | ["adapters.generic.enabled" : "false",
                                                        "cookie-sync.pri": "generic"]
        "has no user-sync configuration, ignoring"   | ["adapters.generic.usersync.cookie-family-name": "null",
                                                        "cookie-sync.pri"                   : "generic",]
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                ["adapters.generic.ccpa-enforced": "true"] + PBS_CONFIG)

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
}
