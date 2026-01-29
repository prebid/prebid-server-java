package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.model.response.setuid.SetuidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.util.ResourceUtil
import spock.lang.Shared

import java.time.Clock
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.GRID
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.model.request.setuid.UidWithExpiry.defaultUidWithExpiry
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.RUBICON_VENDOR_ID

class SetUidSpec extends BaseSpec {

    private static final Integer MAX_COOKIE_SIZE = 500
    private static final Integer MAX_NUMBER_OF_UID_COOKIES = 30
    private static final Integer UPDATED_EXPIRE_DAYS = 14
    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final Integer RANDOM_EXPIRE_DAY = PBSUtils.getRandomNumber(1, 10)
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final String GENERIC_COOKIE_FAMILY_NAME = GENERIC.value
    private static final String VENDOR_ID = PBSUtils.randomNumber as String
    private static final Map<String, String> UID_COOKIES_CONFIG = ['setuid.number-of-uid-cookies': MAX_NUMBER_OF_UID_COOKIES.toString()]
    private static final Map<String, String> GENERIC_ALIAS_CONFIG = ["adapters.generic.aliases.alias.enabled" : "true",
                                                                     "adapters.generic.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static final String TCF_ERROR_MESSAGE = "The gdpr_consent param prevents cookies from being saved"
    private static final int UNAVAILABLE_FOR_LEGAL_REASONS_CODE = 451
    private static final Map<String, String> PBS_CONFIG =
            ["host-cookie.max-cookie-size-bytes"                                                       : MAX_COOKIE_SIZE as String,

             "adapters.${RUBICON.value}.enabled"                                                       : "true",
             "adapters.${RUBICON.value}.usersync.cookie-family-name"                                   : RUBICON.value,

             "adapters.${OPENX.value}.enabled"                                                         : "true",
             "adapters.${OPENX.value}.usersync.cookie-family-name"                                     : OPENX.value,

             "adapters.${APPNEXUS.value}.enabled"                                                      : "true",
             "adapters.${APPNEXUS.value}.usersync.cookie-family-name"                                  : APPNEXUS.value,

             "adapters.${GENERIC.value}.meta-info.vendor-id"                                           : VENDOR_ID,
             "adapters.${GENERIC.value}.usersync.cookie-family-name"                                   : GENERIC_COOKIE_FAMILY_NAME,
             "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"                          : USER_SYNC_URL,
             "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors"                 : CORS_SUPPORT.toString(),

             "adapters.${GENERIC}.aliases.${ALIAS}.enabled"                                            : "true",
             "adapters.${GENERIC}.aliases.${ALIAS}.endpoint"                                           : "$networkServiceContainer.rootUri/auction".toString(),
             "adapters.${GENERIC}.aliases.${ALIAS}.meta-info.vendor-id"                                : VENDOR_ID,
             "adapters.${GENERIC}.aliases.${ALIAS}.usersync.cookie-family-name"                        : GENERIC_COOKIE_FAMILY_NAME,
             "adapters.${GENERIC}.aliases.${ALIAS}.usersync.${USER_SYNC_TYPE.value}.url"               : USER_SYNC_URL,
             "adapters.${GENERIC}.aliases.${ALIAS}.usersync.${USER_SYNC_TYPE.value}.support-cors"      : CORS_SUPPORT.toString(),

             "adapters.${GENERIC}.aliases.${OPENX_ALIAS}.enabled"                                      : "true",
             "adapters.${GENERIC}.aliases.${OPENX_ALIAS}.endpoint"                                     : "$networkServiceContainer.rootUri/auction".toString(),
             "adapters.${GENERIC}.aliases.${OPENX_ALIAS}.meta-info.vendor-id"                          : VENDOR_ID,
             "adapters.${GENERIC}.aliases.${OPENX_ALIAS}.usersync.cookie-family-name"                  : GENERIC_COOKIE_FAMILY_NAME,
             "adapters.${GENERIC}.aliases.${OPENX_ALIAS}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
             "adapters.${GENERIC}.aliases.${OPENX_ALIAS}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()

            ]

    @Shared
    PrebidServerService singleCookiesPbsService = pbsServiceFactory.getService(PBS_CONFIG + GENERIC_ALIAS_CONFIG)
    @Shared
    PrebidServerService multipleCookiesPbsService = pbsServiceFactory.getService(PBS_CONFIG + UID_COOKIES_CONFIG + GENERIC_ALIAS_CONFIG)

    def "PBS should set uids cookie"() {
        given: "Default SetuidRequest"
        def request = SetuidRequest.defaultSetuidRequest
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes setuid request"
        def response = singleCookiesPbsService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uid cookie"
        assert response.uidsCookie.tempUIDs[GENERIC].uid
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")
    }

    def "PBS should updated uids cookie when request parameters contain uid"() {
        given: "Default SetuidRequest"
        def requestUid = UUID.randomUUID().toString()
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = requestUid
        }
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Flush metrics"
        flushMetrics(singleCookiesPbsService)

        when: "PBS processes setuid request"
        def response = singleCookiesPbsService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookie"
        assert daysDifference(response.uidsCookie.tempUIDs[GENERIC].expires) == UPDATED_EXPIRE_DAYS
        assert response.uidsCookie.tempUIDs[GENERIC].uid == requestUid
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        and: "usersync.FAMILY.sets metric should be updated"
        def metrics = singleCookiesPbsService.sendCollectedMetricsRequest()
        assert metrics["usersync.${GENERIC.value}.sets"] == 1
    }

