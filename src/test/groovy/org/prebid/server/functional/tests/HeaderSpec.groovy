package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.PBSUtils

class HeaderSpec extends BaseSpec {

    private static final String VALID_VALUE_FOR_GPC_HEADER = "1"

    def "PBS amp should send headers to bidder request from incoming header"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        def request = defaultPbsService.sendAmpRequestRaw(ampRequest, additionalHeader)

        then: "Bidder request should contain header from incoming request"
        def bidderRequestHeaders = bidder.getLastRecordedBidderRequestHeaders(ampStoredRequest.id)
        assert bidderRequestHeaders.containsKey(additionalHeader.keySet().first())
        assert bidderRequestHeaders.containsValue([additionalHeader.values().first()])

        and: "Amp response shouldn't contain requested headers"
        assert !request.headers.containsKey(additionalHeader.keySet().first())

        where:
        additionalHeader << [["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER],
                             ["Save-Data": PBSUtils.randomString],
                             ["Sec-CH-UA": PBSUtils.randomString],
                             ["Sec-CH-UA-Mobile": PBSUtils.randomString],
                             ["Sec-CH-UA-Platform": PBSUtils.randomString]]
    }

    def "PBS amp shouldn't send headers to bidder request from incoming header when requested unknown headers"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        def request = defaultPbsService.sendAmpRequestRaw(ampRequest, additionalHeader)

        then: "Bidder request should contain header from incoming request"
        def bidderRequestHeaders = bidder.getLastRecordedBidderRequestHeaders(ampStoredRequest.id)
        assert !bidderRequestHeaders.containsKey(additionalHeader.keySet().first())

        and: "Amp response shouldn't contain requested headers"
        assert !request.headers.containsKey(additionalHeader.keySet().first())

        where:
        additionalHeader << [["Sec-GPC-${PBSUtils.randomString}": VALID_VALUE_FOR_GPC_HEADER],
                             ["Save-Data-${PBSUtils.randomString}" : PBSUtils.randomString],
                             ["Sec-CH-UA-${PBSUtils.randomString}" : PBSUtils.randomString],
                             ["Sec-CH-UA-Mobile-${PBSUtils.randomString}" : PBSUtils.randomString],
                             ["Sec-CH-UA-Platform-${PBSUtils.randomString}" : PBSUtils.randomString]]
    }

    def "PBS auction should send headers to bidder request from incoming request header"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request with header"
        def request = defaultPbsService.sendAuctionRequestRaw(bidRequest, additionalHeader)

        then: "Bidder request should contain header from incoming request"
        def bidderRequestHeaders = bidder.getLastRecordedBidderRequestHeaders(bidRequest.id)
        assert bidderRequestHeaders.containsKey(additionalHeader.keySet().first())
        assert bidderRequestHeaders.containsValue([additionalHeader.values().first()])

        and: "Bid response shouldn't contain requested headers"
        assert !request.headers.containsKey(additionalHeader.keySet().first())

        where:
        additionalHeader << [["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER],
                             ["Save-Data": PBSUtils.randomString],
                             ["Sec-CH-UA": PBSUtils.randomString],
                             ["Sec-CH-UA-Mobile": PBSUtils.randomString],
                             ["Sec-CH-UA-Platform": PBSUtils.randomString]]
    }

    def "PBS auction shouldn't send headers to bidder request from incoming request header when requested unknown headers"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request with header"
        def request = defaultPbsService.sendAuctionRequestRaw(bidRequest, additionalHeader)

        then: "Bidder request should contain header from incoming request"
        def bidderRequestHeaders = bidder.getLastRecordedBidderRequestHeaders(bidRequest.id)
        assert !bidderRequestHeaders.containsKey(additionalHeader.keySet().first())

        and: "Bid response shouldn't contain requested headers"
        assert !request.headers.containsKey(additionalHeader.keySet().first())

        where:
        additionalHeader << [["Sec-GPC-${PBSUtils.randomString}": VALID_VALUE_FOR_GPC_HEADER],
                             ["Save-Data-${PBSUtils.randomString}" : PBSUtils.randomString],
                             ["Sec-CH-UA-${PBSUtils.randomString}" : PBSUtils.randomString],
                             ["Sec-CH-UA-Mobile-${PBSUtils.randomString}" : PBSUtils.randomString],
                             ["Sec-CH-UA-Platform-${PBSUtils.randomString}" : PBSUtils.randomString]]
    }
}
