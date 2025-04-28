package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPriceFloorsConfig
import org.prebid.server.functional.model.db.Account
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
import static org.prebid.server.functional.model.request.auction.FetchStatus.INPROGRESS
import static org.prebid.server.functional.model.request.auction.FetchStatus.NONE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class PriceFloorsSignalingSpec extends PriceFloorsBaseSpec {

    private static final Closure<String> INVALID_CONFIG_METRIC = { account -> "alerts.account_config.${account}.price-floors" }
    private static final Closure<String> WARNING_MESSAGE = { message -> "Failed to parse price floors from request, with a reason: $message" }
    private static final Closure<String> ERROR_LOG = { bidRequest, message -> "Failed to parse price floors from request with id: " +
            "'${bidRequest.id}', with a reason: $message" }
    private static final int MAX_SCHEMA_DIMENSIONS_SIZE = 1
    private static final int MAX_RULES_SIZE = 1
    private static Instant startTime

    def setupSpec() {
        startTime = Instant.now()
    }

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

        then: "PBS should not log warning or errors"
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

        then: "PBS should not log warning or errors"
        assert !response.ext.warnings
        assert !response.ext.errors

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
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue / bidAdjustment, pbsService)

        then: "Bidder request bidFloor should be update according to bidAdjustment"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue / bidAdjustment

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors

        where:
        pbsConfigBidAdjustmentFlag | requestBidAdjustmentFlag | accountBidAdjustmentFlag
        true                       | true                     | null
        true                       | null                     | null
        false                      | null                     | true
    }

    def "PBS should not update imp[0].bidFloor when bidadjustment is disallowed"() {
        given: "Pbs with PF configuration with adjustForBidAdjustment"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.tap {
                adjustForBidAdjustment = pbsConfigBidAdjustmentFlag
                adjustForBidAdjustmentSnakeCase = pbsConfigBidAdjustmentFlagSnakeCase
            }
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
            config.auction.priceFloors.adjustForBidAdjustmentSnakeCase = accountBidAdjustmentFlagSnakeCase
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS cache rules and processes auction request"
        cacheFloorsProviderRules(bidRequest, floorsProviderFloorValue, pbsService)

        then: "Bidder request bidFloor should be changed"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors

        where:
        pbsConfigBidAdjustmentFlagSnakeCase | pbsConfigBidAdjustmentFlag | requestBidAdjustmentFlag | accountBidAdjustmentFlag | accountBidAdjustmentFlagSnakeCase
        null                                | false                      | false                    | null                     | false
        null                                | true                       | null                     | false                    | null
        false                               | null                       | false                    | null                     | false
        true                                | null                       | null                     | false                    | null
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

    def "PBS shouldn't emit warning when request schema.fields equal to floor-config.max-schema-dims"() {
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

        then: "PBS should not add warning or errors"
        assert !response.ext.warnings
        assert !response.ext.errors
    }

    def "PBS should emit warning when request has more rules than price-floor.max-rules"() {
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

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor rules number ${getRuleSize(bidRequest)} exceeded its maximum number ${MAX_RULES_SIZE}"]

        and: "Alerts.general metrics should be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        maxRules       | maxRulesSnakeCase
        MAX_RULES_SIZE | null
        null           | MAX_RULES_SIZE
    }

    def "PBS should emit warning when request has more schema.fields than floor-config.max-schema-dims"() {
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

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Failed to parse price floors from request, with a reason: " +
                         "Price floor schema dimensions ${getSchemaSize(bidRequest)} exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"]

        and: "Alerts.general metrics should be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        maxSchemaDims              | maxSchemaDimsSnakeCase
        MAX_SCHEMA_DIMENSIONS_SIZE | null
        null                       | MAX_SCHEMA_DIMENSIONS_SIZE
    }

    def "PBS should emit warning when request has more schema.fields than default-account.max-schema-dims"() {
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

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor schema dimensions ${getSchemaSize(bidRequest)} exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Metric alerts.account_config.ACCOUNT.price-floors should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[INVALID_CONFIG_METRIC(bidRequest.accountId) as String] == 1
        assert !metrics[ALERT_GENERAL]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsFloorConfig)
    }

    def "PBS should emit warning when request has more schema.fields than default-account.fetch.max-schema-dims"() {
        given: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Floor config with default account"
        def accountConfig = getDefaultAccountConfigSettings().tap {
            auction.priceFloors.fetch.enabled = true
            auction.priceFloors.fetch.url = BASIC_FETCH_URL + bidRequest.site.publisher.id
            auction.priceFloors.fetch.maxSchemaDims = MAX_SCHEMA_DIMENSIONS_SIZE
            auction.priceFloors.maxSchemaDims = MAX_SCHEMA_DIMENSIONS_SIZE
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
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Response should includer error warning"
        def message = "Price floor schema dimensions ${getSchemaSize(bidRequest)} exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS shouldn't log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidResponse, message))
        assert floorsLogs.size() == 1

        and: "Metric alerts.account_config.ACCOUNT.price-floors should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[INVALID_CONFIG_METRIC(bidRequest.accountId) as String] == 1
        assert !metrics[ALERT_GENERAL]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsFloorConfig)
    }

    def "PBS should emit warning when request has more schema.fields than fetch.max-schema-dims"() {
        given: "BidRequest with schema 2 fields"
        def bidRequest = bidRequestWithFloors

        and: "Account with disabled fetch in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.accountId).tap {
            config.auction.priceFloors.maxSchemaDims = maxSchemaDims
            config.auction.priceFloors.maxSchemaDimsSnakeCase = maxSchemaDimsSnakeCase
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor schema dimensions ${getSchemaSize(bidRequest)} exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

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

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor schema dimensions ${floorSchemaFilesSize} " +
                "exceeded its maximum number ${MAX_SCHEMA_DIMENSIONS_SIZE}"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
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

        then: "PBS shouldn't add warnings or errors"
        assert !response.ext?.warnings
    }

    def "PBS should emit warning when stored request has more rules than price-floor.max-rules for amp request"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with 2 rules"
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

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes amp request"
        def response = floorsPbsService.sendAmpRequest(ampRequest)

        then: "PBS should log a warning"
        def message = "Price floor rules number ${getRuleSize(ampStoredRequest)} " +
                "exceeded its maximum number ${MAX_RULES_SIZE}"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(ampStoredRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        maxRules       | maxRulesSnakeCase
        MAX_RULES_SIZE | null
        null           | MAX_RULES_SIZE
    }

    def "PBS should emit error in log and response when data is invalid and floors status is in progress"() {
        given: "Default BidRequest with empty floors.data"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data = null
        }

        and: "Account with invalid floors config"
        def account = getAccountWithEnabledFetch(bidRequest.accountId).tap {
            config.auction.priceFloors.enabled = true
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Response should includer error warning"
        def message = 'Price floor rules data must be present'
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS shouldn't log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidResponse, message))
        assert !floorsLogs.size()

        and: "PBS request status should be in progress"
        assert getRequests(bidResponse)[GENERIC]?.first?.ext?.prebid?.floors?.fetchStatus == INPROGRESS
        def bidderRequest = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequest.ext.prebid.floors.fetchStatus == [INPROGRESS]

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS shouldn't emit error in log and response when data is invalid and floors status not in progress"() {
        given: "Default BidRequest with empty floors.data"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data = null
        }

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest, floorsPbsService)

        and: "Account with invalid floors config"
        def account = getAccountWithEnabledFetch(bidRequest.accountId).tap {
            config.auction.priceFloors.enabled = true
        }
        accountDao.save(account)

        and: "Start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = 'Price floor rules data must be present'
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "PBS request status shouldn't be in progress"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id)
        assert getRequests(bidResponse)[GENERIC]?.first?.ext?.prebid?.floors?.fetchStatus == NONE
        assert bidderRequest.ext.prebid.floors.fetchStatus.sort() == [NONE, INPROGRESS].sort()

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS should emit error in log and response when floors skipRate is out of range"() {
        given: "BidRequest with invalid skipRate"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.skipRate = requestSkipRate
        }

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor data skipRate must be in range(0-100), but was $requestSkipRate"
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestSkipRate << [PBSUtils.randomNegativeNumber, PBSUtils.getRandomNumber(100)]
    }

    def "PBS should emit error in log and response when floors modelGroups is empty"() {
        given: "BidRequest with empty modelGroups"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups = requestModelGroups
        }

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor rules should contain at least one model group"
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestModelGroups << [null, []]
    }

    def "PBS should emit error in log and response when modelGroup modelWeight is out of range"() {
        given: "BidRequest with invalid modelWeight"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups = [
                    new ModelGroup(modelWeight: requestModelWeight)
            ]
        }

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor modelGroup modelWeight must be in range(1-100), but was $requestModelWeight"
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestModelWeight << [PBSUtils.randomNegativeNumber, PBSUtils.getRandomNumber(100)]
    }

    def "PBS should emit error in log and response when modelGroup skipRate is out of range"() {
        given: "BidRequest with invalid modelGroup skipRate"
        def requestModelGroupsSkipRate = PBSUtils.getRandomNumber(100)
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups = [
                    new ModelGroup(skipRate: requestModelGroupsSkipRate)
            ]
        }

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor modelGroup skipRate must be in range(0-100), but was $requestModelGroupsSkipRate"
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS should emit error in log and response when modelGroup defaultFloor is negative"() {
        given: "BidRequest with negative defaultFloor"
        def requestModelGroupsSkipRate = PBSUtils.randomNegativeNumber

        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors.data.modelGroups = [
                    new ModelGroup(defaultFloor: requestModelGroupsSkipRate)
            ]
        }

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def bidResponse = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Price floor modelGroup default must be positive float, but was $requestModelGroupsSkipRate"
        assert bidResponse.ext?.warnings[PREBID]*.code == [999]
        assert bidResponse.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE(message)]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, ERROR_LOG(bidRequest, message))
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS should use default floors config when original account config is invalid"() {
        given: "Bid request with empty floors"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors = null
        }

        and: "Account with invalid floors config"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(priceFloors: new AccountPriceFloorsConfig(enabled: false)))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS floors validation failure should not reject the entire auction"
        assert !response.seatbid?.isEmpty()

        then: "PBS shouldn't log warning or errors"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS should emit error in log and response when account have disabled dynamic data config"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with floors"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.floors = null
        }

        and: "Account with invalid floors config"
        def account = getAccountWithEnabledFetch(bidRequest.accountId).tap {
            config.auction.priceFloors.enabled = true
            config.auction.priceFloors.useDynamicData = false
        }
        accountDao.save(account)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flush metrics"
        flushMetrics(floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        def message = "Using dynamic data is not allowed for account ${account.uuid}".toString()
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [message]

        and: "PBS should log a errors"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, message)
        assert floorsLogs.size() == 1
        assert floorsLogs[0].contains(message)

        and: "Alerts.general metrics should be populated"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    private static int getSchemaSize(BidRequest bidRequest) {
        bidRequest?.ext?.prebid?.floors?.data?.modelGroups[0].schema.fields.size()
    }

    private static int getRuleSize(BidRequest bidRequest) {
        bidRequest?.ext?.prebid?.floors?.data?.modelGroups[0].values.size()
    }
}
