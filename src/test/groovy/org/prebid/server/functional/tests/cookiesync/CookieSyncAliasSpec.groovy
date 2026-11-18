package org.prebid.server.functional.tests.cookiesync

import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.service.PrebidServerService

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.NO_COOKIE
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT

class CookieSyncAliasSpec extends CookieSyncBaseSpec {

    private static PrebidServerService pbsWithAliasService

    def setupSpec() {
        pbsWithAliasService = pbsServiceFactory.getService(PBS_WITH_ALIAS_CONFIG)
    }

    def "PBS cookie sync request shouldn't replace synced as family bidder and fill up response with enabled bidders to the limit in request"() {
        given: "Default cookie sync request"
        def requestLimit = 2
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [ALIAS]
            limit = requestLimit
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain source bidder"
        assert !response.getBidderUserSync(GENERIC)

        and: "Response should contain coop-synced bidder"
        assert response.bidderStatus.size() == requestLimit
        assert response.getBidderUserSync(ALIAS)
    }

    def "PBS cookie sync should use requested bidder name in sync url instead of cookie family name"() {
        given: "Cookie sync request for alias bidder"
        def bidderAlias = ALIAS
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [bidderAlias]
            limit = 1
            coopSync = true
            debug = false
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain source bidder"
        assert !response.getBidderUserSync(GENERIC)

        and: "Response should contain alias bidder sync"
        def bidderStatus = response.getBidderUserSync(bidderAlias)

        and: "Sync url should contain requested bidder name"
        assert bidderStatus.userSync.url.contains("setuid%3Fbidder%3D${bidderAlias}")

        and: "Sync url shouldn't contain cookie family name"
        assert !bidderStatus.userSync.url.contains("setuid%3Fbidder%3D${GENERIC}")
    }

    def "PBS cookie sync request shouldn't reflect error when coop-sync enabled and coop sync bidder synced"() {
        given: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [ALIAS]
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response shouldn't contain bidder error"
        def bidderStatus = response.getBidderUserSync(ALIAS)
        assert !bidderStatus?.error

        and: "Response should indicate bidder has no cookie"
        assert bidderStatus?.noCookie == true

        and: "Response should contain bidder user sync information"
        verifyAll(bidderStatus?.userSync) {
            bidderStatus?.userSync?.url?.startsWith(USER_SYNC_URL)
            it.type == REDIRECT
        }
    }

    def "PBS cookie sync request with alias bidder should sync as the source bidder when alias doesn't override cookie-family-name"() {
        given: "Cookie sync request with 2 bidders"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, ALIAS]
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithAliasService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain sync information for single resolved bidder"
        def validBidderUserSyncs = getValidBidderUserSyncs(response).values().toList()
        assert validBidderUserSyncs.url.every { it.startsWith(USER_SYNC_URL) }
        assert validBidderUserSyncs.type == [USER_SYNC_TYPE]

        and: "Response should contain duplicate bidder error"
        def rejectedBidderStatus = getRejectedBidderUserSyncs(response).values().toList()
        assert rejectedBidderStatus == ["Duplicate bidder synced as ${GENERIC.value}"]
    }
}
