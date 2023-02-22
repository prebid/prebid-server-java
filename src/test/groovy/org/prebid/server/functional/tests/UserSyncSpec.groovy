package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class UserSyncSpec extends BaseSpec {

    private static final Map<String, String> GENERIC_USERSYNC_CONFIG = ["adapters.${GENERIC.value}.usersync.${IFRAME.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                                                                        "adapters.${GENERIC.value}.usersync.${IFRAME.value}.support-cors": "false"]

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

    def "PBS should return empty gpp and gppSid in usersync url when gpp and gppSid is not present in request"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false"])

        and: "Default CookieSyncRequest without gpp and gppSid"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            gpp = null
            gppSid = null
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url shouldn't contain gpp and gpp_sid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp").isEmpty()
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp_sid").isEmpty()

        where:
        userSyncFormat << [REDIRECT, IFRAME]
    }

    def "PBS should populate gpp and gppSid in usersync url when gpp and gppSid is present in request"() {
        given: "Pbs config with usersync.#userSyncFormat.url"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.generic.usersync.${userSyncFormat.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
                 "adapters.generic.usersync.${userSyncFormat.value}.support-cors": "false"])

        and: "Default CookieSyncRequest with gpp and gppSid"
        def gpp = PBSUtils.randomString
        def gppSid = "${PBSUtils.randomNumber},${PBSUtils.randomNumber}"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gpp = gpp
            it.gppSid = gppSid
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response userSync url should contain gpp and gppSid"
        def bidderStatus = response.getBidderUserSync(GENERIC)
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp") == gpp
        assert HttpUtil.findUrlParameterValue(bidderStatus.userSync?.url, "gpp_sid") == gppSid

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
        def prebidServerService = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG
                + ["adapters.${GENERIC.value}.usersync.enabled": "true"])

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
        def prebidServerService = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG
                + ["adapters.${GENERIC.value}.usersync.enabled": "false"])

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
        def prebidServerService = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG
                + ["adapters.${GENERIC.value}.usersync.enabled": "false"])

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
        def prebidServerService = pbsServiceFactory.getService(GENERIC_USERSYNC_CONFIG
                + ["adapters.${GENERIC.value}.usersync.enabled": "true"])

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
