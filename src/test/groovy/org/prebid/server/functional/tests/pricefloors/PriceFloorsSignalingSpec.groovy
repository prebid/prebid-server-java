package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.ModelGroup
import org.prebid.server.functional.model.pricefloors.PriceFloorSchema
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.pricefloors.MediaType.MULTIPLE
import static org.prebid.server.functional.model.pricefloors.MediaType.VIDEO
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SITE_DOMAIN
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class PriceFloorsSignalingSpec extends PriceFloorsBaseSpec {

    @PendingFeature
    def "PBS should skip signalling for request with rules when ext.prebid.floors.enabled = false in request"() {
        given: "Default BidRequest with disabled floors"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.enabled = false
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should skip signalling for request without rules when ext.prebid.floors.enabled = false in request"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.getDefaultBidRequest(APP)

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain bidFloor"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should prefer fetched data when ext.prebid.floors is defined in request"() {
        given: "Default BidRequest with floors"
        def requestFloorValue = 0.8
        def floorsProviderFloorValue = requestFloorValue + 0.1
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values = [(rule): requestFloorValue]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to floors provider"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
    }

    @PendingFeature
    def "PBS should take data from ext.prebid.floors when fetched data is invalid"() {
        given: "Default BidRequest with floors"
        def requestFloorValue = 0.8
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values =
                    [(new Rule(mediaType: MULTIPLE, country: Country.MULTIPLE).rule): requestFloorValue]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set invalid Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == requestFloorValue
    }

    @PendingFeature
    def "PBS should not signalling and enforcement when neither fetched floors nor ext.prebid.floors exist"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set invalid Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain bidFloor"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should make PF signalling when skipRate = 100"() {
        given: "Default BidRequest with bidFloor, bidFloorCur"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].bidFloor = 0.8
            imp[0].bidFloorCur = "USD"
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with skipRate"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
            data.modelGroups[0].skipRate = 100
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to floors provider"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
        assert bidderRequest.imp[0].bidFloorCur == floorsResponse.data.modelGroups[0].currency
    }

    @PendingFeature
    def "PBS should assume modelWeight = 1 when modelWeight isn't provided"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.getDefaultBidRequest(APP)

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response without modelWeight"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].modelWeight = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request modelWeight should correspond to default model weight"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
        assert bidderRequest.ext.prebid.floors.data.modelGroups[0].modelWeight == DEFAULT_MODEL_WEIGHT
    }

    @PendingFeature
    def "PBS should reject entire ruleset when modelWeight from floors provider is invalid"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups << ModelGroup.modelGroup
            data.modelGroups.first().values = [(rule): floorValue + 0.1]
            data.modelGroups.first().modelWeight = invalidModelWeight
            data.modelGroups.last().values = [(rule): floorValue]
            data.modelGroups.last().modelWeight = modelWeight
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to rule from valid modelGroup"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        invalidModelWeight << [0, -1, 1000000]
    }

    @PendingFeature
    def "PBS should reject entire ruleset when skipRate from floors provider is invalid"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups << ModelGroup.modelGroup
            data.modelGroups.first().values = [(rule): floorValue + 0.1]
            data.modelGroups.first().skipRate = invalidSkipRate
            data.modelGroups.last().values = [(rule): floorValue]
            data.modelGroups.last().skipRate = 100
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to rule from valid modelGroup"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        invalidSkipRate << [-1, 101]
    }

    @PendingFeature
    def "PBS should not emit error when request has more rules than fetch.max-rules"() {
        given: "BidRequest with 2 rules"
        def requestFloorValue = randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values =
                    [(rule)                                                       : requestFloorValue + 0.1,
                     (new Rule(mediaType: BANNER, country: Country.MULTIPLE).rule): requestFloorValue]
        }

        and: "Account with maxRules in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.fetch.enabled = false
            config.auction.priceFloors.fetch.maxRules = 1
        }
        accountDao.save(account)

        when: "PBS processes auction request"
       def response =  floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not log warning"
        assert !response.ext.warnings

        and: "Bidder request should contain bidFloor from the request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == requestFloorValue
    }

    @PendingFeature
    def "PBS should update imp[0].ext.prebid.floors.floorRuleValue when ext.prebid.bidadjustmentfactors is defined"() {
        given: "BidRequest with bidAdjustment"
        def floorsProviderFloorValue = 0.8
        def bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors.enforcement.bidAdjustment = requestBidAdjustmentFlag
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment as BigDecimal])
        }

        and: "Account with adjustForBidAdjustment in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.adjustForBidAdjustment = accountBidAdjustmentFlag
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should be update according to bidAdjustment"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue / bidAdjustment
        assert bidderRequest.imp[0].ext.prebid.floors.floorRuleValue == floorsProviderFloorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorValue == floorsProviderFloorValue / bidAdjustment

        where:
        requestBidAdjustmentFlag | accountBidAdjustmentFlag
        true                     | null
        null                     | null
        null                     | true
    }

    @PendingFeature
    def "PBS should not update imp[0].ext.prebid.floors.floorRuleValue when bidadjustment is disallowed"() {
        given: "Default BidRequest"
        def floorsProviderFloorValue = 0.8
        def bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors.enforcement.bidAdjustment = requestBidAdjustmentFlag
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment as BigDecimal])
        }

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.adjustForBidAdjustment = accountBidAdjustmentFlag
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should be changed"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorRuleValue == floorsProviderFloorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorValue == floorsProviderFloorValue

        where:
        requestBidAdjustmentFlag | accountBidAdjustmentFlag
        false                    | null
        null                     | false
    }

    @PendingFeature
    def "PBS should remove non-selected models"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups << ModelGroup.modelGroup
            data.modelGroups.first().values = [(rule): floorValue + 0.1]
            data.modelGroups.last().schema = new PriceFloorSchema(fields: [SITE_DOMAIN])
            data.modelGroups.last().values = [(new Rule(siteDomain: domain).rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain 1 modelGroup"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.floors.data.modelGroups.size() == 1
    }

    @PendingFeature
    def "PBS should choose appropriate rule for each imp when request contains multiple imps"() {
        given: "Default BidRequest"
        def bannerFloorValue = randomFloorValue
        def videoFloorValue = randomFloorValue
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp << Imp.defaultImpression
            imp.last().banner.format = [new Format(w: PBSUtils.randomNumber, h: PBSUtils.randomNumber)]
        }

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            data.modelGroups[0].values = [(new Rule(mediaType: BANNER).rule): bannerFloorValue]
            data.modelGroups[0].values = [(new Rule(mediaType: VIDEO).rule): videoFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain appropriate bidFloor for each imp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.first().bidFloor == bannerFloorValue
        assert bidderRequest.imp.last().bidFloor == videoFloorValue
    }
}
