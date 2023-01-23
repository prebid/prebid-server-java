package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.CompressionType.GZIP
import static org.prebid.server.functional.util.HttpUtil.CONTENT_ENCODING_HEADER

class AliasSpec extends BaseSpec {

    def "PBS should apply aliases for bidder"() {
        given: "Default bid request with alias"
        def aliases = [("alias"): GENERIC] as Map
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = aliases
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain same url for bidder request"
        def httpcalls = response.ext.debug.httpcalls
        assert httpcalls.size() == 2
        assert httpcalls[GENERIC.value]*.uri == httpcalls[ALIAS.value]*.uri

        and: "Bidder request should contain aliases"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.every() { it.ext.prebid.aliases == aliases }
    }

    def "PBS should apply compression type for bidder alias when adapters.BIDDER.endpoint-compression = gzip"() {
        given: "PBS with adapter configuration"
        def compressionType = GZIP.value
        def pbsService = pbsServiceFactory.getService(
                ["adapters.generic.endpoint-compression": compressionType])

        and: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [("alias"): GENERIC]
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain header Content-Encoding = gzip"
        assert response.ext?.debug?.httpcalls?.get(ALIAS.value)?.requestHeaders?.first()
                       ?.get(CONTENT_ENCODING_HEADER)?.first() == compressionType
    }

    def "PBS should return an error when GVL Id alias refers to unknown bidder alias"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def validId = 1
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): validId]
        bidRequest.ext.prebid.aliases = [(PBSUtils.randomString): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "vendorId ${validId} refers to unknown bidder alias: ${bidderName}")
    }

    def "PBS should return an error when GVL ID alias value is lower that one"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): invalidId]
        bidRequest.ext.prebid.aliases = [(bidderName): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "Invalid vendorId ${invalidId} for alias: ${bidderName}. Choose a different vendorId, or remove this entry.")

        where:
        invalidId << [PBSUtils.randomNegativeNumber, 0]
    }
}
