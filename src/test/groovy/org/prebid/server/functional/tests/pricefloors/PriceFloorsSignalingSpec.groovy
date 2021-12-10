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

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.pricefloors.MediaType.MULTIPLE
import static org.prebid.server.functional.model.pricefloors.MediaType.VIDEO
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SITE_DOMAIN

class PriceFloorsSignalingSpec extends PriceFloorsBaseSpec {

    @PendingFeature
    def "PBS should skip signalling for request with rules when ext.prebid.floors.enabled = false in request"() {
        given: "Default BidRequest with price"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.enabled = false
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should skip signalling for request without rules when ext.prebid.floors.enabled = false in request"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == null
    }

    @PendingFeature
    def "PBS should prefer fetched data when ext.prebid.floors is defined in request"() {
        given: "Default BidRequest with price"
        def requestFloorValue = 0.8
        def floorsProviderFloorValue = requestFloorValue + 0.1
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values = [(rule): requestFloorValue]
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"

        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
    }

    @PendingFeature
    def "PBS should take data from ext.prebid.floors when fetched data is invalid"() {
        given: "Default BidRequest with price"
        def requestFloorValue = 0.8
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values =
                    [(new Rule(mediaType: MULTIPLE, country: Country.MULTIPLE).rule): requestFloorValue]
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set invalid Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == requestFloorValue
    }

    @PendingFeature
    def "PBS should not signalling and enforcement when neither fetched floors nor ext.prebid.floors exist"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set invalid Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        assert floorsProvider.getRequestCount(bidRequest.id) == 0

        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == null
    }

    @PendingFeature
    def "PBS should pass location: noData when neither fetched floors nor ext.prebid.floors exist !!!"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set invalid Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
//        assert
    }

    @PendingFeature
    def "PBS should skip floors when skipRate = 100"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].bidFloor = 0.8
            imp[0].bidFloorCur = "USD"
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"

        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].skipRate = 100
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        assert floorsProvider.getRequestCount(bidRequest.id) == 0

        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor
        assert bidderRequest.imp[0].bidFloorCur == bidRequest.imp[0].bidFloorCur
    }

    @PendingFeature
    def "PBS should assume modelWeight = 1 when modelWeight isn't provided"() {
        given: "Default BidRequest with price"
        def floorsProviderFloorValue = 0.8
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values = [(rule): floorsProviderFloorValue + 0.1]
            ext.prebid.floors.data.modelGroups[0].modelWeight = null
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
        assert bidderRequest.ext.prebid.floors.data.modelGroups[0].modelWeight == DEFAULT_MODEL_WEIGHT
    }

    @PendingFeature
    def "PBS should reject entire ruleset when modelWeight is invalid"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
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

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        invalidModelWeight << [0, -1, 1000000]
    }

    @PendingFeature
    def "PBS should not emit error when request has more rules than fetch.max-rules"() {
        given: "Test start time"
        def startTime = Instant.now()
        def requestFloorValue = 0.8

        and: "Default BidRequest"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups[0].values =
                    [(rule)                                                       : requestFloorValue + 0.1,
                     (new Rule(mediaType: BANNER, country: Country.MULTIPLE).rule): requestFloorValue]
        }

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.fetch.maxRules = 1
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 0

        and: ""
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == requestFloorValue
    }

    @PendingFeature
    def "PBS should update imp[0].ext.prebid.floors.floorRuleValue when ext.prebid.bidadjustmentfactors is defined"() {
        given: "Default BidRequest"
        def floorsProviderFloorValue = 0.8
        def bidAdjustment = 0.1
        def bidRequest = bidRequestWithFloors.tap {
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

        then: "PBS log should contain request to allowed adapter"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorRuleValue == floorsProviderFloorValue / bidAdjustment
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
        def bidRequest = bidRequestWithFloors.tap {
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

        then: "PBS log should contain request to allowed adapter"
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
        given: "BidRequest with device.ua"
        def domain = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
        }

        and: "Account in the DB"
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
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.floors.data.modelGroups.size() == 1
    }

    @PendingFeature
    def "PBS should choose appropriate rule for each imp when request contains multiple imps"() {
        given: "Default BidRequest"
        def bannerFloorValue = randomFloorValue
        def videoFloorValue = randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
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
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should contain request to allowed adapter"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.first().bidFloor == bannerFloorValue
        assert bidderRequest.imp.last().bidFloor == videoFloorValue
    }
}
