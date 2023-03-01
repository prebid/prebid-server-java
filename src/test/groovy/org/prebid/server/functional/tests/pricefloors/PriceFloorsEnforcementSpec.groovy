package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.ExtPrebidPriceFloorEnforcement
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class PriceFloorsEnforcementSpec extends PriceFloorsBaseSpec {

    def "PBS should make PF enforcement for amp request when stored request #descriprion rules"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request #descriprion rules "
        def alias = "alias"
        def bidderParam = PBSUtils.randomNumber
        def bidderAliasParam = PBSUtils.randomNumber
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.aliases = [(alias): GENERIC]
            imp[0].ext.prebid.bidder.alias = new Generic(firstParam: bidderAliasParam)
            imp[0].ext.prebid.bidder.generic.firstParam = bidderParam
            ext.prebid.floors = floors
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account with enabled fetch and fetch.url in the DB"
        def account = getAccountWithEnabledFetch(ampRequest.account as String)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(ampRequest.account as String, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(ampRequest, floorValue)

        and: "Bid response for generic bidder"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid.first().bid.first().price = floorValue
        }
        bidder.setResponse(bidder.getRequest(bidderParam as String, "imp[0].ext.bidder.firstParam"), bidResponse)

        and: "Bid response for generic bidder alias"
        def lowerPrice = floorValue - 0.1
        def aliasBidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid.first().bid.first().price = lowerPrice
        }
        bidder.setResponse(bidder.getRequest(bidderAliasParam as String, "imp[0].ext.bidder.firstParam"), aliasBidResponse)

        when: "PBS processes amp request"
        def response = floorsPbsService.sendAmpRequest(ampRequest)

        then: "PBS should suppress bids lower than floorRuleValue"
        def bidPrice = getRoundedTargetingValueWithDefaultPrecision(floorValue)
        verifyAll(response) {
            targeting["hb_pb_generic"] == bidPrice
            targeting["hb_pb"] == bidPrice
            !targeting["hb_pb_alias"]
        }

        and: "PBS should log warning about bid suppression"
        assert response.ext?.warnings[ErrorType.ALIAS]*.code == [6]
        assert response.ext?.warnings[ErrorType.ALIAS]*.message ==
                ["Bid with id '${aliasBidResponse.seatbid[0].bid[0].id}' was rejected by floor enforcement: " +
                         "price $lowerPrice is below the floor ${floorValue.stripTrailingZeros()}" as String]
        assert response.ext?.warnings[ErrorType.ALIAS]*.impIds[0][0] == ampStoredRequest.imp[0].id

        where:
        descriprion       | floors
        "doesn't contain" | null
        "contains"        | ExtPrebidFloors.extPrebidFloors
    }

    def "PBS should reject bids when ext.prebid.floors.enforcement.enforcePBS = #enforcePbs"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: enforcePbs))
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest, floorValue)

        and: "Bid response with 2 bids: price = floorValue, price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid[0].price = floorValue
            seatbid.first().bid[1].price = floorValue - 0.1
            seatbid.first().bid[2].price = floorValue - 0.2
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid?.first()?.bid?.collect { it.price } == [floorValue]

        and: "PBS should log warning about suppression all bids below the floor value "
        def impId = bidRequest.imp[0].id
        assert response.ext?.warnings[ErrorType.GENERIC]*.code == [6, 6]
        assert response.ext?.warnings[ErrorType.GENERIC]*.message ==
                ["Bid with id '${bidResponse.seatbid[0].bid[1].id}' was rejected by floor enforcement: " +
                         "price ${bidResponse.seatbid[0].bid[1].price} is below the floor ${floorValue.stripTrailingZeros()}" as String,
                 "Bid with id '${bidResponse.seatbid[0].bid[2].id}' was rejected by floor enforcement: " +
                         "price ${bidResponse.seatbid[0].bid[2].price} is below the floor ${floorValue.stripTrailingZeros()}" as String]
        assert response.ext?.warnings[ErrorType.GENERIC]*.impIds[0].first() == impId
        assert response.ext?.warnings[ErrorType.GENERIC]*.impIds[1].first() == impId

        where:
        enforcePbs << [true, null]
    }

    def "PBS should not reject bids when ext.prebid.floors.enforcement.enforcePBS = false"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: false))
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest, floorValue)

        and: "Bid response with 2 bids: price = floorValue, price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid?.first()?.bid?.collect { it.price }?.sort() ==
                bidResponse.seatbid.first().bid.collect { it.price }.sort()
    }

    def "PBS should make PF enforcement when imp[].bidfloor/cur comes from request"() {
        given: "Default BidRequest with floors"
        def floorValue = PBSUtils.randomFloorValue
        def floorCur = USD
        def bidRequest = bidRequestWithFloors.tap {
            cur = [floorCur]
            imp[0].bidFloor = floorValue
            imp[0].bidFloorCur = floorCur
            ext.prebid.floors = null
        }

        and: "Account with disabled fetch in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        and: "Bid response with 2 bids: price = floorValue, price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
            cur = floorCur
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid?.first()?.bid?.collect { it.price } == [floorValue]
    }

    def "PBS should prefer ext.prebid.floors for PF enforcement"() {
        given: "Default BidRequest with floors"
        def floorValue = PBSUtils.randomFloorValue
        def floorCur = USD
        def bidRequest = bidRequestWithFloors.tap {
            cur = [floorCur]
            imp[0].bidFloor = PBSUtils.randomFloorValue
            imp[0].bidFloorCur = floorCur
            ext.prebid.floors.floorMin = floorValue
            ext.prebid.floors.data.modelGroups[0].values = [(rule): floorValue]
            ext.prebid.floors.data.modelGroups[0].currency = floorCur
        }

        and: "Account with disabled fetch in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        and: "Bid response with 2 bids: price = floorValue, price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
            cur = floorCur
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert response.seatbid?.first()?.bid?.collect { it.price } == [floorValue]
    }

    def "PBS should suppress deal that are below the matched floor when enforce-deal-floors = true"() {
        given: "Pbs with PF configuration with enforceDealFloors"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.enforceDealFloors = false
        }
        def pbsService = pbsServiceFactory.getService(floorsConfig +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(preferDeals: true)
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(floorDeals: true))
        }

        and: "Account with enabled fetch, fetch.url,enforceDealFloors in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceDealFloors = true
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(pbsService, bidRequest, floorValue)

        and: "Bid response with 2 bids: bid.price = floorValue, dealBid.price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().dealid = PBSUtils.randomNumber
            seatbid.first().bid.first().price = floorValue - 0.1
            seatbid.first().bid.last().price = floorValue
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should suppress bid lower than floorRuleValue"
        assert response.seatbid?.first()?.bid?.collect { it.id } == [bidResponse.seatbid.first().bid.last().id]
        assert response.seatbid.first().bid.collect { it.price } == [floorValue]
    }

    def "PBS should not suppress deal that are below the matched floor according to ext.prebid.floors.enforcement.enforcePBS"() {
        given: "Pbs with PF configuration with enforceDealFloors"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.enforceDealFloors = pbsConfigEnforceDealFloors
        }
        def pbsService = pbsServiceFactory.getService(floorsConfig +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Default basic BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(preferDeals: true)
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(floorDeals: floorDeals, enforcePbs: enforcePbs))
        }

        and: "Account with enabled fetch, fetch.url, enforceDealFloors in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceDealFloors = accountEnforceDealFloors
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(pbsService, bidRequest, floorValue)

        and: "Bid response with 2 bids: bid.price = floorValue, dealBid.price < floorValue"
        def dealBidPrice = floorValue - 0.1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().dealid = PBSUtils.randomNumber
            seatbid.first().bid.first().price = dealBidPrice
            seatbid.first().bid.last().price = floorValue
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with deal"
        assert response.seatbid?.first()?.bid?.first()?.id == bidResponse.seatbid.first().bid.first().id
        assert response.seatbid.first().bid.collect { it.price } == [dealBidPrice]

        where:
        pbsConfigEnforceDealFloors | enforcePbs | accountEnforceDealFloors | floorDeals
        true                       | null       | false                    | true
        false                      | false      | true                     | true
        false                      | null       | true                     | false
    }

    def "PBS should suppress any bids below the matched floor when fetch.enforce-floors-rate = 100 in account config"() {
        given: "Pbs with PF configuration with minMaxAgeSec"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.enforceFloorsRate = pbsConfigEnforceRate
        }
        def pbsService = pbsServiceFactory.getService(floorsConfig +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforceRate: requestEnforceRate))
        }

        and: "Account with enabled fetch, fetch.url, enforceFloorsRate in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceFloorsRate = accountEnforceRate
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(pbsService, bidRequest, floorValue)

        and: "Bid response with 2 bids: price = floorValue, price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue - 0.1
            seatbid.first().bid.last().price = floorValue
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should suppress inappropriate bids"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.id == bidResponse.seatbid.first().bid.last().id
        assert response.seatbid.first().bid.collect { it.price } == [floorValue]

        where:
        pbsConfigEnforceRate             | requestEnforceRate | accountEnforceRate
        PBSUtils.getRandomNumber(0, 100) | null               | 100
        100                              | 100                | null
        100                              | null               | null
    }

    def "PBS should not suppress any bids below the matched floor when fetch.enforce-floors-rate = 0 in account config"() {
        given: "Pbs with PF configuration with minMaxAgeSec"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.enforceFloorsRate = pbsConfigEnforceFloorsRate
        }
        def pbsService = pbsServiceFactory.getService(floorsConfig +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforceRate: enforceRate))
        }

        and: "Account with enabled fetch, fetch.url, enforceFloorsRate in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceFloorsRate = accountEnforceFloorsRate
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(pbsService, bidRequest, floorValue)

        and: "Bid response with 2 bids: price = floorValue, price < floorValue"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue - 0.1
            seatbid.first().bid.last().price = floorValue
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not suppress bids"
        assert response.seatbid?.first()?.bid?.size() == 2
        assert response.seatbid.first().bid.collect { it.price } == [floorValue, floorValue - 0.1]

        where:
        pbsConfigEnforceFloorsRate       | enforceRate | accountEnforceFloorsRate
        PBSUtils.getRandomNumber(0, 100) | null        | 0
        0                                | 0           | null
        PBSUtils.getRandomNumber(0, 100) | 100         | 0
        PBSUtils.getRandomNumber(0, 100) | 0           | 100
        0                                | null        | null
    }

    def "PBS should suppress any bids below the adjusted floor value when ext.prebid.bidadjustmentfactors is defined"() {
        given: "BidRequest with bidAdjustment"
        def floorValue = PBSUtils.randomFloorValue
        BigDecimal bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: true))
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment])
        }

        and: "Account with adjustForBidAdjustment in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest)

        and: "Bid response with 2 bids: bid.price = floorValue, dealBid.price < floorValue"
        def adjustedFloorValue = floorValue / bidAdjustment
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = adjustedFloorValue
            seatbid.first().bid.last().price = adjustedFloorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should suppress bid lower than floorRuleValue"
        assert response.seatbid?.first()?.bid?.collect { it.id } == [bidResponse.seatbid.first().bid.first().id]
        assert response.seatbid.first().bid.collect { it.price } == [floorValue]
    }

    def "PBS floor enforcement shouldn't fail when bidder request was not made"() {
        given: "BidRequestWithFloors with Stored Auction Response and Floors enabled = #bidRequestFloors"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = getBidRequestWithFloors().tap {
            ext.prebid.floors.enabled = bidRequestFloors
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Request shouldn't fail with an error"
        def thrown = noExceptionThrown()
        assert !thrown

        and: "PBS shouldn't log errors"
        assert !response.ext.errors

        where:
        bidRequestFloors << [true, false]
    }

    def "PBS floors shouldn't enforce when floors property disabled"() {
        given: "BidRequestWithFloors with floors"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            ext.prebid.floors = new ExtPrebidFloors(enabled: bidRequestFloors)
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enabled = accountConfigFloors
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): PBSUtils.randomFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest)

        and: "Bid response with bid price"
        def bidPrice = floorValue - 0.1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid.first().price = bidPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't suppress bid lower than floorRuleValue"
        assert response.seatbid.first().bid.collect { it.price } == [bidPrice]

        where:
        bidRequestFloors | accountConfigFloors
        false            | true
        true             | false
        false            | false
    }
}
