package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.activitie.Activity
import org.prebid.server.functional.model.request.activitie.AllowActivities
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies

import java.time.Clock
import java.time.ZonedDateTime

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.setuid.UidWithExpiry.getDefaultUidWithExpiry
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT

// TODO update tests by sync config due to fact than CookieSyncRequest will not be updated
class GppSyncUserActivitiesSpec extends ActivityBaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$Dependencies.networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    private static final Map<String, String> RUBICON_CONFIG = [
            "adapters.${RUBICON.value}.enabled"                    : "true",
            "adapters.${RUBICON.value}.usersync.cookie-family-name": RUBICON.value,]
    private static final Map<String, String> APPNEXUS_CONFIG = [
            "adapters.${APPNEXUS.value}.enabled"                    : "true",
            "adapters.${APPNEXUS.value}.usersync.cookie-family-name": APPNEXUS.value]
    private static final Map<String, String> PBS_CONFIG = APPNEXUS_CONFIG + RUBICON_CONFIG + GENERIC_CONFIG

    private PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS cookie sync request with all bidder allowed in activities should include all bidders"() {
        given: "Activities set for cookie sync with all bidders allowed"
        AllowActivities activities = gene(List.of("bidders"), true)

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = false
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 3

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request with add bidders restricted in activities should include none bidders"() {
        given: "Activities set for cookie sync with full bidder restriction"
        AllowActivities activities = generateDefaultActivities(List.of("bidders"), false)

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = true
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 0
        and: "Include warning"
        assert response.warnings == ["Bidder sync blocked for privacy reasons"]
    }

    def "PBS cookie sync request with restricted bidder in activities should exclude bidder from response"() {
        given: "Activities set for cookie sync with single bidder restriction"
        BidderName excludedBidder = APPNEXUS
        AllowActivities activities = generateDefaultActivities(List.of(APPNEXUS.value), false)

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = false
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 2

        and: "Response shouldn't contain excluded bidder"
        assert !response.getBidderUserSync(excludedBidder)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(RUBICON)
    }

    def "PBS cookie sync request with allowed bidders in activities should exclude other bidder from response"() {
        given: "Activities set for cookie sync with single bidder restriction"
        BidderName excludedBidder = APPNEXUS
        AllowActivities activities = generateDefaultActivities(List.of(GENERIC.value, RUBICON.value), true)

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = false
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 2

        and: "Response shouldn't contain excluded bidder"
        assert !response.getBidderUserSync(excludedBidder)

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(RUBICON)
    }

    def "PBS cookie sync request with invalid bidder allow in activities should include none bidders"() {
        given: "Activities set for cookie sync with invalid bidder allow"
        AllowActivities activities = generateDefaultActivities(List.of("ivalid_bidder"), true)

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = true
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 0

        and: "Include warning"
        assert response.warnings == ["Bidder sync blocked for privacy reasons"]
    }

    def "PBS cookie sync request with only invalid bidder restricted in activities should include none bidder"() {
        given: "Activities set for cookie sync with invalid bidder allow"
        AllowActivities activities = generateDefaultActivities(List.of("ivalid_bidder"), false)

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = true
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 3

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS cookie sync request with empty rules for allow activities should act as default"() {
        given: "Activities set for cookie sync with empty rules"
        AllowActivities activities = AllowActivities.defaultAllowActivities.tap {
            syncUser = Activity.defaultActivityRule.tap {
                rules = []
        }
        }

        and: "Cookie sync request with allow activities set"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            coopSync = true
            debug = true
            allowActivities = activities
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 3

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(RUBICON)
        assert response.getBidderUserSync(APPNEXUS)
    }

    def "PBS setuid request with no bidder restriction in activities should respond with all valid bidders"() {
        given: "Activities set for setuid with no bidder restriction"
        AllowActivities activities = generateDefaultActivities(List.of("bidders"), true)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            allowActivities = activities
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

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[RUBICON]
    }

    def "PBS setuid should reject bidder when it restricted by activities"() {
        given: "Activities set for cookie sync with no bidder restriction"
        AllowActivities activities = generateDefaultActivities(List.of(APPNEXUS.value), false)

        and: "Cookie sync SetuidRequest with allow activities set"

        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            allowActivities = activities
        }
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            def appnexusUidWithExpiry = defaultUidWithExpiry.tap {
                expires = ZonedDateTime.now(Clock.systemUTC()).plusDays(5)
            }
            tempUIDs = [(APPNEXUS): appnexusUidWithExpiry]
        }
        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"

        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 451
        assert exception.responseBody == "The gdpr_consent param prevents cookies from being saved"
    }


    def "PBS setuid should reject request when some of them restricted by activities setup"() {
        given: "Activities set for cookie sync with no bidder restriction"
        AllowActivities activities = generateDefaultActivities(List.of(APPNEXUS.value), false)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            allowActivities = activities
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
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"

        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 451
        assert exception.responseBody == "The gdpr_consent param prevents cookies from being saved"
    }

    def "PBS setuid request with empty rules for allow activities should act as default"() {
        given: "Activities set with empty rules"
        AllowActivities activities = AllowActivities.defaultAllowActivities.tap {
            syncUser = Activity.defaultActivityRule.tap {
                rules = []
            }
        }
        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            allowActivities = activities
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

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.
                sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[RUBICON]
    }
}
