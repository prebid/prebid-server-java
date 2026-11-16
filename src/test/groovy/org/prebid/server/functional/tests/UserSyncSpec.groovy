package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.testcontainers.container.PbsContainerConfig
import org.prebid.server.functional.util.HttpUtil

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class UserSyncSpec extends BaseSpec {

    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync&redir={redirect_url}"

    private static final PbsContainerConfig GENERIC_USERSYNC_CONFIG = new PbsContainerConfig(["adapters.${GENERIC.value}.usersync.${IFRAME.value}.url": USER_SYNC_URL])

    private static final PbsContainerConfig ENABLED_USERSYNC_CONFIG = GENERIC_USERSYNC_CONFIG + ["adapters.${GENERIC.value}.usersync.enabled": "true"]

    private static final PbsContainerConfig DISABLED_USERSYNC_CONFIG = GENERIC_USERSYNC_CONFIG + ["adapters.${GENERIC.value}.usersync.enabled": "false"]

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(GENERIC_USERSYNC_CONFIG)
        pbsServiceFactory.removeContainer(ENABLED_USERSYNC_CONFIG)
        pbsServiceFactory.removeContainer(DISABLED_USERSYNC_CONFIG)
    }

    def "PBS should return usersync url with '#formatParam' format parameter for #userSyncFormat when format-override absent"() {
        given: "PBS config with usersync.#userSyncFormat"
        def pbsConfig = new PbsContainerConfig([
                "adapters.${GENERIC.value}.usersync.${userSyncFormat.value}.url": USER_SYNC_URL
        ])
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain '#formatParam' format parameter"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.type == userSyncFormat
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "f") == formatParam

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat || formatParam
        REDIRECT       || PIXEL.name
        IFRAME         || BLANK.name
    }

    def "PBS should return overridden usersync url format for #userSyncFormat usersync when format-override is #formatOverride"() {
        given: "PBS config with usersync.#userSyncFormat and iframe.format-override: #formatOverride"
        def pbsConfig = new PbsContainerConfig([
                "adapters.${GENERIC.value}.usersync.${userSyncFormat.value}.url"            : USER_SYNC_URL,
                "adapters.${GENERIC.value}.usersync.${userSyncFormat.value}.format-override": formatOverride.value
        ])
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response usersync url should contain #formatOverride format"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.type == userSyncFormat
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "f") == formatOverride.name

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat || formatOverride
        REDIRECT       || BLANK
        REDIRECT       || PIXEL
        IFRAME         || BLANK
        IFRAME         || PIXEL
    }

    def "PBS should return empty uid in usersync url when uid macro is not present in config"() {
        given: "PBS config with usersync.#userSyncFormat.url"
        def pbsConfig = new PbsContainerConfig(
                ["adapters.${GENERIC.value}.usersync.${userSyncFormat.value}.url"      : USER_SYNC_URL,
                 "adapters.${GENERIC.value}.usersync.${userSyncFormat.value}.uid-macro": null])
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain empty uid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "uid").isEmpty()

        cleanup:
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat << [REDIRECT, IFRAME]
    }

    def "PBS cookie sync should sync bidder by default when bidder.usersync.enabled not overridden"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG)

        and: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.bidderStatus.size() == 1
    }

    def "PBS cookie sync should sync bidder when bidder.usersync.enabled=true"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(ENABLED_USERSYNC_CONFIG)

        and: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.bidderStatus.size() == 1
    }

    def "PBS cookie sync shouldn't sync bidder and emit error when bidder.usersync.enabled=false"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(DISABLED_USERSYNC_CONFIG)

        and: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidder with error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Sync disabled by config"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync shouldn't coop-sync bidder when coop-sync=true and bidder.usersync.enabled=false "() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(DISABLED_USERSYNC_CONFIG)

        and: "Cookie sync request without bidders and coop-sync=true"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidder with error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Sync disabled by config"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync should coop-sync bidder when coop-sync=true and bidder.usersync.enabled=true "() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(ENABLED_USERSYNC_CONFIG)

        and: "Cookie sync request without bidders and coop-sync=true"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.bidderStatus.size() == 1
    }
}
