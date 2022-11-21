package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.util.HttpUtil

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class UserSyncSpec extends BaseSpec {

    def "PBS should return usersync url with '#formatParam' format parameter for #userSyncFormat when format-override absent"() {
        given: "Pbs config with usersync.#userSyncFormat"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false"])

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain '#formatParam' format parameter"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.type == userSyncFormat
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "f") == formatParam

        where:
        userSyncFormat || formatParam
        REDIRECT       || PIXEL.name
        IFRAME         || BLANK.name
    }

    def "PBS should return overridden usersync url format for #userSyncFormat usersync when format-override is #formatOverride"() {
        given: "Pbs config with usersync.#userSyncFormat and iframe.format-override: #formatOverride"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${userSyncFormat.value}.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${userSyncFormat.value}.support-cors"   : "false",
                 "adapters.generic.usersync.${userSyncFormat.value}.format-override": formatOverride.value])

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response usersync url should contain #formatOverride format"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert bidderStatus?.userSync?.type == userSyncFormat
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "f") == formatOverride.name

        where:
        userSyncFormat || formatOverride
        REDIRECT       || BLANK
        REDIRECT       || PIXEL
        IFRAME         || BLANK
        IFRAME         || PIXEL
    }

    def "PBS should return empty uid in usersync url when uid macro is not present in config"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false",
                 "adapters.generic.usersync.${userSyncFormat.value}.uid-macro"   : null])

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain empty uid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "uid").isEmpty()

        where:
        userSyncFormat << [REDIRECT, IFRAME]
    }
}
