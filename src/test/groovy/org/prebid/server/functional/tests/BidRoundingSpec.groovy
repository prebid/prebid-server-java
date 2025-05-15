package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.BidRounding.DOWN
import static org.prebid.server.functional.model.request.auction.BidRounding.TIME_SPLIT
import static org.prebid.server.functional.model.request.auction.BidRounding.TRUE
import static org.prebid.server.functional.model.request.auction.BidRounding.UNKNOWN
import static org.prebid.server.functional.model.request.auction.BidRounding.UP

class BidRoundingSpec extends BaseSpec {

    def "PBS should round bid value to the down when account bid rounding empty or unknown"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def account = getAccountWithBidRounding(bidRequest.accountId, bidRoundingValue)
        accountDao.save(account)

        and: "Default bid response"
        def bidPrice = PBSUtils.randomFloorValue
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = bidPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Targeting hb_pb should be round"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb"] == getRoundedTargetingValueWithDefaultPrecision(bidPrice)

        where:
        bidRoundingValue << [new AccountAuctionConfig(bidRounding: null),
                             new AccountAuctionConfig(bidRounding: UNKNOWN)]
    }

    def "PBS should round bid value to the up when account bid rounding UP or TRUE"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def account = getAccountWithBidRounding(bidRequest.accountId, accountAuctionConfig)
        accountDao.save(account)

        and: "Default bid response"
        def bidPrice = PBSUtils.getRandomFloorValue(0.15, 0.19)
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = bidPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Targeting hb_pb should be round"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb"] == getRoundedTargetingValueWithUpPrecision(bidPrice)

        where:
        accountAuctionConfig << [new AccountAuctionConfig(bidRounding: UP),
                                 new AccountAuctionConfig(bidRounding: TRUE),
                                 new AccountAuctionConfig(bidRoundingSnakeCase: UP),
                                 new AccountAuctionConfig(bidRoundingSnakeCase: TRUE)]
    }

    def "PBS should round bid value to the down when account bid rounding DOWN or TRUE"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def account = getAccountWithBidRounding(bidRequest.accountId, accountAuctionConfig)
        accountDao.save(account)

        and: "Default bid response"
        def bidPrice = PBSUtils.getRandomFloorValue(0.11, 0.14)
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = bidPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Targeting hb_pb should be round"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb"] == getRoundedTargetingValueWithDefaultPrecision(bidPrice)

        where:
        accountAuctionConfig << [new AccountAuctionConfig(bidRounding: DOWN),
                                 new AccountAuctionConfig(bidRounding: TRUE),
                                 new AccountAuctionConfig(bidRoundingSnakeCase: DOWN),
                                 new AccountAuctionConfig(bidRoundingSnakeCase: TRUE)]
    }

    def "PBS should round bid value to the 50% down and 50% up when account bid rounding time split"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            ext.prebid.targeting = new Targeting(includeBidderKeys: true)
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2, targetBidderCodePrefix: GENERIC.value)]
        }

        and: "Account in the DB"
        def account = getAccountWithBidRounding(bidRequest.accountId, accountAuctionConfig)
        accountDao.save(account)

        and: "Default bid response"
        def bidPrice = 0.15
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = bidPrice
            seatbid[0].bid.add(Bid.getDefaultBids(bidRequest.imp)[0])
            seatbid[0].bid[1].price = bidPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Targeting hb_pb should be round"
        def targeting = response.seatbid[0].bid.ext.prebid.targeting
        assert targeting.collectEntries().findAll { it -> it.key.toString().contains("hb_pb_") }
                .values().sort() == [getRoundedTargetingValueWithDefaultPrecision(bidPrice),
                                     getRoundedTargetingValueWithUpPrecision(bidPrice)].sort()

        where:
        accountAuctionConfig << [new AccountAuctionConfig(bidRounding: TIME_SPLIT),
                                 new AccountAuctionConfig(bidRoundingSnakeCase: TIME_SPLIT)]
    }

    private static final Account getAccountWithBidRounding(String accountId, AccountAuctionConfig accountAuctionConfig) {
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        new Account(uuid: accountId, config: accountConfig)
    }
}
