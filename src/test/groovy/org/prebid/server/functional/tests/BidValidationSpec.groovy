package org.prebid.server.functional.tests

import org.apache.http.HttpStatus
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Dooh
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.response.BidderErrorCode
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import java.time.Instant

import static org.apache.http.HttpStatus.SC_BAD_REQUEST
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.DebugCondition.DISABLED
import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.NoBidResponse.UNKNOWN_ERROR
import static org.prebid.server.functional.util.HttpUtil.REFERER_HEADER

class BidValidationSpec extends BaseSpec {

    @PendingFeature
    def "PBS should return error type invalid bid when bid does not pass validation with error"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Default basic bid with seatbid[].bid[].price = null"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid.first().price = null

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain basic fields"
        assert response.ext?.errors[ErrorType.GENERIC]*.code == [5]
        assert response.ext?.errors[ErrorType.GENERIC]*.message ==
                ["Bid \"${bidResponse.seatbid.first().bid.first().id}\" does not contain a 'price'" as String]
    }

    def "PBS should throw an exception when bid request includes more than one distribution channel and strict setting enabled for service"() {
        given: "PBS with string setting enabled"
        def strictPrebidService = pbsServiceFactory.getService(['auction.strict-app-site-dooh': 'true'])

        and: "Flush metrics"
        flushMetrics(strictPrebidService)

        when: "PBS processes auction request"
        def response = strictPrebidService.sendAuctionRequest(bidRequest, SC_BAD_REQUEST)

        then: "PBS throws an exception"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BidderErrorCode.GENERIC]
            it.errorMassage.any { it.contains("no more than one of request.site or request.app or request.dooh can be defined") }
        }

        and: "Bid validation metric value is incremented"
        def metrics = strictPrebidService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        where:
        bidRequest << [BidRequest.getDefaultBidRequest(DistributionChannel.APP).tap {
                           it.dooh = Dooh.defaultDooh
                       },
                       BidRequest.getDefaultBidRequest(DistributionChannel.SITE).tap {
                           it.dooh = Dooh.defaultDooh
                       },
                       BidRequest.getDefaultBidRequest(DistributionChannel.SITE).tap {
                           it.app = App.defaultApp
                       }]
    }

    def "PBS should contain response and emit warning logs when bidRequest include multiple distribution channel and strict setting disabled for service"() {
        given: "Start time and random referer"
        def startTime = Instant.now()
        def randomReferer = PBSUtils.randomString

        and: "PBS with string setting disabled"
        def softPrebidService = pbsServiceFactory.getService(['auction.strict-app-site-dooh': 'false'])

        and: "Request distribution channels"
        def requestDistributionChannels = bidRequest.getRequestDistributionChannels()

        and: "Flush metrics"
        flushMetrics(softPrebidService)

        when: "PBS processes auction request"
        def response = softPrebidService.sendAuctionRequest(bidRequest, [(REFERER_HEADER): randomReferer])

        then: "BidResponse contain single seatbid"
        assert response.seatbid.size() == 1

        then: "Response should contain warning"
        def warningChannelsValues = requestDistributionChannels.collect { "${it.value.toLowerCase()}" }.join(" and ")
        assert response.ext?.warnings[PREBID]*.code == [BidderErrorCode.GENERIC]
        assert response.ext?.warnings[PREBID]*.message ==
                ["BidRequest contains $warningChannelsValues. Only the first one is applicable, the others are ignored" as String]

        and: "Bid validation metric value is incremented"
        def metrics = softPrebidService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "PBS log should contain message"
        def logs = softPrebidService.getLogsByTime(startTime)
        def validatorLogChannelsValues = requestDistributionChannels.collect { "request.${it.value.toLowerCase()}" }.join(" and ")
        assert getLogsByText(logs, "$validatorLogChannelsValues are present. Referer: $randomReferer")
        assert getLogsByText(logs, "$warningChannelsValues are present. Referer: $randomReferer. Account: ${bidRequest.getAccountId()}")

        where:
        bidRequest << [BidRequest.getDefaultBidRequest(DistributionChannel.APP).tap {
                           it.dooh = Dooh.defaultDooh
                       },
                       BidRequest.getDefaultBidRequest(DistributionChannel.SITE).tap {
                           it.dooh = Dooh.defaultDooh
                       },
                       BidRequest.getDefaultBidRequest(DistributionChannel.SITE).tap {
                           it.app = App.defaultApp
                       },
                       BidRequest.getDefaultBidRequest(DistributionChannel.SITE).tap {
                           it.app = App.defaultApp
                           it.dooh = Dooh.defaultDooh
                       }]
    }

    def "PBS should validate dooh when it is present"() {
        given: "Default basic BidRequest"
        def bidDoohRequest = BidRequest.getDefaultBidRequest(DOOH).tap {
            dooh.id = null
            dooh.venueType = null
        }
        bidDoohRequest.ext.prebid.debug = ENABLED

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidDoohRequest, SC_BAD_REQUEST)

        then: "Request should fail with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BidderErrorCode.GENERIC]
            it.errorMassage.any { it.contains("request.dooh should include at least one of request.dooh.id " +
                    "or request.dooh.venuetype.") }
        }
    }

    def "PBS should validate site when it is present"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)
        bidRequest.ext.prebid.debug = ENABLED

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest, HttpStatus.SC_BAD_REQUEST)

        then: "Request should fail with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BidderErrorCode.GENERIC]
            it.errorMassage.any { it.contains("request.site should include at least one of request.site.id or request.site.page") }
        }
    }

    def "PBS should treat bids with 0 price as valid when deal id is present"() {
        given: "Default basic BidRequest with generic bidder and enabled debug"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        and: "Bid response with 0 price bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid.first().dealid = PBSUtils.randomNumber
        bidResponse.seatbid.first().bid.first().price = 0

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Zero price bid should be present in the PBS response"
        assert response.seatbid?.first()?.bid*.id == [bidResponse.seatbid.first().bid.first().id]

        and: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings
    }

    def "PBS should drop invalid bid and emit debug error when bid price is #bidPrice and deal id is #dealId"() {
        given: "Default basic BidRequest with generic bidder and enabled debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.debug = debug
            it.test = test
        }

        and: "Bid response"
        def bidId = PBSUtils.randomString
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid.first.tap {
                id = bidId
                dealid = dealId
                price = bidPrice
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Invalid bid should be deleted"
        assert !response.seatbid
        assert !response.ext.seatnonbid

        and: "PBS should emit an error"
        assert response.ext?.warnings[PREBID]*.code == [BidderErrorCode.GENERIC]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Dropped bid '$bidId'. Does not contain a positive (or zero if there is a deal) 'price'" as String]

        where:
        debug    | test     | bidPrice                      | dealId
        DISABLED | ENABLED  | PBSUtils.randomNegativeNumber | null
        DISABLED | ENABLED  | PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        DISABLED | ENABLED  | 0                             | null
        DISABLED | ENABLED  | null                          | PBSUtils.randomNumber
        DISABLED | ENABLED  | null                          | null
        ENABLED  | DISABLED | PBSUtils.randomNegativeNumber | null
        ENABLED  | DISABLED | PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        ENABLED  | DISABLED | 0                             | null
        ENABLED  | DISABLED | null                          | PBSUtils.randomNumber
        ENABLED  | DISABLED | null                          | null
    }

    def "PBS should drop invalid bid without debug error when request debug disabled and bid price is #bidPrice and deal id is #dealId"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            test = DISABLED
            ext.prebid.debug = DISABLED
        }

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid.first.tap {
                dealid = dealId
                price = bidPrice
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Invalid bid should be deleted"
        assert !response.seatbid
        assert !response.ext.seatnonbid

        and: "PBS shouldn't emit an error"
        assert !response.ext?.warnings
        assert !response.ext?.warnings

        and: "PBS should call bidder"
        def bidderRequests = bidder.getBidderRequests(bidResponse.id)
        assert bidderRequests.size() == 1

        where:
        bidPrice                      | dealId
        PBSUtils.randomNegativeNumber | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        0                             | null
        null                          | PBSUtils.randomNumber
        null                          | null
    }

    def "PBS should only drop invalid bid without discarding whole seat"() {
        given: "Default basic  BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED
        bidRequest.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "One of the bids is invalid"
        def invalidBid = bidResponse.seatbid.first().bid.first()
        invalidBid.dealid = dealId
        invalidBid.price = bidPrice
        def validBidId = bidResponse.seatbid.first().bid.last().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response contains only valid bid"
        assert response.seatbid?.first()?.bid*.id == [validBidId]

        and: "PBS should emit an error"
        assert response.ext?.warnings[PREBID]*.code == [BidderErrorCode.GENERIC]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Dropped bid '$invalidBid.id'. Does not contain a positive (or zero if there is a deal) 'price'" as String]

        where:
        debug | test | bidPrice                      | dealId
        0     | 1    | PBSUtils.randomNegativeNumber | null
        0     | 1    | PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        0     | 1    | 0                             | null
        0     | 1    | null                          | PBSUtils.randomNumber
        0     | 1    | null                          | null
        1     | 0    | PBSUtils.randomNegativeNumber | null
        1     | 0    | PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        1     | 0    | 0                             | null
        1     | 0    | null                          | PBSUtils.randomNumber
        1     | 0    | null                          | null
    }

    def "PBS should only drop invalid bid without discarding whole seat without debug error when request debug disabled "() {
        given: "Default basic  BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            test = DISABLED
            ext.prebid.tap {
                debug = DISABLED
                multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            }
        }

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "One of the bids is invalid"
        def invalidBid = bidResponse.seatbid.first().bid.first()
        invalidBid.dealid = dealId
        invalidBid.price = bidPrice
        def validBidId = bidResponse.seatbid.first().bid.last().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response contains only valid bid"
        assert response.seatbid?.first()?.bid*.id == [validBidId]

        and: "PBS shouldn't emit an error"
        assert !response.ext?.warnings
        assert !response.ext?.warnings

        where:
        bidPrice                      | dealId
        PBSUtils.randomNegativeNumber | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        0                             | null
        null                          | PBSUtils.randomNumber
        null                          | null
    }

    def "PBS should update 'adapter.generic.requests.bid_validation' metric when bid validation error appears"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set invalid bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].impid = PBSUtils.randomNumber as String
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid validation metric value is incremented"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["adapter.generic.requests.bid_validation"] == 1
    }

    def "PBS shouldn't throw error when two separate eids with same eids.source"() {
        given: "Default bid request with user.eids"
        def source = PBSUtils.randomString
        def defaultEids = [Eid.getDefaultEid(source), Eid.getDefaultEid(source)]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                eids = defaultEids
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should contain same eids as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids[0].source == defaultEids[0].source
        assert bidderRequest.user.eids[0].uids[0].id == defaultEids[0].uids[0].id
        assert bidderRequest.user.eids[0].uids[0].atype == defaultEids[0].uids[0].atype

        assert bidderRequest.user.eids[1].source == defaultEids[1].source
        assert bidderRequest.user.eids[1].uids[0].id == defaultEids[1].uids[0].id
        assert bidderRequest.user.eids[1].uids[0].atype == defaultEids[1].uids[0].atype
    }

    def "PBS shouldn't throw error when two separate eids with different eids.source"() {
        given: "Default bid request with user.eids"
        def defaultEids = [
                Eid.getDefaultEid(PBSUtils.randomString),
                Eid.getDefaultEid(PBSUtils.randomString)]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                eids = defaultEids
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should contain same eids as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids[0].source == defaultEids[0].source
        assert bidderRequest.user.eids[0].uids[0].id == defaultEids[0].uids[0].id
        assert bidderRequest.user.eids[0].uids[0].atype == defaultEids[0].uids[0].atype

        assert bidderRequest.user.eids[1].source == defaultEids[1].source
        assert bidderRequest.user.eids[1].uids[0].id == defaultEids[1].uids[0].id
        assert bidderRequest.user.eids[1].uids[0].atype == defaultEids[1].uids[0].atype
    }
}
