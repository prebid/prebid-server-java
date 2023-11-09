package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.ModelGroup
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.model.pricefloors.PriceFloorSchema
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.ExtPrebidPriceFloorEnforcement
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.util.PBSUtils

import static org.mockserver.model.HttpStatusCode.BAD_REQUEST_400
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.pricefloors.MediaType.MULTIPLE
import static org.prebid.server.functional.model.pricefloors.MediaType.VIDEO
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SITE_DOMAIN
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class PriceFloorsSignalingSpec extends PriceFloorsBaseSpec {

    def "PBS should skip signalling for request with rules when ext.prebid.floors.enabled = false in request"() {
        given: "Default BidRequest with disabled floors"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.enabled = requestEnabled
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enabled = accountEnabled
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor
        assert !bidderRequest.ext?.prebid?.floors?.enabled

        and: "PBS should not fetch rules from floors provider"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 0

        where:
        requestEnabled | accountEnabled
        false          | true
        true           | false
    }

    def "PBS should skip signalling for request without rules when ext.prebid.floors.enabled = false in request"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors = new ExtPrebidFloors(enabled: false)
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain bidFloor"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.imp[0].bidFloor

        and: "PBS should not fetch rules from floors provider"
        assert floorsProvider.getRequestCount(bidRequest.app.publisher.id) == 0
    }

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
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue)

        then: "Bidder request bidFloor should correspond to floors provider"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
    }

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
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to request"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == requestFloorValue

        and: "PBS should fetch rules from floors provider"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 1
    }

    def "PBS should not signalling when neither fetched floors nor ext.prebid.floors exist, imp.bidFloor is not defined"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set invalid Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = null
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain bidFloor"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert !bidderRequest.imp[0].bidFloor

        and: "PBS should fetch rules from floors provider"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 1
    }

    def "PBS should make PF signalling when skipRate = #skipRate"() {
        given: "Default BidRequest with bidFloor, bidFloorCur"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].bidFloor = PBSUtils.randomFloorValue
            imp[0].bidFloorCur = USD
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with skipRate"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
            modelGroups[0].currency = USD
            modelGroups[0].skipRate = skipRate
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to floors provider"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
        assert bidderRequest.imp[0].bidFloorCur == floorsResponse.modelGroups[0].currency
        assert !bidderRequest.ext?.prebid?.floors?.skipped

        where:
        skipRate << [0, null]
    }

    def "PBS should not make PF signalling, enforcing when skipRate = 100"() {
        given: "Default BidRequest with bidFloor, bidFloorCur"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].bidFloor = 0.8
            imp[0].bidFloorCur = USD
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with skipRate"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
            modelGroups[0].currency = USD
            modelGroups[0].skipRate = modelGroupSkipRate
            skipRate = dataSkipRate
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to request"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor
        assert bidderRequest.imp[0].bidFloorCur == bidRequest.imp[0].bidFloorCur
        assert bidderRequest.ext?.prebid?.floors?.skipRate == 100
        assert bidderRequest.ext?.prebid?.floors?.skipped

        and: "PBS should not made signalling"
        assert !bidderRequest.imp[0].ext?.prebid?.floors

        where:
        modelGroupSkipRate | dataSkipRate
        100                | 0
        null               | 100
    }

    def "PBS should not emit error when request has more rules than fetch.max-rules"() {
        given: "BidRequest with 2 rules"
        def requestFloorValue = PBSUtils.randomFloorValue
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

        and: "Set Floors Provider response  with status code != 200"
        floorsProvider.setResponse(bidRequest.site.publisher.id, BAD_REQUEST_400)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid.first().price = requestFloorValue
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        and: "Bidder request should contain bidFloor from the request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == requestFloorValue
    }

    def "PBS should not emit error when stored request has more rules than fetch.max-rules for amp request"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with 2 rules "
        def requestFloorValue = PBSUtils.randomFloorValue
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.floors = ExtPrebidFloors.extPrebidFloors
            ext.prebid.floors.data.modelGroups[0].values =
                    [(rule)                                                       : requestFloorValue + 0.1,
                     (new Rule(mediaType: BANNER, country: Country.MULTIPLE).rule): requestFloorValue]
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account with maxRules in the DB"
        def account = getAccountWithEnabledFetch(ampRequest.account as String).tap {
            config.auction.priceFloors.fetch.enabled = false
            config.auction.priceFloors.fetch.maxRules = 1
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest)
        bidResponse.seatbid.first().bid.first().price = requestFloorValue
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = floorsPbsService.sendAmpRequest(ampRequest)

        then: "PBS should not log warning"
        assert !response.ext.warnings

        and: "Bidder request should contain bidFloor from the request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.imp[0].bidFloor == requestFloorValue
    }

    def "PBS should update imp[0].bidFloor when ext.prebid.bidadjustmentfactors is defined"() {
        given: "Pbs with PF configuration with minMaxAgeSec"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.adjustForBidAdjustment = pbsConfigBidAdjustmentFlag
        }
        def pbsService = pbsServiceFactory.getService(FLOORS_CONFIG +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "BidRequest with bidAdjustment"
        def floorsProviderFloorValue = PBSUtils.randomFloorValue
        BigDecimal bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: requestBidAdjustmentFlag))
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment])
        }

        and: "Account with adjustForBidAdjustment in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id).tap {
            config.auction.priceFloors.adjustForBidAdjustment = accountBidAdjustmentFlag
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)

        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(pbsService, bidRequest, floorsProviderFloorValue)

        then: "Bidder request bidFloor should be update according to bidAdjustment"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorsProviderFloorValue / bidAdjustment
            imp[0].ext.prebid.floors.floorRuleValue == floorsProviderFloorValue
            imp[0].ext.prebid.floors.floorValue == imp[0].bidFloor
        }

        where:
        pbsConfigBidAdjustmentFlag | requestBidAdjustmentFlag | accountBidAdjustmentFlag
        true                       | true                     | null
        true                       | null                     | null
        false                      | null                     | true
    }

    def "PBS should not update imp[0].bidFloor when bidadjustment is disallowed"() {
        given: "Pbs with PF configuration with adjustForBidAdjustment"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.adjustForBidAdjustment = pbsConfigBidAdjustmentFlag
        }
        def pbsService = pbsServiceFactory.getService(FLOORS_CONFIG +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Default BidRequest"
        def floorsProviderFloorValue = 0.8
        def bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: requestBidAdjustmentFlag))
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment])
        }

        and: "Account in the DB"
        def accountId = bidRequest.app.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.adjustForBidAdjustment = accountBidAdjustmentFlag
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(pbsService, bidRequest, floorsProviderFloorValue)

        then: "Bidder request bidFloor should be changed"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorRuleValue == floorsProviderFloorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorValue == floorsProviderFloorValue

        where:
        pbsConfigBidAdjustmentFlag | requestBidAdjustmentFlag | accountBidAdjustmentFlag
        false                      | false                    | null
        true                       | null                     | false
    }

    def "PBS should choose most aggressive adjustment when request contains multiple media-types"() {
        given: "BidRequest with bidAdjustment"
        def bidAdjustment = PBSUtils.roundDecimal(PBSUtils.getRandomDecimal(0, 10), 1)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first().video = Video.defaultVideo
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: true))
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(
                    mediaTypes: [(BidAdjustmentMediaType.BANNER): [(GENERIC): bidAdjustment],
                                 (BidAdjustmentMediaType.VIDEO) : [(GENERIC): bidAdjustment + 0.1]])
        }

        and: "Account with adjustForBidAdjustment in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should be update according to bidAdjustment"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == getAdjustedValue(floorValue, bidAdjustment)
        assert bidderRequest.imp[0].ext.prebid.floors.floorRuleValue == floorValue
        assert bidderRequest.imp[0].ext.prebid.floors.floorValue == bidderRequest.imp[0].bidFloor
    }

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
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups << ModelGroup.modelGroup
            modelGroups.first().values = [(rule): floorValue + 0.1]
            modelGroups.last().schema = new PriceFloorSchema(fields: [SITE_DOMAIN])
            modelGroups.last().values = [(new Rule(siteDomain: domain).rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(bidRequest)

        then: "Bidder request should contain 1 modelGroup"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.ext?.prebid?.floors?.data?.modelGroups?.size() == 1
    }

    def "PBS should choose appropriate rule for each imp when request contains multiple imps"() {
        given: "Default BidRequest with multiple imp: banner, video"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp << Imp.getDefaultImpression(MediaType.VIDEO)
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def bannerFloorValue = PBSUtils.randomFloorValue
        def videoFloorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            modelGroups[0].values = [(new Rule(mediaType: BANNER).rule): bannerFloorValue,
                                     (new Rule(mediaType: VIDEO).rule) : videoFloorValue]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain appropriate bidFloor for each imp"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp.first().bidFloor == bannerFloorValue
        assert bidderRequest.imp.last().bidFloor == videoFloorValue
    }
}
