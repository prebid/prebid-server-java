package org.prebid.server.functional.tests.cookiesync

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.cookiesync.FilterSettings
import org.prebid.server.functional.model.request.cookiesync.MethodFilter
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.AAX
import static org.prebid.server.functional.model.bidder.BidderName.ACEEX
import static org.prebid.server.functional.model.bidder.BidderName.ADKERNEL
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.MAGNITE
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.cookiesync.FilterType.EXCLUDE
import static org.prebid.server.functional.model.request.cookiesync.FilterType.INCLUDE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.NO_COOKIE
import static org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse.Status.OK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.BLANK
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Format.PIXEL
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.IFRAME
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT

class CookieSyncNoLimitSpec extends CookieSyncBaseSpec {

    private static PrebidServerService pbsWithoutLimitService

    def setupSpec() {
        pbsWithoutLimitService = pbsServiceFactory.getService(PBS_CONFIG)
    }

    def "PBS cookie sync should emit error when requested bidder rejected by limit"() {
        given: "Default cookie sync request with 2 bidders"
        def limit = 1
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.bidders = DEFAULT_PBS_BIDDERS
            it.limit = limit
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain only one valid user sync"
        def validBidderUserSyncs = getValidBidderUserSyncs(response)
        assert validBidderUserSyncs.size() == limit

        and: "Discarded bidder user sync should contain an error"
        def rejectedBidderUserSyncs = getRejectedBidderUserSyncs(response)
        assert rejectedBidderUserSyncs.size() == DEFAULT_PBS_BIDDERS.size() - limit
        assert rejectedBidderUserSyncs.every { it.value == "Limit reached" }
    }

    def "PBS cookie sync request should successful pass when request body empty"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest()

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain '#formatParam' format parameter"
        def genericBidderStatus = response.getBidderUserSync(ACEEX)
        assert genericBidderStatus?.userSync?.type == REDIRECT
        assert HttpUtil.findUrlParameterValue(genericBidderStatus.userSync?.url, "f") == PIXEL.name

        and: "Response should contain '#formatParam' format parameter"
        def magniteBidderStatus = response.getBidderUserSync(MAGNITE)
        assert magniteBidderStatus?.userSync?.type == IFRAME
        assert HttpUtil.findUrlParameterValue(magniteBidderStatus.userSync?.url, "f") == BLANK.name

        and: "Response should contain coop-synced bidder"
        assert response.bidderStatus.bidder.containsAll(DEFAULT_PBS_BIDDERS)
    }

    def "PBS cookie sync request shouldn't include bidder with invalid user sync type"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [GENERIC], filter: INCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain bidders with Redirect type"
        def userSync = response.getBidderUserSync(GENERIC)
        assert userSync?.userSync?.type == REDIRECT
    }

    def "PBS cookie sync request shouldn't exclude bidder with invalid user sync type"() {
        given: "Cookie sync request with filter setting"
        def filterSettings = new FilterSettings().tap {
            iframe = new MethodFilter(bidders: [GENERIC], filter: EXCLUDE)
        }
        def cookieSyncRequest = new CookieSyncRequest(filterSettings: filterSettings, limit: 0)

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain bidders with Redirect type"
        def userSync = response.getBidderUserSync(GENERIC)
        assert userSync?.userSync?.type == REDIRECT
    }

    def "PBS cookie sync request should successfully passed when account is absent"() {
        given: "Cookie sync request with empty account"
        def cookieSyncRequest = new CookieSyncRequest(account: null)

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain configured bidders"
        assert response?.getBidderStatus()?.bidder?.toSorted() == DEFAULT_PBS_BIDDERS.toSorted()
    }

    def "PBS cookie sync request shouldn't return sync url when active uids cookie is present for bidder"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest()

        and: "Set up uids cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        when: "PBS processes cookie sync request with uids cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest, uidsCookie)

        then: "Response should have status 'OK'"
        assert response.status == OK

        and: "Response shouldn't contain bidder"
        assert !response?.getBidderUserSync(GENERIC)

        and: "Response should contain configured bidders"
        assert response?.getBidderStatus()?.bidder?.sort() == (DEFAULT_PBS_BIDDERS - GENERIC).sort()
    }

    def "PBS cookie sync request should return all possible bidder"() {
        given: "Cookie sync request with non specified bidders and specified coop sync"
        def cookieSyncRequest = new CookieSyncRequest(bidders: null, coopSync: true)

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain coop sync bidders"
        assert response.bidderStatus.size() == DEFAULT_PBS_BIDDERS.size()
        assert response.getBidderUserSync(AAX)
        assert response.getBidderUserSync(ADKERNEL)
        assert response.getBidderUserSync(OPENX)
        assert response.getBidderUserSync(GENERIC)
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(MAGNITE)
        assert response.getBidderUserSync(ACEEX)
    }

    def "PBS cookie sync request should contain request bidders and rest from coop sync"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [APPNEXUS, GENERIC]
            coopSync = true
        }

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        then: "Response should contain requested bidder and coop-synced"
        assert response.getBidderStatus().size() > cookieSyncRequest.bidders.size()
        assert response.getBidderUserSync(APPNEXUS)
        assert response.getBidderUserSync(GENERIC)
    }

    def "PBS cookie sync request should return eight users sync bidders when limit isn't define"() {
        given: "Empty cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            limit = null
            bidders = null
        }

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should have status 'NO_COOKIE'"
        assert response.status == NO_COOKIE

        and: "Response should contain eight bidder by default"
        assert response?.getBidderStatus()?.size() == DEFAULT_PBS_BIDDERS.size()
    }

    def "PBS cookie sync request should fill response with all available coop sync bidder when limit is not specified"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.coopSync = true
            it.limit = null
        }

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains all available coop sync bidders"
        assert response.bidderStatus.bidder.size() == DEFAULT_PBS_BIDDERS.size()
    }

    def "PBS cookie sync request should override general configuration limit"() {
        given: "Cookie sync request body"
        def cookieSyncRequest = new CookieSyncRequest().tap {
            it.coopSync = true
            it.limit = PBSUtils.getRandomNumber(1, DEFAULT_PBS_BIDDERS.size())
        }

        when: "PBS processes cookie sync request without cookies"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contains same count of bidder as specified limit"
        assert response.bidderStatus.size() == cookieSyncRequest.limit
    }

    def "PBS cookie sync request should take presence coop sync over coop sync in config"() {
        given: "Default cookie sync request"
        def accountId = PBSUtils.randomNumber
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, BOGUS]
            account = accountId
            coopSync = true
        }

        and: "Save account with cookie config"
        def accountConfig = new AccountConfig(status: ACTIVE, cookieSync: cookieSyncConfig)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = pbsWithoutLimitService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain corresponding bidders size due to global config"
        assert response.bidderStatus.size() == (DEFAULT_PBS_BIDDERS + BOGUS).size()

        where:
        cookieSyncConfig << [new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: true)),
                             new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false)),
                             new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: null)),
                             new AccountCookieSyncConfig(coopSyncSnakeCase: new AccountCoopSyncConfig(enabled: true)),
                             new AccountCookieSyncConfig(coopSyncSnakeCase: new AccountCoopSyncConfig(enabled: false)),
                             new AccountCookieSyncConfig(coopSyncSnakeCase: new AccountCoopSyncConfig(enabled: null))]
    }
}