    def "PBS setuid should remove expired uids cookie"() {
        given: "Default SetuidRequest"
        def request = SetuidRequest.defaultSetuidRequest
        def uidsCookie = UidsCookie.getDefaultUidsCookie(RUBICON, -RANDOM_EXPIRE_DAY)

        when: "PBS processes setuid request"
        def response = singleCookiesPbsService.sendSetUidRequest(request, uidsCookie)

        then: "Response shouldn't contain uids cookie"
        assert !response.uidsCookie.tempUIDs
    }

    def "PBS setuid should return requested uids cookie when priority bidder not present in config"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": null]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(RUBICON): defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain requested tempUIDs"
        assert response.uidsCookie.tempUIDs[GENERIC]
        assert response.uidsCookie.tempUIDs[RUBICON]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid should return prioritized uids bidder when size is full"() {
        given: "PBS config"
        def genericBidder = GENERIC
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": genericBidder.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.bidder = genericBidder
            uid = UUID.randomUUID().toString()
        }
        def rubiconBidder = RUBICON
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS)     : getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY + 1),
                        (rubiconBidder): getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY)]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[rubiconBidder]
        assert response.uidsCookie.tempUIDs[genericBidder]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid should remove most distant expiration bidder when size is full"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": GENERIC.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        assert response.uidsCookie.tempUIDs[RUBICON]
        assert response.uidsCookie.tempUIDs[GENERIC]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid should ignore requested bidder and log metric when cookie's filled and requested bidder not in prioritize list"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": APPNEXUS.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid should reject bidder when cookie's filled and requested bidder in pri and rejected by tcf"() {
        given: "Setuid request"
        def pbsConfig = PBS_CONFIG + ["gdpr.host-vendor-id": RUBICON_VENDOR_ID.toString(),
                                      "cookie-sync.pri"    : RUBICON.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.bidder = RUBICON
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
        assert exception.statusCode == UNAVAILABLE_FOR_LEGAL_REASONS_CODE
        assert exception.responseBody == TCF_ERROR_MESSAGE

        and: "usersync.FAMILY.tcf.blocked metric should be updated"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["usersync.${RUBICON.value}.tcf.blocked"] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid should remove most distant expiration uid and log metric when cookie's filled and this uid's not on the pri"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": GENERIC.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY),
                        (RUBICON) : getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY + 1)]
        }

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        and: "usersync.FAMILY.sizeblocked metric should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics["usersync.${RUBICON.value}.sizeblocked"] == 1

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[GENERIC]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS set uid should emit sizeblocked metric and remove most distant expiration bidder from uids cookie for non-prioritized bidder"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": "$OPENX.value, $GENERIC.value" as String]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Set uid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.uid = UUID.randomUUID().toString()
            it.bidder = OPENX
        }

        and: "Set up set uid cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY + 1),
                        (RUBICON) : getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY)]
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        when: "PBS processes set uid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain pri bidder in uids cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        and: "Response set cookie header size should be lowest or the same as max cookie config size"
        assert getSetUidsHeaders(response).first.split("Secure;")[0].length() <= MAX_COOKIE_SIZE

        and: "Request bidder should contain uid from Set uid request"
        assert response.uidsCookie.tempUIDs[OPENX].uid == request.uid

        and: "usersync.FAMILY.sizeblocked metric should be updated"
        def metricsRequest = prebidServerService.sendCollectedMetricsRequest()
        assert metricsRequest["usersync.${APPNEXUS.value}.sizeblocked"] == 1

        and: "usersync.FAMILY.sets metric should be updated"
        assert metricsRequest["usersync.${OPENX.value}.sets"] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS set uid should emit sizedout metric and remove most distant expiration bidder from uids cookie in prioritized bidder"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG + ["cookie-sync.pri": "$OPENX.value, $APPNEXUS.value, $RUBICON.value" as String]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Set uid request"
        def request = SetuidRequest.defaultSetuidRequest

        and: "Set up set uid cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(APPNEXUS): getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY + 1),
                        (OPENX)   : getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY),
                        (RUBICON) : getDefaultUidWithExpiry(RANDOM_EXPIRE_DAY)]
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        when: "PBS processes set uid request"
        def response = prebidServerService.sendSetUidRequest(request, uidsCookie)

        then: "Response should contain pri bidder in uids cookies"
        assert response.uidsCookie.tempUIDs[OPENX]
        assert response.uidsCookie.tempUIDs[RUBICON]

        and: "Response set cookie header size should be lowest or the same as max cookie config size"
        assert getSetUidsHeaders(response).first.split("Secure;")[0].length() <= MAX_COOKIE_SIZE

        and: "usersync.FAMILY.sizedout metric should be updated"
        def metricsRequest = prebidServerService.sendCollectedMetricsRequest()
        assert metricsRequest["usersync.${APPNEXUS.value}.sizedout"] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS  setuid should reject request when requested bidder mismatching with cookie-family-name"() {
        given: "Default SetuidRequest"
        def request = SetuidRequest.getDefaultSetuidRequest().tap {
            it.bidder = bidderName
        }

        when: "PBS processes setuid request"
        singleCookiesPbsService.sendSetUidRequest(request, UidsCookie.defaultUidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: "bidder" query param is invalid'

        where:
        bidderName << [UNKNOWN, WILDCARD, GENERIC_CAMEL_CASE, ALIAS, ALIAS_CAMEL_CASE]
    }

    def "PBS should throw an exception when incoming request have optout flag"() {
        given: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest
        def genericUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC)

        and: "PBS service with optout cookies"
        def pbsConfig = PBS_CONFIG + ["host-cookie.optout-cookie.name" : "uids",
                                      "host-cookie.optout-cookie.value": Base64.urlEncoder.encodeToString(encode(genericUidsCookie).bytes)]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(request, [genericUidsCookie])

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 401
        assert exception.responseBody == 'Unauthorized: Sync is not allowed for this uids'
    }

    def "PBS should merge cookies when incoming request have multiple uids cookies"() {
        given: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def genericUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC)
        def rubiconUidsCookie = UidsCookie.getDefaultUidsCookie(RUBICON)

        when: "PBS processes setuid request"
        def response = multipleCookiesPbsService.sendSetUidRequest(request, [genericUidsCookie, rubiconUidsCookie])

        then: "Response should contain requested tempUIDs"
        assert response.uidsCookie.tempUIDs[GENERIC]
        assert response.uidsCookie.tempUIDs[RUBICON]

        and: "Headers uids cookies should contain same cookie as response"
        def setUidsHeaders = getSetUidsHeaders(response)
        def uidsCookie = extractHeaderTempUIDs(setUidsHeaders.first)
        assert setUidsHeaders.size() == 1
        assert uidsCookie.tempUIDs[GENERIC]
        assert uidsCookie.tempUIDs[RUBICON]
    }

    def "PBS should send multiple uids cookies by priority and expiration timestamp"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG +
                UID_COOKIES_CONFIG +
                ["cookie-sync.pri": "$OPENX.value, $GENERIC.value" as String] +
                ["host-cookie.max-cookie-size-bytes": MAX_COOKIE_SIZE as String]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)


        and: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest

        def genericUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC, RANDOM_EXPIRE_DAY + 1)
        def rubiconUidsCookie = UidsCookie.getDefaultUidsCookie(RUBICON, RANDOM_EXPIRE_DAY + 2)
        def openxUidsCookie = UidsCookie.getDefaultUidsCookie(OPENX, RANDOM_EXPIRE_DAY + 3)
        def appnexusUidsCookie = UidsCookie.getDefaultUidsCookie(APPNEXUS, RANDOM_EXPIRE_DAY)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(request, [appnexusUidsCookie, genericUidsCookie, rubiconUidsCookie, openxUidsCookie])

        then: "Response should contain requested tempUIDs"
        assert response.uidsCookie.tempUIDs.keySet() == new LinkedHashSet([GENERIC, OPENX, APPNEXUS, RUBICON])

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should remove duplicates when incoming cookie-family already exists in the working list"() {
        given: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest

        and: "Duplicated uids cookies"
        def genericUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC, RANDOM_EXPIRE_DAY)
        def duplicateUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC, RANDOM_EXPIRE_DAY + 1)

        when: "PBS processes setuid request"
        def response = multipleCookiesPbsService.sendSetUidRequest(request, [genericUidsCookie, duplicateUidsCookie])

        then: "Response should contain single generic uid with most distant expiration timestamp"
        assert response.uidsCookie.tempUIDs.size() == 1
        assert response.uidsCookie.tempUIDs[GENERIC].uid == duplicateUidsCookie.tempUIDs[GENERIC].uid
        assert response.uidsCookie.tempUIDs[GENERIC].expires == duplicateUidsCookie.tempUIDs[GENERIC].expires
    }

    def "PBS should shouldn't modify uids cookie when uid is empty"() {
        given: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            it.uid = null
            it.bidder = GENERIC
        }

        and: "Specific uids cookies"
        def uidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC)

        when: "PBS processes setuid request"
        def response = multipleCookiesPbsService.sendSetUidRequest(request, [uidsCookie])

        then: "Response should contain single generic uid"
        assert response.uidsCookie.tempUIDs.size() == 1
        assert response.uidsCookie.tempUIDs[GENERIC].uid == uidsCookie.tempUIDs[GENERIC].uid
        assert response.uidsCookie.tempUIDs[GENERIC].expires == uidsCookie.tempUIDs[GENERIC].expires
    }

    def "PBS should include all cookies even empty when incoming request have multiple uids cookies"() {
        given: "Setuid request"
        def request = SetuidRequest.defaultSetuidRequest.tap {
            uid = UUID.randomUUID().toString()
        }
        def genericUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC)
        def rubiconUidsCookie = UidsCookie.getDefaultUidsCookie(RUBICON)

        when: "PBS processes setuid request"
        def response = multipleCookiesPbsService.sendSetUidRequest(request, [genericUidsCookie, rubiconUidsCookie])

        then: "Response should contain requested tempUIDs"
        assert response.uidsCookie.tempUIDs[GENERIC]
        assert response.uidsCookie.tempUIDs[RUBICON]

        and: "Headers uids cookies should contain same cookie as response"
        assert getSetUidsHeaders(response).size() == 1
        assert getSetUidsHeaders(response, true).size() == MAX_NUMBER_OF_UID_COOKIES
    }

    def "PBS shouldn't failed with error when adapters has same user sync and vendor id config"() {
        given: "Default set uid request"
        def request = SetuidRequest.defaultSetuidRequest

        and: "Default uids cookie generic and adtrgtme"
        def genericUidsCookie = UidsCookie.getDefaultUidsCookie(GENERIC)
        def gridUidsCookie = UidsCookie.getDefaultUidsCookie(GRID)

        when: "PBS processes auction request"
        def response = singleCookiesPbsService.sendSetUidRequest(request, [gridUidsCookie, genericUidsCookie])

        then: "Response should contain requested tempUIDs"
        assert response.uidsCookie.tempUIDs[GENERIC]
        assert response.uidsCookie.tempUIDs[GRID]
    }

    def "PBS shouldn't failed with error when alias adapters has same user sync and vendor id config"() {
        given: "Default set uid request"
        def request = SetuidRequest.defaultSetuidRequest

        and: "Default uids cookie generic alias and opnex alias"
        def genericAliasUidsCookie = UidsCookie.getDefaultUidsCookie(ALIAS)
        def genericOpenxAliasUidsCookie = UidsCookie.getDefaultUidsCookie(OPENX_ALIAS)

        when: "PBS processes auction request"
        def response = singleCookiesPbsService.sendSetUidRequest(request, [genericAliasUidsCookie, genericOpenxAliasUidsCookie])

        then: "Response should contain requested tempUIDs"
        assert response.uidsCookie.tempUIDs[ALIAS]
        assert response.uidsCookie.tempUIDs[OPENX_ALIAS]
    }

    List<String> getSetUidsHeaders(SetuidResponse response, boolean includeEmpty = false) {
        response.headers.get("Set-Cookie").findAll { cookie ->
            includeEmpty || !(cookie =~ /\buids\d*=\s*;/)
        }
    }

    static UidsCookie extractHeaderTempUIDs(String header) {
        def uid = (header =~ /uids\d*=(\S+?);/)[0][1]
        decodeWithBase64(uid as String, UidsCookie)
    }

    def daysDifference(ZonedDateTime inputDate) {
        ZonedDateTime now = ZonedDateTime.now(Clock.systemUTC()).minusHours(1)
        return ChronoUnit.DAYS.between(now, inputDate)
    }
}
