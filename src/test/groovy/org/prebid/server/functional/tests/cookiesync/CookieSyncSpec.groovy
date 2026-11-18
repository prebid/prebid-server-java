//file:noinspection GroovyGStringKey
package org.prebid.server.functional.tests.cookiesync

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.PrivacySandbox
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.cookiesync.FilterSettings
import org.prebid.server.functional.model.request.cookiesync.MethodFilter
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent

import java.util.concurrent.TimeUnit

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.ACEEX
import static org.prebid.server.functional.model.bidder.BidderName.ACUITYADS
import static org.prebid.server.functional.model.bidder.BidderName.ADKERNEL
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.MAGNITE
import static org.prebid.server.functional.model.request.cookiesync.FilterType.EXCLUDE
import static org.prebid.server.functional.model.request.cookiesync.FilterType.INCLUDE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.NO_COOKIE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.OK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.util.HttpUtil.SET_COOKIE_HEADER
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class CookieSyncSpec extends CookieSyncBaseSpec {

    private static PrebidServerService prebidServerService

    def setupSpec() {
        prebidServerService = pbsServiceFactory.getService(PBS_WITH_LIMIT_CONFIG)
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

        flushMetrics(prebidServerService)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Rejected by TCF"

        and: "Metric should contain cookie_sync.BIDDER.tcf.blocked"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 1
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
        def cookieSyncBidders = [GENERIC, MAGNITE, APPNEXUS]
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = cookieSyncBidders
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain valid bidders and 1 bidder with error"
        verifyAll(response.bidderStatus) {
            it.bidder.sort() == cookieSyncBidders.sort()
            it.error.sort() == [null, null, "Limit reached"].sort()
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
        assert configuredBidderStatus?.noCookie == true
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

    def "PBS cookie sync with cookie-sync.default-limit config should use limit from cookie sync config"() {
        given: "Default cookie sync request with 3 bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = [MAGNITE, APPNEXUS, GENERIC]
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
            it.bidders = [MAGNITE, APPNEXUS, GENERIC]
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
            bidders = [GENERIC, APPNEXUS, MAGNITE]
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

        and: "Metric should contain cookie_sync.BIDDER.tcf.blocked"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["cookie_sync.generic.tcf.blocked"] == 1
    }

    def "PBS cookie sync shouldn't emit error limit reached when bidder coop-synced"() {
        given: "Default cookie sync request with single bidder"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.coopSync = true
            it.bidders = [MAGNITE]
            it.limit = 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(GENERIC)
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
            iframe = new MethodFilter(bidders: [MAGNITE], filter: INCLUDE)
            image = new MethodFilter(bidders: [APPNEXUS], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Bidder should be include by filter"
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(MAGNITE)
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
            bidders = [APPNEXUS, GENERIC, MAGNITE]
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
        assert rejectedBidderUserSyncs.every { it.value == "Limit reached" }
    }

    def "PBS cookie sync request should return only requested bidder and reduce image list by filter settings"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            bidders = [MAGNITE, APPNEXUS, ACEEX]
            coopSync = false
            filterSettings = new FilterSettings().tap {
                image = new MethodFilter(bidders: [ACEEX], filter: EXCLUDE)
                iframe = new MethodFilter(bidders: [MAGNITE], filter: INCLUDE)
            }
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain two requested bidders"
        assert response.getBidderStatus().size() == cookieSyncRequest.bidders.size() - 1
        assert response.getBidderUserSync(MAGNITE)
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
        def cookieSyncRequest = new CookieSyncRequest(coopSync: coopSync, bidders: [APPNEXUS, MAGNITE], limit: 1)

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
            bidders = [MAGNITE, GENERIC]
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [MAGNITE, GENERIC], filter: INCLUDE))
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain amount of bidder that define in request"
        assert response.getBidderStatus().size() == cookieSyncRequest.limit
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
            it.bidders = [GENERIC, MAGNITE]
            it.filterSettings = new FilterSettings(image: new MethodFilter(bidders: [GENERIC, MAGNITE, APPNEXUS], filter: INCLUDE))
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains the same size"
        assert response.bidderStatus.bidder.size() == cookieSyncRequest.bidders.size()

        and: "Should contain requested bidders"
        assert response.bidderStatus.bidder.containsAll([GENERIC, MAGNITE])
    }

    def "PBS cookie sync request should return bidders without excluded by filter settings"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            coopSync = false
            bidders = [ACEEX, MAGNITE]
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [ACEEX], filter: EXCLUDE))
            debug = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(ACEEX)
        assert bidderStatus.error == "Rejected by request filter"

        and: "Response should contain one valid bidder"
        assert response.getBidderUserSync(MAGNITE)
    }

    def "PBS cookie sync request shouldn't include bidder when bidder specified in uids cookie"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            coopSync = false
            bidders = [APPNEXUS, MAGNITE]
            filterSettings = new FilterSettings(image: new MethodFilter(bidders: [APPNEXUS, MAGNITE], filter: INCLUDE))
            debug = false
        }

        and: "Given uid cookie"
        def cookie = UidsCookie.getDefaultUidsCookie(APPNEXUS)

        when: "PBS processes cookie sync request with cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, cookie)

        then: "Response should contain valid bidder"
        assert response.getBidderUserSync(MAGNITE)

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
}
