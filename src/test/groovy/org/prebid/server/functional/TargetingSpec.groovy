package org.prebid.server.functional

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.AdServerTargeting
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.AdServerTargetingSource.BIDREQUEST
import static org.prebid.server.functional.model.request.auction.AdServerTargetingSource.BIDRESPONSE
import static org.prebid.server.functional.model.request.auction.AdServerTargetingSource.STATIC

class TargetingSpec extends BaseSpec {

    private static final int TARGETING_PARAM_MAX_LENGTH = 20
    private static final int TARGETING_PARAM_MIN_LENGTH = 11

    def "PBS should add custom values to targeting when ad server targeting specified"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Stored request with ad server targeting"
        def bidRequestKey = PBSUtils.randomString
        def ampDataKey = "account"
        def staticKey = PBSUtils.randomString
        def staticValue = PBSUtils.randomString
        def macroKey = "${GENERIC}_custom"

        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.adServerTargeting =
                    [new AdServerTargeting(key: bidRequestKey, source: BIDREQUEST, value: "imp.id"),
                     new AdServerTargeting(key: ampDataKey, source: BIDREQUEST, value: "ext.prebid.amp.data.$ampDataKey"),
                     new AdServerTargeting(key: staticKey, source: STATIC, value: staticValue),
                     new AdServerTargeting(key: macroKey, source: BIDRESPONSE, value: "seatbid.bid.price")]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default basic bid"
        def price = PBSUtils.getRandomNumber(1, 10)
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].price = price
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response targeting data should match the rules defined in adServerTargeting"
        assert response.targeting[bidRequestKey] == ampStoredRequest.imp[0].id
        assert response.targeting[ampDataKey] == ampRequest.account as String
        assert response.targeting[staticKey] == staticValue
        assert response.targeting[macroKey as String] == price as String
    }

    @Unroll
    def "PBS should not add custom value to targeting when ad server targeting contain incorrect fields"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Stored request with ad server targeting"
        def key = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.adServerTargeting = [new AdServerTargeting(key: key, source: source, value: value)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default basic bid"
        def price = PBSUtils.getRandomNumber(1, 10)
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].price = price
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response targeting should not contain incorrect value"
        assert !response.targeting[key]

        where:
        source      | value
        BIDREQUEST  | "imp"
        BIDREQUEST  | "ext.prebid.bogus"
        BIDRESPONSE | "seatbid.bid"
        BIDRESPONSE | "seatbid.bid.ext.bogus"
    }

    def "PBS should set the max length for names of targeting keywords according to the value 'settings.targeting.truncate-attr-chars'"() {
        given: "PBS with targeting configuration"
        def maxLength = 15
        def pbsService = pbsServiceFactory.getService("settings.targeting.truncate-attr-chars": maxLength as String)

        and: "Default amp request"
        //increased timeout for test stabilization
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = 3000
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = pbsService.sendAmpRequest(ampRequest)

        println mapper.encode(response.ext.errors)
        then: "PBS should truncate keys"
        assert response.targeting
        assert response.targeting*.getKey().every { it.size() <= maxLength }
    }

    def "PBS should truncate targeting keywords specified in ad server targeting"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Stored request with ad server targeting"
        def key = PBSUtils.getRandomString(25)
        def value = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.adServerTargeting = [new AdServerTargeting(key: key, source: STATIC, value: value)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "PBS should truncate key"
        assert !response.targeting[key]
        assert response.targeting[getTruncatedTargetingParam(key)] == value
    }

    @Unroll
    def "PBS should process targeting duplicated key when truncation specified in account config for auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.site.publisher.id, truncateTargetAttr: truncationLength)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response targeting should not contain keys longer than the specified length"
        assert response.seatbid[0].bid[0].ext.prebid.targeting
        assert response.seatbid[0].bid[0].ext.prebid.targeting*.getKey().every { it.size() <= truncationLength }

        where:
        truncationLength << [1, PBSUtils.getRandomNumber(2, 10)]
    }

    def "PBS should process targeting duplicated key when truncation specified in account config for amp request"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def truncationLength = 15
        def account = new Account(uuid: ampRequest.account, truncateTargetAttr: truncationLength)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "PBS should truncate keys"
        assert response.targeting
        assert response.targeting*.getKey().every { it.size() <= truncationLength }
    }

    def "PBS should return empty targeting when truncation less than the minimum allowed value"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = new Account(uuid: ampRequest.account, truncateTargetAttr: TARGETING_PARAM_MIN_LENGTH - 1)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Targeting should be empty"
        assert response.targeting.isEmpty()
    }

    private static String getTruncatedTargetingParam(String param) {
        param.substring(0, TARGETING_PARAM_MAX_LENGTH)
    }
}
