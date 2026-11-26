package org.prebid.server.functional.tests.cookiesync

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PbsContainerConfig
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class CookieSyncPriSpec extends CookieSyncBaseSpec {

    private static final boolean CORS_SUPPORT = false

    private static final PbsContainerConfig GENERIC_USERSYNC_CONFIG = new PbsContainerConfig(GENERIC_CONFIG)

    private static final PbsContainerConfig COOP_SYNC_BASE_CONFIG = GENERIC_USERSYNC_CONFIG +
            ["adapters.${GENERIC.value}.usersync.redirect.url"         : USER_SYNC_URL,
             "adapters.${GENERIC.value}.usersync.redirect.support-cors": CORS_SUPPORT.toString(),
             'cookie-sync.pri'                                         : GENERIC.value]

    private static final PbsContainerConfig COOP_SYNC_ENABLED_CONFIG = COOP_SYNC_BASE_CONFIG + ['cookie-sync.coop-sync.default': 'true']
    private static final PbsContainerConfig COOP_SYNC_DISABLED_CONFIG = COOP_SYNC_BASE_CONFIG + ['cookie-sync.coop-sync.default': 'false']

    private static PrebidServerService pbsWithCoopSyncDefault
    private static PrebidServerService pbsWithoutCoopSyncDefault

    def setupSpec() {
        pbsWithCoopSyncDefault = pbsServiceFactory.getService(COOP_SYNC_ENABLED_CONFIG)
        pbsWithoutCoopSyncDefault = pbsServiceFactory.getService(COOP_SYNC_DISABLED_CONFIG)
    }

    def "PBS cookie sync with cookie-sync.pri and enabled coop-sync in config should sync bidder which present in cookie-sync.pri config"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithCoopSyncDefault.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from cookie-sync.pri config"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with cookie-sync.pri and disabled coop-sync in config shouldn't sync bidder which present in cookie-sync.pri config"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutCoopSyncDefault.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder from cookie-sync.pri config"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS should prioritise disabled account cookie sync config over #configType host config and shouldn't sync bidder"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false))
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder which present in cookie-sync.pri config"
        assert !response.getBidderUserSync(GENERIC)

        where:
        configType           | pbsService
        'enabled coop-sync'  | pbsWithCoopSyncDefault
        'disabled coop-sync' | pbsWithoutCoopSyncDefault
    }

    def "PBS should prioritise enabled account cookie sync config over #configType host config and should sync bidder"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: true))
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from cookie-sync.pri config"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type

        where:
        configType           | pbsService
        'enabled coop-sync'  | pbsWithCoopSyncDefault
        'disabled coop-sync' | pbsWithoutCoopSyncDefault
    }

    def "PBS cookie sync with enabled coop-sync should log error when prioritized bidder name is invalid"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "PBS config with invalid prioritized bidder"
        def pbsConfig = new PbsContainerConfig([
                "cookie-sync.pri": PBSUtils.randomString
        ])
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Cookie sync request with coop-sync enabled"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS log should contain invalid bidder message"
        def logs = prebidServerService.getLogsByTime(startTime)
        assert getLogsByText(logs, "is invalid bidder name, ignoring").size() == 1

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS cookie sync with enabled coop-sync should log error when prioritized bidder is disabled"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "PBS config with disabled prioritized bidder"
        def pbsConfig = new PbsContainerConfig([
                "adapters.generic.enabled": "false",
                "cookie-sync.pri"         : GENERIC.value
        ])
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Cookie sync request with coop-sync enabled"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS log should contain disabled bidder message"
        def logs = prebidServerService.getLogsByTime(startTime)
        assert getLogsByText(logs, "disabled in current pbs instance, ignoring").size() == 1

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS cookie sync with enabled coop-sync should log error when prioritized bidder has no user sync configuration"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "PBS config with prioritized bidder without user sync configuration"
        def pbsConfig = new PbsContainerConfig([
                "adapters.generic.usersync.cookie-family-name": null,
                "cookie-sync.pri"                             : GENERIC.value
        ])
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Cookie sync request with coop-sync enabled"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "PBS log should contain missing user sync configuration message"
        def logs = prebidServerService.getLogsByTime(startTime)
        assert getLogsByText(logs, "has no user-sync configuration, ignoring").size() == 1

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS cookie sync with cookie-sync.pri and enabled coop sync should sync bidder which present in cookie-sync.pri"() {
        given: "Default cookie sync request with coop-sync"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutCoopSyncDefault.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with cookie-sync.pri and disabled coop-sync in request shouldn't sync bidder which present in cookie-sync.pri config"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = false
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutCoopSyncDefault.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain generic bidder"
        assert !response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync with cookie-sync.pri and enabled coop sync in account should sync bidder which present in cookie-sync.pri config"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = null
            it.coopSync = null
            it.account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: true))
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsWithoutCoopSyncDefault.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from cookie-sync.pri config"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with coop-sync.default config and pri in cookie sync account should sync bidder which present in pri account config"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def accountId = PBSUtils.randomNumber
        def pbsWithGenericCors = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG)
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = null
            account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(pri: [GENERIC.value])
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsWithGenericCors.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from pri account config"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }

    def "PBS cookie sync with pri and enabled coop sync in cookie sync account should sync bidder which present in pri account config"() {
        given: "Default cookie sync request without coop-sync and bidders"
        def pbsWithGenericCors = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG)
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = null
            it.coopSync = null
            it.account = accountId
        }

        and: "Save account with cookie config"
        def cookieSyncConfig = new AccountCookieSyncConfig(pri: [GENERIC.value], coopSync: new AccountCoopSyncConfig(enabled: true))
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsWithGenericCors.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain generic bidder from pri account config"
        def genericBidder = response.getBidderUserSync(GENERIC)
        assert genericBidder?.userSync?.url
        assert genericBidder?.userSync?.type
    }
}
