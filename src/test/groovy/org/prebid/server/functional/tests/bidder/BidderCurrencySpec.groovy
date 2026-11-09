package org.prebid.server.functional.tests.bidder

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.tests.BaseSpec

import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.GBP
import static org.prebid.server.functional.model.Currency.JPY
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY
import static org.prebid.server.functional.model.response.auction.ErrorType.ALIAS
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class BidderCurrencySpec extends BaseSpec {

    private static final PrebidServerService noCurrencyAcceptedService = pbsServiceFactory.getService(
            "adapters.${BidderName.GENERIC}.meta-info.currency-accepted": "",
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.enabled": "true",
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.meta-info.vendor-id": "0",
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.endpoint": "$Dependencies.networkServiceContainer.rootUri/auction".toString(),
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.meta-info.currency-accepted": ""
    )

    private static PrebidServerService withCurrencyAcceptedService = pbsServiceFactory.getService(
            "adapters.generic.meta-info.currency-accepted": "${USD},${GBP}".toString(),
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.enabled": "true",
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.meta-info.vendor-id": "0",
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.endpoint": "$Dependencies.networkServiceContainer.rootUri/auction".toString(),
            "adapters.${BidderName.GENERIC}.aliases.${BidderName.ALIAS}.meta-info.currency-accepted": "${EUR},${JPY}".toString()
    )

    def "PBS should send request to bidder when adapters.bidder.meta-info.currency-accepted not specified"() {
        given: "Default bid request with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = noCurrencyAcceptedService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
    }

    def "PBS should send request to bidder when adapters.bidder.aliases.bidder.meta-info.currency-accepted not specified"() {
        given: "Default bid request with alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        when: "PBS processes auction request"
        def response = noCurrencyAcceptedService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[BidderName.ALIAS.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
    }

    def "PBS should send request to bidder when adapters.bidder.meta-info.currency-accepted intersect with requested currency"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = withCurrencyAcceptedService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid and contain errors"
        assert !response.ext.seatnonbid
    }

    def "PBS shouldn't send request to bidder and emit warning when adapters.bidder.meta-info.currency-accepted not intersect with requested currency"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [EUR]
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = withCurrencyAcceptedService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain http calls"
        assert !response.ext?.debug?.httpcalls

        and: "Response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Pbs shouldn't make bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response should seatNon bid with code 205"
        assert response.ext.seatnonbid.size() == 1

        and: "PBS should emit an warnings"
        assert response.ext?.warnings[GENERIC]*.code == [999]
        assert response.ext?.warnings[GENERIC]*.message ==
                ["No match between the configured currencies and bidRequest.cur"]

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY
    }

    def "PBS should send request to bidder when adapters.bidder.aliases.bidder.meta-info.currency-accepted intersect with requested currency"() {
        given: "Default basic BidRequest with alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [JPY]
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = JPY
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = withCurrencyAcceptedService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[ALIAS.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid and contain errors"
        assert !response.ext.seatnonbid
    }

    def "PBS shouldn't send request to bidder and emit warning when adapters.bidder.aliases.bidder.meta-info.currency-accepted not intersect with requested currency"() {
        given: "Default basic BidRequest with alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [GBP]
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = withCurrencyAcceptedService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain http calls"
        assert !response.ext?.debug?.httpcalls

        and: "Response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Pbs shouldn't make bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "PBS should emit an warnings"
        assert response.ext?.warnings[ALIAS]*.code == [999]
        assert response.ext?.warnings[ALIAS]*.message ==
                ["No match between the configured currencies and bidRequest.cur"]

        and: "Response should seatNon bid with code 205"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY
    }
}
