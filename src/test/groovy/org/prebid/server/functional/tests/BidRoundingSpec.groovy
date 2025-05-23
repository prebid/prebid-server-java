package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.request.auction.BidRounding.DOWN
import static org.prebid.server.functional.model.request.auction.BidRounding.TRUE
import static org.prebid.server.functional.model.request.auction.BidRounding.UNKNOWN
import static org.prebid.server.functional.model.request.auction.BidRounding.UP

class BidRoundingSpec extends BaseSpec {

    def "PBS should round bid value to the down when account bid rounding setting is #bidRoundingValue"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
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
        assert targeting["hb_pb"] == getRoundedTargetingValueWithDownPrecision(bidPrice)

        where:
        bidRoundingValue << [new AccountAuctionConfig(bidRounding: null),
                             new AccountAuctionConfig(bidRounding: UNKNOWN),
                             new AccountAuctionConfig(bidRounding: DOWN),
                             new AccountAuctionConfig(bidRoundingSnakeCase: DOWN)]
    }

    def "PBS should round bid value to the up when account bid rounding setting is #bidRoundingValue"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountWithBidRounding(bidRequest.accountId, bidRoundingValue)
        accountDao.save(account)

        and: "Default bid response"
        def bidPrice = PBSUtils.getRandomFloorValue()
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
        bidRoundingValue << [new AccountAuctionConfig(bidRounding: UP),
                             new AccountAuctionConfig(bidRoundingSnakeCase: UP)]
    }

    def "PBS should round bid value to the up or down when account bid rounding setting is #bidRoundingValue"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountWithBidRounding(bidRequest.accountId, bidRoundingValue)
        accountDao.save(account)

        and: "Default bid response"
        def bidPrice = PBSUtils.getRandomFloorValue()
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = bidPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Targeting hb_pb should be round"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb"] == getRoundedTargetingValueWithHalfUpPrecision(bidPrice)

        where:
        bidRoundingValue << [new AccountAuctionConfig(bidRounding: TRUE),
                             new AccountAuctionConfig(bidRoundingSnakeCase: TRUE)]
    }

    private static final Account getAccountWithBidRounding(String accountId, AccountAuctionConfig accountAuctionConfig) {
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        new Account(uuid: accountId, config: accountConfig)
    }
}
