package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.util.ResourceUtil
import spock.lang.Shared

import java.time.Clock
import java.time.ZonedDateTime

import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.request.setuid.UidWithExpiry.defaultUidWithExpiry
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class SetUidSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> PBS_CONFIG =
            ["host-cookie.max-cookie-size-bytes"                                      : "500",
             "adapters.${RUBICON.value}.enabled"                                      : "true",
             "adapters.${RUBICON.value}.usersync.cookie-family-name"                  : RUBICON.value,
             "adapters.${APPNEXUS.value}.enabled"                                     : "true",
             "adapters.${APPNEXUS.value}.usersync.cookie-family-name"                 : APPNEXUS.value,
             "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
             "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

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

    def "PBS setuid should remove expired uids cookie"() {
        given: "Default SetuidRequest"
        def request = SetuidRequest.defaultSetuidRequest
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            def uidWithExpiry = defaultUidWithExpiry.tap {
                expires = ZonedDateTime.now(Clock.systemUTC()).minusDays(2)
            }
            tempUIDs = [(GENERIC): uidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response shouldn't contain uids cookie"
        assert response.uidsCookie.tempUIDs.size() == 0
    }

    def "PBS setuid should populate uids cookie with non priority bidder"() {
        given: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): defaultUidWithExpiry,
                        (RUBICON) : defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain none priority uids"
        assert response.uidsCookie.tempUIDs.size() == 2
    }

    def "PBS setuid should return prioritized uids bidder when size is full"() {
        given: "PBS config"
        def bidderGeneric = GENERIC
        PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["host-cookie.max-cookie-size-bytes": "500",
                 "cookie-sync.pri"                  : bidderGeneric.value])

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.bidder = bidderGeneric
            uid = UUID.randomUUID().toString()
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): defaultUidWithExpiry,
                        (RUBICON) : defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[GENERIC]
    }

    def "PBS setuid should remove earliest expiration bidder when size is full"() {
        given: "PBS config"
        PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["host-cookie.max-cookie-size-bytes": "500",
                 "cookie-sync.pri"                  : GENERIC.value])

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            def uidWithExpiry = defaultUidWithExpiry.tap {
                expires = ZonedDateTime.now(Clock.systemUTC()).plusDays(2)
            }
            tempUIDs = [(APPNEXUS): defaultUidWithExpiry,
                        (RUBICON) : uidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[GENERIC]
    }
}
