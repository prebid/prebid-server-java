package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.util.ResourceUtil
import spock.lang.Shared

import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class SetUidSpec extends BaseSpec {

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(
            ["adapters.generic.usersync.redirect.url"            : "$networkServiceContainer.rootUri/generic-usersync&redir={{redirect_url}}".toString(),
             "adapters.generic.usersync.redirect.support-cors"   : "false",
             "adapters.generic.usersync.redirect.format-override": "blank"])

    def "PBS should set uids cookie"() {
        given: "Default SetuidRequest"
        def request = SetuidRequest.defaultSetuidRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie.bday
        assert !response.uidsCookie.tempUIDs
        assert !response.uidsCookie.uids
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")
    }
}
