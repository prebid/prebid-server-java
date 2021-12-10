package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.pricefloors.PriceFloorEnforcement
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.ExtPrebidPriceFloorEnforcement
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class PriceFloorsEnforcementSpec extends PriceFloorsBaseSpec {

    @PendingFeature
    def "PBS should make PF enforcement for amp request when stored request #descriprion rules"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request #descriprion rules "
        def alias = "genericAlias"
        def bidderParam = PBSUtils.randomNumber
        def bidderAliasParam = PBSUtils.randomNumber
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.aliases = [(alias): GENERIC]
            imp[0].ext.prebid.bidder.genericAlias = new Generic(firstParam: bidderParam)
            imp[0].ext.prebid.bidder.generic.firstParam = bidderAliasParam
            site.publisher.id = ampRequest.account
            ext.prebid.floors = floors
        }
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account with enabled fetch and fetch.url in the DB"
        def account = getAccountWithEnabledFetch(ampStoredRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(ampStoredRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid.first().bid.first().price = floorValue
        }
        bidder.setResponse(bidder.getRequest("imp[0].ext.bidder.firstParam", bidderParam as String), bidResponse)

        def bidResponse2 = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid.first().bid.first().price = floorValue + 2
        }
        bidder.setResponse(bidder.getRequest("imp[0].ext.bidder.firstParam", bidderAliasParam as String), bidResponse2)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAmpRequest(ampRequest)

        then: "PBS should fetch data"
       // TODO targetting

        where:
        descriprion       | floors
        "doesn't contain" | null
        "contains"        | ExtPrebidFloors.extPrebidFloors
    }

    @PendingFeature
    def "PBS should reject bids when ext.prebid.floors.enforcement.enforcePBS = #enforcePbs"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: enforcePbs))
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid.first().bid.collect { it.price } == [floorValue]

        where:
        enforcePbs << [true, null]
    }

    @PendingFeature
    def "PBS should not reject bids when ext.prebid.floors.enforcement.enforcePBS = false"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: false))
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid.first().bid.collect { it.price }.sort() ==
                bidResponse.seatbid.first().bid.collect { it.price }.sort()
    }

    @PendingFeature
    def "PBS should make PF enforcement when imp[].bidfloor/cur comes from request"() {
        given: "Default BidRequest with price"
        def bidRequest = bidRequestWithFloors

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid.first().bid.collect { it.price } == [floorValue]
    }

    @PendingFeature
    def "PBS should suppress deal that are below the matched floor when enforce-deal-floors = true"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(preferdeals: true)
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceDealFloors = true
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
            enforcement = new PriceFloorEnforcement(floorDeals: true)
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().dealid = PBSUtils.randomNumber
            seatbid.first().bid.first().price = floorValue - 0.1
            seatbid.first().bid.last().price = floorValue
        }
        def winnerBidId = bidResponse.seatbid.first().bid.last().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with deal"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.id == winnerBidId
        assert response.seatbid?.first()?.bid?.first()?.price == floorValue
    }

    @PendingFeature
    def "PBS should not suppress deal that are below the matched floor according to ext.prebid.floors.enforcement.enforcePBS"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(preferdeals: true)
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: enforcePbs))
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceDealFloors = false
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
            enforcement = new PriceFloorEnforcement(floorDeals: floorDeals)
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def dealBidPrice = floorValue - 0.1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().dealid = PBSUtils.randomNumber
            seatbid.first().bid.first().price = dealBidPrice
            seatbid.first().bid.last().price = floorValue
        }
        def winnerBidId = bidResponse.seatbid.first().bid.first().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with deal"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.id == winnerBidId
        assert response.seatbid?.first()?.bid?.first()?.price == dealBidPrice

        where:
        enforcePbs | enforceDealFloors | floorDeals
        null       | false             | true
        false      | true              | true
        null       | true              | false
    }

    @PendingFeature
    def "PBS should suppress any bids below the matched floor when fetch.enforce-floors-rate = 100 in account config"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforceRate: enforceRate))
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceFloorsRate = enforceFloorsRate
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
            enforcement = new PriceFloorEnforcement(floorDeals: true)
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue - 0.1
            seatbid.first().bid.last().price = floorValue
        }
        def winnerBidId = bidResponse.seatbid.first().bid.last().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with deal"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.id == winnerBidId
        assert response.seatbid?.first()?.bid?.first()?.price == floorValue

        and: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.floors.enforcement.enforcePbs

        where:
        enforceRate | enforceFloorsRate
        null        | 100
        100         | null
    }

    @PendingFeature
    def "PBS should suppress any bids below the matched floor when fetch.enforce-floors-rate = 0 in account config"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforceRate: enforceRate))
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceFloorsRate = enforceFloorsRate
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
            enforcement = new PriceFloorEnforcement(floorDeals: true)
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue - 0.1
            seatbid.first().bid.last().price = floorValue
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with deal"
        assert response.seatbid?.first()?.bid?.size() == 2
        assert response.seatbid.first().bid.collect { it.price } == [floorValue, floorValue - 0.1]

        and: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.floors.enforcement.enforcePbs

        where:
        enforceRate | enforceFloorsRate
        null        | 0
        0           | null
        100         | 0
        0           | 100
    }
}
