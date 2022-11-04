package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.cookiesync.FilterSettings
import org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class CookieSyncSpec extends BaseSpec {

    PrebidServerService prebidServerService = pbsServiceFactory.getService(
            ["adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
             "adapters.generic.usersync.redirect.support-cors"   : "false",
             "adapters.generic.usersync.redirect.format-override": "blank"])

    def "PBS cookie sync with valid uids cookie should return status OK"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == CookieSyncResponse.Status.OK
    }

    def "PBS cookie sync without uids cookie should return element.usersync.url"() {
        given: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain all bidders"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.url
        assert bidderStatus?.userSync?.type
    }

    def "PBS cookie sync with bidder config should resolve cookie-family-name which hasn't the same name as bidder"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.cookie-family-name"      : "bogus",
                 "adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder but synced as bogus"
        def bidderStatus = response.getBidderUserSync(BOGUS)
        assert bidderStatus?.userSync?.url
        assert bidderStatus?.userSync?.type
    }

    def "PBS cookie sync with coop-sync should sync bidder which has valid config in PBS"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.rubicon.enabled"                          : "true",
                 "adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

        and: "Default cookie sync request with coop-sync and without bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain 2 bidders that synced by PBS config"
        assert response.bidderStatus.size() == 2

        and: "Response should contain rubicon bidder PBS config"
        def rubiconBidder = response.getBidderUserSync(RUBICON)
        assert rubiconBidder?.userSync?.url
        assert rubiconBidder?.userSync?.type

        and: "Response should contain generic bidder PBS config"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with limit and coop-sync.pri should sync bidder which present in coop-sync.pri"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.coop-sync.pri"                         : "rubicon",
                 "adapters.rubicon.enabled"                          : "true",
                 "adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

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

    def "PBS cookie sync with limit and coop-sync.pri should return bidder from precedence request"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.coop-sync.pri"                         : "rubicon",
                 "adapters.rubicon.enabled"                          : "true",
                 "adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

        and: "Default cookie sync request with coop-sync and limit"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            limit = 1
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain precedence generic bidder"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type

    }

    def "PBS cookie sync with cookie-sync.max-limit config should sync bidder by limit value"() {
        given: "PBS config with bidders usersync config"
        def prebidServerService = pbsServiceFactory.getService(
                ["cookie-sync.max-limit"                             : "1",
                 "adapters.rubicon.enabled"                          : "true",
                 "adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

        and: "Default cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders.add(RUBICON)
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
        "is invalid bidder name, ignoring"           | ["cookie-sync.coop-sync.pri": "unknown"]
        "disabled in current pbs instance, ignoring" | ["adapters.generic.enabled" : "false",
                                                        "cookie-sync.coop-sync.pri": "generic"]
        "has no user-sync configuration, ignoring"   | ["adapters.generic.usersync.cookie-family-name": "null",
                                                        "cookie-sync.coop-sync.pri"                   : "generic",]
    }

    def "PBS cookie sync with unknown bidder should be mention in response error"() {
        given: "Default cookie sync request with one more bidder"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [BOGUS]
        }

        when: "PBS processes cookie sync request"
        def response = defaultPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(BOGUS)
        assert bidderStatus?.error == "Unsupported bidder"
    }

    def "PBS cookie sync with bidder which doesn't have usersync config should be mention in response error"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.cookie-family-name": "null"])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "No sync config"
    }

    def "PBS cookie_sync with bidder which disabled in config should be mention in response error"() {
        given: "PBS config with disabled bidder"
        def prebidServerService = pbsServiceFactory.getService(["adapters.generic.enabled": "false"])

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.error == "Disabled bidder"
    }

    def "PBS cookie sync with family which already in uids cookie should be already in sync"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == CookieSyncResponse.Status.OK

        and: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Already in sync"
    }

    def "PBS cookie sync with alias config should be sync as the source bidder when aliases doesn't override cookie-family-name"() {
        given: "PBS config with alias bidder"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.aliases.alias.enabled"            : "true",
                 "adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

        and: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders.add(ALIAS)
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def bidderStatus = response.getBidderUserSync(ALIAS)
        assert bidderStatus.error == "synced as generic"
    }

    def "PBS cookie sync with filter setting should reject bidder sync"() {
        given: "Cookie sync request with filter settings"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            filterSettings = FilterSettings.defaultFilterSetting
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
        given: "PBS bidder usersync config"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.ccpa-enforced"                    : "true",
                 "adapters.generic.usersync.redirect.support-cors"   : "false",
                 "adapters.generic.usersync.redirect.format-override": "blank"])

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
