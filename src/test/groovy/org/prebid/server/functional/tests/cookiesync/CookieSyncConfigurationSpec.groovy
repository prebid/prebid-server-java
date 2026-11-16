package org.prebid.server.functional.tests.cookiesync

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.PrivacySandbox
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.testcontainers.container.PbsContainerConfig
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.MAGNITE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.NO_COOKIE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.OK
import static org.prebid.server.functional.util.HttpUtil.SET_COOKIE_HEADER
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

class CookieSyncConfigurationSpec extends CookieSyncBaseSpec {

    private static final PbsContainerConfig NO_COOKIE_FAMILY_CONFIG = new PbsContainerConfig(["adapters.${GENERIC.value}.usersync.cookie-family-name": null])

    private static final PbsContainerConfig CUSTOM_COOKIE_FAMILY_CONFIG = PBS_CONFIG + ["adapters.${GENERIC.value}.usersync.cookie-family-name": BOGUS.value]

    private static final PbsContainerConfig HOST_COOKIE_CONFIG = PBS_CONFIG +
            ['host-cookie.family'     : GENERIC.value,
             'host-cookie.cookie-name': RANDOM_COOKIE_NAME]

    def "PBS cookie sync request should replace disabled bidder and fill up response with enabled bidders to the limit in request"() {
        given: "PBS bidder config"
        def pbsConfig = new PbsContainerConfig(MAGNITE_CONFIG + APPNEXUS_CONFIG + ["adapters.${GENERIC.value}.enabled": "false"])
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

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder without sync config"() {
        given: "PBS bidder config without cookie family name"
        def prebidServerService = pbsServiceFactory.getService(NO_COOKIE_FAMILY_CONFIG)

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

    def "PBS cookie sync should preserve requested bidder when cookie family name is configured"() {
        given: "PBS bidder config with configured cookie family name"
        def prebidServerService = pbsServiceFactory.getService(CUSTOM_COOKIE_FAMILY_CONFIG)

        and: "Cookie sync request for generic bidder"
        def cookieSyncRequest = CookieSyncRequest.getDefaultCookieSyncRequest([GENERIC])

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain requested bidder"
        assert response.bidderStatus.size() == 1
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
        assert bidderStatus?.userSync?.type == USER_SYNC_TYPE
        assert bidderStatus?.noCookie == true
    }

    def "PBS cookie sync should be able to read custom cookie family name from uids cookie"() {
        given: "PBS bidder config with defined cookie family name"
        def bidder = BOGUS
        def prebidServerService = pbsServiceFactory.getService(CUSTOM_COOKIE_FAMILY_CONFIG)

        and: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest
        def uidsCookie = UidsCookie.getDefaultUidsCookie(bidder)

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response should contain information about bidder status"
        assert response.bidderStatus.size() == 1
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Already in sync"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync request with bidder without cookie family name should emit an error"() {
        given: "PBS bidder config without cookie family name"
        def prebidServerService = pbsServiceFactory.getService(NO_COOKIE_FAMILY_CONFIG)

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

    def "PBS cookie sync request with alias bidder should sync independently when alias provide cookie-family-name"() {
        given: "PBS config with alias bidder with cookie family name"
        def bidderAlias = ALIAS
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${GENERIC.value}.aliases.${bidderAlias.value}.enabled"                    : "true",
                   "adapters.${GENERIC.value}.aliases.${bidderAlias.value}.meta-info.vendor-id"        : "0",
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
            assert it.noCookie == true
        }
    }

    def "PBS cookie sync request with host cookie should return bidder sync with host cookie uid when there is no uids cookie"() {
        given: "PBS bidders config"
        def prebidServerService = pbsServiceFactory.getService(HOST_COOKIE_CONFIG)

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
        def prebidServerService = pbsServiceFactory.getService(HOST_COOKIE_CONFIG)

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
        def prebidServerService = pbsServiceFactory.getService(HOST_COOKIE_CONFIG)

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
        def prebidServerService = pbsServiceFactory.getService(
                HOST_COOKIE_CONFIG + ['host-cookie.cookie-name': null])

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

    def "PBS cookie sync should sync bidder by limit value"() {
        given: "PBS config with bidders usersync config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

        and: "Default cookie sync request with 2 bidders and limit of 1"
        def limit = 1
        def bidders = [GENERIC, MAGNITE]
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.limit = limit
            it.bidders = bidders
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one valid user sync"
        def validBidderUserSyncs = getValidBidderUserSyncs(response)
        assert validBidderUserSyncs.values().url.size() == limit
        assert validBidderUserSyncs.values().type.size() == limit

        and: "Discarded bidder user sync should contain an error"
        def rejectedBidderUserSyncs = getRejectedBidderUserSyncs(response)
        assert rejectedBidderUserSyncs.size() == bidders.size() - limit
        assert rejectedBidderUserSyncs.every { it.value == "Limit reached" }
    }

    def "PBS cookie sync with ccpa should reject bidder sync"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(
                new PbsContainerConfig(GENERIC_CONFIG + ["adapters.${GENERIC.value}.ccpa-enforced": 'true']))

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
}
