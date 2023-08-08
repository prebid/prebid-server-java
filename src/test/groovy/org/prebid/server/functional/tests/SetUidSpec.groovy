package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.setuid.UidWithExpiry
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.util.ResourceUtil
import spock.lang.Shared

import java.time.Clock
import java.time.ZonedDateTime

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.setuid.UidWithExpiry.defaultUidWithExpiry
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.RUBICON_VENDOR_ID

class SetUidSpec extends BaseSpec {

    private static final Integer MAX_COOKIE_SIZE = 500
    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> PBS_CONFIG =
            ["host-cookie.max-cookie-size-bytes"                                      : MAX_COOKIE_SIZE as String,
             "adapters.${RUBICON.value}.enabled"                                      : "true",
             "adapters.${RUBICON.value}.usersync.cookie-family-name"                  : RUBICON.value,
             "adapters.${OPENX.value}.enabled"                                        : "true",
             "adapters.${OPENX.value}.usersync.cookie-family-name"                    : OPENX.value,
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
            tempUIDs = [(RUBICON): uidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response shouldn't contain uids cookie"
        assert !response.uidsCookie.tempUIDs[RUBICON]
    }

    def "PBS setuid should return requested uids cookie when priority bidder not present in config"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["cookie-sync.pri": null])

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(RUBICON): defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain requested uids"
        assert response.uidsCookie.tempUIDs[GENERIC]
        assert response.uidsCookie.tempUIDs[RUBICON]
    }

    def "PBS setuid should return prioritized uids bidder when size is full"() {
        given: "PBS config"
        def genericBidder = GENERIC
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["cookie-sync.pri": genericBidder.value])

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.bidder = genericBidder
            uid = UUID.randomUUID().toString()
        }
        def rubiconBidder = RUBICON
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS)     : defaultUidWithExpiry,
                        (rubiconBidder): defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[rubiconBidder]
        assert response.uidsCookie.tempUIDs[genericBidder]
    }

    def "PBS setuid should remove earliest expiration bidder when size is full"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["cookie-sync.pri": GENERIC.value])

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            def appnexusUidWithExpiry = defaultUidWithExpiry.tap {
                expires = ZonedDateTime.now(Clock.systemUTC()).plusDays(5)
            }
            def rubiconUidWithExpiry = defaultUidWithExpiry.tap {
                expires = ZonedDateTime.now(Clock.systemUTC()).plusDays(1)
            }
            tempUIDs = [(APPNEXUS): appnexusUidWithExpiry,
                        (RUBICON) : rubiconUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[GENERIC]
    }

    def "PBS setuid should ignore requested bidder and log metric when cookie's filled and requested bidder not in prioritize list"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["cookie-sync.pri": APPNEXUS.value])

        and: "Setuid request"
        def bidderName = GENERIC
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.bidder = bidderName
            uid = UUID.randomUUID().toString()
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): defaultUidWithExpiry,
                        (RUBICON) : defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "usersync.FAMILY.sizeblocked metric should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics["usersync.${bidderName.value}.sizeblocked"] == 1

        and: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[RUBICON]
    }

    def "PBS setuid should reject bidder when cookie's filled and requested bidder in pri and rejected by tcf"() {
        given: "Setuid request"
        def bidderName = RUBICON
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["gdpr.host-vendor-id": RUBICON_VENDOR_ID.toString(),
                   "cookie-sync.pri"    : bidderName.value])

        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.bidder = bidderName
            gdpr = "1"
            gdprConsent = new TcfConsent.Builder().build()
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): defaultUidWithExpiry,
                        (RUBICON) : defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 451
        assert exception.responseBody == "The gdpr_consent param prevents cookies from being saved"

        and: "usersync.FAMILY.tcf.blocked metric should be updated"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["usersync.${bidderName.value}.tcf.blocked"] == 1
    }

    def "PBS setuid should remove oldest uid and log metric when cookie's filled and oldest uid's not on the pri"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["cookie-sync.pri": GENERIC.value])

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }

        def bidderName = RUBICON
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            def uidWithExpiry = defaultUidWithExpiry.tap {
                expires.plusDays(10)
            }
            tempUIDs = [(APPNEXUS)  : defaultUidWithExpiry,
                        (bidderName): uidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        and: "usersync.FAMILY.sizedout metric should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics["usersync.${bidderName.value}.sizedout"] == 1

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[GENERIC]
    }

    def "PBS SetUid should remove oldest bidder from uids cookie in favor of prioritized bidder"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG +
                ["cookie-sync.pri": "$OPENX.value, $GENERIC.value" as String])

        and: "Set uid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.uid = UUID.randomUUID().toString()
            it.bidder = OPENX
        }

        and: "Set up set uid cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(APPNEXUS): defaultUidWithExpiry,
                           (RUBICON) : defaultUidWithExpiry]
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        when: "PBS processes set uid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain pri bidder in uids cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        and: "Response set cookie header size should be lowest or the same as max cookie config size"
        assert response.headers.get("Set-Cookie").value.split("Secure;")[0].length() <= MAX_COOKIE_SIZE

        and: "Request bidder should contain uid from Set uid request"
        assert response.uidsCookie.tempUIDs[OPENX].uid == request.uid

        and: "usersync.FAMILY.sizedout metric should be updated"
        def metricsRequest = prebidServerService.sendCollectedMetricsRequest()
        assert metricsRequest["usersync.${APPNEXUS.value}.sizedout"] == 1

        and: "usersync.FAMILY.sets metric should be updated"
        assert metricsRequest["usersync.${OPENX.value}.sets"] == 1
    }
}
