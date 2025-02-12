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

import java.time.Instant

import static org.mockserver.model.HttpStatusCode.BAD_REQUEST_400
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.pricefloors.MediaType.MULTIPLE
import static org.prebid.server.functional.model.pricefloors.MediaType.VIDEO
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SITE_DOMAIN
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class PriceFloorsSignalingSpec extends PriceFloorsBaseSpec {

    private static final Closure<String> INVALID_CONFIG_METRIC = { account -> "alerts.account_config.${account}.price-floors" }
    private static final int MAX_SCHEMA_DIMENSIONS_SIZE = 1
    private static final int MAX_RULES_SIZE = 1

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
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue, floorsPbsService)

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
        given: "BidRequest with bidAdjustment"
        def floorsProviderFloorValue = PBSUtils.randomFloorValue
        BigDecimal bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: requestBidAdjustmentFlag))
            it.ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment])
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
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue / bidAdjustment, floorsPbsService)

        then: "Bidder request bidFloor should be update according to bidAdjustment"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue / bidAdjustment

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors

        where:
        requestBidAdjustmentFlag | accountBidAdjustmentFlag
        true                     | null
        null                     | null
        null                     | true
    }

    def "PBS should not update imp[0].bidFloor when bidadjustment is disallowed"() {
        given: "Default BidRequest"
        def floorsProviderFloorValue = 0.8
        def bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: requestBidAdjustmentFlag))
            it.ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment])
        }

        and: "Account in the DB"
        def accountId = bidRequest.app.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            it.config.auction.priceFloors.adjustForBidAdjustment = accountBidAdjustmentFlag
            it.config.auction.priceFloors.adjustForBidAdjustmentSnakeCase = accountBidAdjustmentFlagSnakeCase
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            it.modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue, floorsPbsService)

        then: "Bidder request bidFloor should be changed"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors

        where:
        requestBidAdjustmentFlag | accountBidAdjustmentFlag | accountBidAdjustmentFlagSnakeCase
        false                    | null                     | false
        null                     | false                    | null
        false                    | null                     | false
        null                     | false                    | null
    }

    def "PBS should priorities account config over default account config and update imp[0].bidFloor"() {
        given: "Pbs with PF configuration with adjustForBidAdjustment"
        def accountConfig = defaultAccountConfigSettings.tap {
            it.auction.priceFloors.tap {
                it.adjustForBidAdjustment = false
                adjustForBidAdjustmentSnakeCase = null
            }
        }
        def pbsConfig = FLOORS_CONFIG +
                ["settings.default-account-config": encode(accountConfig)]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "BidRequest with bidAdjustment"
        def floorsProviderFloorValue = PBSUtils.randomFloorValue
        BigDecimal bidAdjustment = 0.1
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(bidAdjustment: null))
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustment])
        }
        and: "Account with adjustForBidAdjustment in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id).tap {
            config.auction.priceFloors.adjustForBidAdjustment = null
            config.auction.priceFloors.adjustForBidAdjustmentSnakeCase = true
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)
        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue / bidAdjustment, pbsService)

        then: "Bidder request bidFloor should be update according to bidAdjustment"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue / bidAdjustment

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should choose most aggressive adjustment when request contains multiple media-types"() {
        given: "BidRequest with bidAdjustment"
        def bidAdjustment = PBSUtils.roundDecimal(PBSUtils.getRandomDecimal(0.1, 10), 1)
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

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors
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

    def "PBS shouldn't emit errors when request schema.fields than floor-config.max-schema-dims"() {
        given: "Bid request with schema 2 fields"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.maxSchemaDims = PBSUtils.getRandomNumber(2)
        }

        and: "Account with maxSchemaDims in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't log a errors"
        assert !response.ext?.errors
    }

    def "PBS should emit errors when request has more rules than price-floor.max-rules"() {
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
            config.auction.priceFloors.maxRules = maxRules
            config.auction.priceFloors.maxRulesSnakeCase = maxRulesSnakeCase
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid.first().price = requestFloorValue
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a errors"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor rules number ${getRuleSize(bidRequest)} exceeded its maximum number ${MAX_RULES_SIZE}"]

        where:
        maxRules       | maxRulesSnakeCase
        MAX_RULES_SIZE | null
        null           | MAX_RULES_SIZE
    }

    def "PBS should emit errors when request has more schema.fields than floor-config.max-schema-dims"() {
        given: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Account with maxSchemaDims in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.maxSchemaDims = maxSchemaDims
            config.auction.priceFloors.maxSchemaDimsSnakeCase = maxSchemaDimsSnakeCase
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a errors"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor schema dimensions ${getSchemaSize(bidRequest)} exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"]

        where:
        maxSchemaDims              | maxSchemaDimsSnakeCase
        MAX_SCHEMA_DIMENSIONS_SIZE | null
        null                       | MAX_SCHEMA_DIMENSIONS_SIZE
    }

    def "PBS should emit errors when request has more schema.fields than default-account.max-schema-dims"() {
        given: "Floor config with default account"
        def accountConfig = getDefaultAccountConfigSettings().tap {
            auction.priceFloors.maxSchemaDims = MAX_SCHEMA_DIMENSIONS_SIZE
        }
        def pbsFloorConfig = GENERIC_ALIAS_CONFIG + ["price-floors.enabled"           : "true",
                                                     "settings.default-account-config": encode(accountConfig)]

        and: "Prebid server with floor config"
        def floorsPbsService = pbsServiceFactory.getService(pbsFloorConfig)

        and: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Account with maxSchemaDims in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.maxSchemaDims = PBSUtils.randomNegativeNumber
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a errors"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor schema dimensions ${getSchemaSize(bidRequest)} " +
                         "exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"]

        and: "Metric alerts.account_config.ACCOUNT.price-floors should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[INVALID_CONFIG_METRIC(bidRequest.accountId) as String] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsFloorConfig)
    }

    def "PBS should emit errors when request has more schema.fields than default-account.fetch.max-schema-dims"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Floor config with default account"
        def accountConfig = getDefaultAccountConfigSettings().tap {
            it.auction.priceFloors.fetch.enabled = true
            it.auction.priceFloors.fetch.url = BASIC_FETCH_URL + bidRequest.site.publisher.id
            it.auction.priceFloors.fetch.maxSchemaDims = MAX_SCHEMA_DIMENSIONS_SIZE
            it.auction.priceFloors.maxSchemaDims = null
        }
        def pbsFloorConfig = GENERIC_ALIAS_CONFIG + ["price-floors.enabled"           : "true",
                                                     "settings.default-account-config": encode(accountConfig)]

        and: "Prebid server with floor config"
        def floorsPbsService = pbsServiceFactory.getService(pbsFloorConfig)

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        and: "Account with maxSchemaDims in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.fetch.maxSchemaDims = PBSUtils.randomNegativeNumber
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, BASIC_FETCH_URL + accountId)
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains("Failed to fetch price floor from provider for fetch.url: " +
                "'$BASIC_FETCH_URL$accountId', account = $accountId with a reason : Price floor schema dimensions ${getSchemaSize(bidRequest)} " +
                "exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}")

        and: "Metric alerts.account_config.ACCOUNT.price-floors should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[INVALID_CONFIG_METRIC(bidRequest.accountId) as String] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsFloorConfig)
    }

    def "PBS should emit errors when request has more schema.fields than fetch.max-schema-dims"() {
        given: "Default BidRequest with floorMin"
        def bidRequest = bidRequestWithFloors

        and: "Account with disabled fetch in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.accountId).tap {
            config.auction.priceFloors.maxSchemaDims = maxSchemaDims
            config.auction.priceFloors.maxSchemaDimsSnakeCase = maxSchemaDimsSnakeCase
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a errors"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor schema dimensions ${getSchemaSize(bidRequest)} exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"]

        where:
        maxSchemaDims              | maxSchemaDimsSnakeCase
        MAX_SCHEMA_DIMENSIONS_SIZE | null
        null                       | MAX_SCHEMA_DIMENSIONS_SIZE
    }

    def "PBS should fail with error and maxSchemaDims take precede over fetch.maxSchemaDims when requested both"() {
        given: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Account with maxSchemaDims in the DB"
        def accountId = bidRequest.site.publisher.id
        def floorSchemaFilesSize = getSchemaSize(bidRequest)
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.maxSchemaDims = MAX_SCHEMA_DIMENSIONS_SIZE
            config.auction.priceFloors.fetch.maxSchemaDims = floorSchemaFilesSize
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a errors"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor schema dimensions ${floorSchemaFilesSize} " +
                         "exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"]
    }

    def "PBS shouldn't fail with error and maxSchemaDims take precede over fetch.maxSchemaDims when requested both"() {
        given: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Account with maxSchemaDims in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.maxSchemaDims = getSchemaSize(bidRequest)
            config.auction.priceFloors.fetch.maxSchemaDims = getSchemaSize(bidRequest) - 1
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't log a errors"
        assert !response.ext?.errors
    }

    def "PBS should emit errors when stored request has more rules than price-floor.max-rules for amp request"() {
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
            config.auction.priceFloors.maxRules = maxRules
            config.auction.priceFloors.maxRulesSnakeCase = maxRulesSnakeCase
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest)
        bidResponse.seatbid.first().bid.first().price = requestFloorValue
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = floorsPbsService.sendAmpRequest(ampRequest)

        then: "PBS should log a errors"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor rules number ${getRuleSize(ampStoredRequest)} " +
                         "exceeded its maximum number ${MAX_RULES_SIZE}"]

        where:
        maxRules       | maxRulesSnakeCase
        MAX_RULES_SIZE | null
        null           | MAX_RULES_SIZE
    }

    private static int getSchemaSize(BidRequest bidRequest) {
        bidRequest?.ext?.prebid?.floors?.data?.modelGroups[0].schema.fields.size()
    }

    private static int getRuleSize(BidRequest bidRequest) {
        bidRequest?.ext?.prebid?.floors?.data?.modelGroups[0].values.size()
    }
}
