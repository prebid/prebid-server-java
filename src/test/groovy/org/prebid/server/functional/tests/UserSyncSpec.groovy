package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.util.HttpUtil

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.cookiesync.UsersyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UsersyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UsersyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UsersyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class UserSyncSpec extends BaseSpec {

    def "PBS should return usersync url with '#usersyncFormat' for #usersyncMethod when format-override absent"() {
        given: "Pbs config with usersync.#usersyncMethod"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${usersyncMethod.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${usersyncMethod.value}.support-cors": "false"])

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain format blank"
        def bidderStatus = response.getBidderUsersync(GENERIC)
        assert bidderStatus?.usersync?.type == usersyncMethod
        assert HttpUtil.decodeUrl(bidderStatus.usersync?.url).contains("f=$usersyncFormat")

        where:
        usersyncMethod || usersyncFormat
        REDIRECT       || PIXEL.name
        IFRAME         || BLANK.name
    }

    def "PBS should return overridden usersync url format for #usersyncMethod usersync when format-override is #formatOverride"() {
        given: "Pbs config with usersync.#usersyncMethod and iframe.format-override: #formatOverride"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${usersyncMethod.value}.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${usersyncMethod.value}.support-cors"   : "false",
                 "adapters.generic.usersync.${usersyncMethod.value}.format-override": formatOverride.value])

        and: "Default CookieSyncRequest"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response usersync url should contain #formatOverride format"
        def bidderStatus = response.getBidderUsersync(GENERIC)
        assert bidderStatus?.usersync?.type == usersyncMethod
        assert HttpUtil.decodeUrl(bidderStatus.usersync?.url).contains("f=$formatOverride.name")

        where:
        usersyncMethod || formatOverride
        REDIRECT       || BLANK
        REDIRECT       || PIXEL
        IFRAME         || BLANK
        IFRAME         || PIXEL
    }
}
