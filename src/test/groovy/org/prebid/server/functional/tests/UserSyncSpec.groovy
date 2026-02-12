package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class UserSyncSpec extends BaseSpec {

    private static final Map<String, String> BASE_USERSYNC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${IFRAME.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
            "adapters.${GENERIC.value}.usersync.${IFRAME.value}.support-cors": "false"
    ]

    private static final Map<String, String> USERSYNC_DISABLED_CONFIG = BASE_USERSYNC_CONFIG + [
            "adapters.${GENERIC.value}.usersync.enabled": "false"
    ]

    private static final Map<String, String> USERSYNC_ENABLED_CONFIG = BASE_USERSYNC_CONFIG + [
            "adapters.${GENERIC.value}.usersync.enabled": "true"
    ]

    private static final PrebidServerService userSyncDisabledService = pbsServiceFactory.getService(USERSYNC_DISABLED_CONFIG)
    private static final PrebidServerService userSyncEnabledService = pbsServiceFactory.getService(USERSYNC_ENABLED_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(USERSYNC_DISABLED_CONFIG)
        pbsServiceFactory.removeContainer(USERSYNC_ENABLED_CONFIG)
    }

    def "PBS should return usersync url with '#formatParam' format parameter for #userSyncFormat when format-override absent"() {
        given: "Pbs config with usersync.#userSyncFormat"
        def pbsConfig = ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                         "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain '#formatParam' format parameter"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.type == userSyncFormat
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "f") == formatParam

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat | formatParam
        REDIRECT       | PIXEL.name
        IFRAME         | BLANK.name
    }

    def "PBS should return overridden usersync url format for #userSyncFormat usersync when format-override is #formatOverride"() {
        given: "Pbs config with usersync.#userSyncFormat and iframe.format-override: #formatOverride"
        def pbsConfig = ["adapters.generic.usersync.${userSyncFormat.value}.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                         "adapters.generic.usersync.${userSyncFormat.value}.support-cors"   : "false",
                         "adapters.generic.usersync.${userSyncFormat.value}.format-override": formatOverride.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response usersync url should contain #formatOverride format"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.type == userSyncFormat
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "f") == formatOverride.name

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat | formatOverride
        REDIRECT       | BLANK
        REDIRECT       | PIXEL
        IFRAME         | BLANK
        IFRAME         | PIXEL
    }

    def "PBS should return empty uid in usersync url when uid macro is not present in config"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def pbsConfig = ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                         "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false",
                         "adapters.generic.usersync.${userSyncFormat.value}.uid-macro"   : null]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain empty uid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "uid").isEmpty()

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        userSyncFormat << [REDIRECT, IFRAME]
    }

    def "PBS cookie sync should sync bidder by default when bidder.usersync.enabled not overridden"() {
        given: "PBS bidder config"
        def prebidServerService = pbsServiceFactory.getService(BASE_USERSYNC_CONFIG)

        and: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.bidderStatus.size() == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(BASE_USERSYNC_CONFIG)
    }

    def "PBS cookie sync should sync bidder when bidder.usersync.enabled=true"() {
        given: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = userSyncEnabledService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.bidderStatus.size() == 1
    }

    def "PBS cookie sync shouldn't sync bidder and emit error when bidder.usersync.enabled=false"() {
        given: "Default Cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request without cookies"
        def response = userSyncDisabledService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidder with error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Sync disabled by config"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync shouldn't coop-sync bidder when coop-sync=true and bidder.usersync.enabled=false "() {
        given: "Cookie sync request without bidders and coop-sync=true"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = userSyncDisabledService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidder with error"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus.error == "Sync disabled by config"
        assert bidderStatus?.userSync == null
        assert bidderStatus?.noCookie == null
    }

    def "PBS cookie sync should coop-sync bidder when coop-sync=true and bidder.usersync.enabled=true "() {
        given: "Cookie sync request without bidders and coop-sync=true"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = null
            coopSync = true
        }

        when: "PBS processes cookie sync request without cookies"
        def response = userSyncEnabledService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.bidderStatus.size() == 1
    }
}
