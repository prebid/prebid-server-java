package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountRankingConfig
import org.prebid.server.functional.model.config.PriceGranularityType
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.AdServerTargeting
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.PrebidCache
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.PriceGranularity
import org.prebid.server.functional.model.request.auction.Range
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidMediaType
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.model.response.auction.Prebid
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.util.PBSUtils

import java.math.RoundingMode
import java.nio.charset.StandardCharsets

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.model.config.PriceGranularityType.UNKNOWN
import static org.prebid.server.functional.model.response.auction.ErrorType.TARGETING
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class TargetingSpec extends BaseSpec {

    private static final Integer TARGETING_PARAM_NAME_MAX_LENGTH = 20
    private static final Integer TARGETING_KEYS_SIZE = 14
    private static final Integer MAX_AMP_TARGETING_TRUNCATION_LENGTH = 11
    private static final String DEFAULT_TARGETING_PREFIX = "hb"
    private static final Integer TARGETING_PREFIX_LENGTH = 11
    private static final Integer MAX_TRUNCATE_ATTR_CHARS = 255
    private static final Integer MAX_BIDS_RANKING = 3
    private static final String HB_ENV_AMP = "amp"
    private static final Integer MAIN_RANK = 1
    private static final Integer SUBORDINATE_RANK = 2

    def "PBS should include targeting bidder specific keys when alwaysIncludeDeals is true and deal bid wins"() {
        given: "Bid request with alwaysIncludeDeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: false, alwaysIncludeDeals: true)
        }

        and: "Bid response with 2 bids where deal bid has higher price"
        def bidPrice = PBSUtils.randomPrice
        def dealBidPrice = bidPrice + 1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp[0]).tap { it.price = bidPrice }
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber
            seatbid[0].bid[0].price = dealBidPrice
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)
        def bidderName = GENERIC.value

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        def notBidderKeys = targetingKeyMap.findAll { !it.key.endsWith(bidderName) }
        notBidderKeys.each { assert targetingKeyMap.containsKey("${it.key}_$bidderName" as String) }
    }

    def "PBS should not include targeting bidder specific keys when alwaysIncludeDeals flag is #condition"() {
        given: "Bid request with set alwaysIncludeDeals flag"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: false, alwaysIncludeDeals: alwaysIncludeDeals)
        }

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp[0]).tap { it.price = bidPrice }
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber
            seatbid[0].bid[0].price = dealBidPrice
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        targetingKeyMap.each { assert !it.key.endsWith(GENERIC.value) }

        where:
        condition                 || bidPrice                       || dealBidPrice   || alwaysIncludeDeals
        "false and deal bid wins" || PBSUtils.getRandomPrice(1, 10) || bidPrice + 0.5 || false
        "true and deal bid loses" || PBSUtils.getRandomPrice(1, 10) || bidPrice - 0.5 || true
    }

    def "PBS should not include bidder specific keys in bid response targeting when includeBidderKeys is #includeBidderKeys and cache.winningOnly is #winningOnly"() {
        given: "Bid request with set includeBidderKeys, winningOnly flags"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: includeBidderKeys)
            it.ext.prebid.cache = new PrebidCache(winningOnly: winningOnly)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = getEnabledWinBidsPbsService().sendAuctionRequest(bidRequest)

        then: "PBS response targeting does not contain bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        targetingKeyMap.each { assert !it.key.endsWith(GENERIC.value) }

        where:
        includeBidderKeys || winningOnly
        false             || null
        null              || true
        false             || false
        null              || null
    }

    def "PBS should include bidder specific keys in bid response targeting when includeBidderKeys is #includeBidderKeys and cache.winningOnly is #winningOnly"() {
        given: "Bid request with set includeBidderKeys, winningOnly flags"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: includeBidderKeys)
            it.ext.prebid.cache = new PrebidCache(winningOnly: winningOnly)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)
        def bidderName = GENERIC.value

        when: "PBS processes auction request"
        def response = getDisabledWinBidsPbsService().sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        def notBidderKeys = targetingKeyMap.findAll { !it.key.endsWith(bidderName) }
        notBidderKeys.each { assert targetingKeyMap.containsKey("${it.key}_$bidderName" as String) }

        where:
        includeBidderKeys || winningOnly
        true              || null
        null              || false
        true              || false
        null              || null
    }

    def "PBS auction shouldn't throw an exception and don't populate targeting when targeting includeBidderKeys and includeWinners and includeFormat flags are false"() {
        given: "Default bid request with includeBidderKeys=false and includeWinners=false and includeFormat=false"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting().tap {
                includeBidderKeys = false
                includeWinners = false
                includeFormat = false
            }
        }

        when: "Requesting PBS auction"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain targeting in response"
        assert !bidResponse.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
    }

    def "PBS amp shouldn't throw an exception and don't populate targeting when includeBidderKeys and includeWinners and includeFormat flags are false"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with includeBidderKeys=false and includeWinners=false and includeFormat=false"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting().tap {
                includeBidderKeys = false
                includeWinners = false
                includeFormat = false
            }
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response shouldn't contain targeting"
        assert !response.targeting
    }

    def "PBS should include only #presentDealKey deal specific targeting key when includeBidderKeys is #includeBidderKeys and includeWinners is #includeWinners"() {
        given: "Bid request with set includeBidderKeys and includeWinners flags"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: includeBidderKeys, includeWinners: includeWinners)
        }

        and: "Deal specific bid response"
        def dealId = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].dealid = dealId
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting includes only one deal specific key"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        assert !targetingKeyMap.containsKey(absentDealKey)

        def dealTargetingKey = targetingKeyMap.get(presentDealKey)
        assert dealTargetingKey
        assert dealTargetingKey == dealId as String

        where:
        includeBidderKeys || includeWinners || absentDealKey              || presentDealKey
        false             || true           || "hb_deal_" + GENERIC.value || "hb_deal"
        true              || false          || "hb_deal"                  || "hb_deal_" + GENERIC.value
    }

    def "PBS should copy amp query params to ext.prebid.amp.data when amp request specified"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain amp query params in ext.prebid.amp.data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            ampRequest.tagId == bidderRequest.ext.prebid.amp.data.tagId
            ampRequest.debug == bidderRequest.ext.prebid.amp.data.debug
            ampRequest.curl == bidderRequest.ext.prebid.amp.data.curl
            ampRequest.account == bidderRequest.ext.prebid.amp.data.account
        }
    }

    def "PBS should populate amp response with custom targeting when custom targeting present in ext.prebid.adservertargeting"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with custom ad server targeting"
        def uuid = UUID.randomUUID().toString()
        def customBidRequest = "custom_bid_request"
        def customAmp = "custom_amp"
        def customStatic = "custom_static"
        def customValue = "static-value"
        def customBidder = "{{BIDDER}}_custom"
        def storedBidResponseId = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.adServerTargeting = [
                    new AdServerTargeting().tap {
                        key = customBidRequest
                        source = "bidrequest"
                        value = "imp.id"
                    },
                    new AdServerTargeting().tap {
                        key = customAmp
                        source = "bidrequest"
                        value = "ext.prebid.amp.data.curl"
                    },
                    new AdServerTargeting().tap {
                        key = customStatic
                        source = "static"
                        value = "static-value"
                    },
                    new AdServerTargeting().tap {
                        key = "{{BIDDER}}_custom"
                        source = "bidresponse"
                        value = "seatbid.bid.price"
                    }]
            imp[0].tap {
                id = uuid
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedBidResponseId, bidder: GENERIC)]
            }
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Create and save stored response into DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest)
        def storedResponse = new StoredResponse(responseId: storedBidResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response targeting should contain ad server targeting key"
        verifyAll {
            response.targeting[customBidRequest] == uuid
            response.targeting[customAmp] == ampRequest.curl
            response.targeting[customStatic] == customValue
            response.targeting[customBidder.replace("{{BIDDER}}", GENERIC.value)]
                    == storedBidResponse.seatbid[0].bid[0].price.stripTrailingZeros().toString()
        }
    }

    def "PBS shouldn't populate amp response with custom targeting when adServerTargeting contain incorrect fields"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default BidRequest"
        def customKey = "hb_custom_key"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.adServerTargeting = [
                    new AdServerTargeting().tap {
                        key = customKey
                        source = customSource
                        value = customValue
                    }]
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response shouldn't contain custom targeting"
        assert !response.targeting[customKey]

        where:
        customSource  | customValue
        "bidrequest"  | "imp"
        "bidrequest"  | "ext.prebid.bogus"
        "bidresponse" | "seatbid.bid"
        "bidresponse" | "seatbid.bid.ext.bogus"
    }

    def "PBS should truncate target in amp response with custom targeting when targeting is biggest that twenty"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def customKey = "hb_custom_key_that_is_too_long"
        def staticValue = "static_value"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.adServerTargeting = [
                    new AdServerTargeting().tap {
                        key = customKey
                        source = "static"
                        value = staticValue
                    }]
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response shouldn't contain custom targeting with full naming"
        assert !response.targeting[customKey]

        and: "Amp response should contain custom truncate targeting"
        assert response.targeting[customKey.substring(0, TARGETING_PARAM_NAME_MAX_LENGTH)] == staticValue
    }

    def "PBS should auction populate ext.prebid.targeting with proper size when truncateTargetAttr is define"() {
        given: "PBs config with additional openx bidder"
        def pbsConfig = [
                "adapters.openx.enabled" : "true",
                "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            imp[0].ext.prebid.bidder.generic = new Generic()
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def targetingLength = PBSUtils.getRandomNumber(2, 10)
        def account = new Account(uuid: accountId, truncateTargetAttr: targetingLength)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain targeting with corresponding length"
        assert response.seatbid.bid.ext.prebid.targeting
                .every(list -> list
                        .every(map -> map.keySet()
                                .every(key -> key.length() <= targetingLength)))
    }

    def "PBS should truncate targeting corresponding to value in account config when in account define truncate target attr"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, BidRequest.defaultStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Create and save account in the DB"
        def account = new Account(uuid: ampRequest.account, truncateTargetAttr: MAX_AMP_TARGETING_TRUNCATION_LENGTH)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain in targeting key not biggest that max size define in account"
        assert response.targeting.keySet().every { str -> str.length() <= MAX_AMP_TARGETING_TRUNCATION_LENGTH }
    }

    def "PBS shouldn't populate targeting in response when truncate target attr less then eleven"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Create and save account in the DB"
        def account = new Account(uuid: ampRequest.account, truncateTargetAttr: PBSUtils.getRandomNumber(1, 10))
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response shouldn't contain targeting"
        assert response.targeting.isEmpty()
    }

    def "PBS amp should use ranges.max value for hb_pb targeting when bid.price is greater than ranges.max"() {
        given: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with targeting and stored bid response"
        def storedBidResponseId = PBSUtils.randomString
        def max = PBSUtils.randomDecimal
        def precision = 2
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedBidResponseId, bidder: GENERIC)]
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                priceGranularity = new PriceGranularity().tap {
                    it.precision = precision
                    ranges = [new Range(max: max, increment: PBSUtils.randomDecimal)]
                }
            }
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Create and save stored response into DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].price = max.plus(1)
        }
        def storedResponse = new StoredResponse(responseId: storedBidResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Create and save account in the DB"
        def account = new Account(uuid: ampRequest.account)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain targeting hb_pb"
        assert response.targeting["hb_pb"] == String.format("%,.2f", max.setScale(precision, RoundingMode.DOWN))
    }

    def "PBS auction should use ranges.max value for hb_pb targeting when bid.price is greater that ranges.max"() {
        given: "Default bid request"
        def storedBidResponseId = PBSUtils.randomString
        def max = PBSUtils.randomDecimal
        def precision = 2
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedBidResponseId, bidder: GENERIC)]
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                priceGranularity = new PriceGranularity().tap {
                    it.precision = precision
                    it.ranges = [new Range(max: max, increment: PBSUtils.randomDecimal)]
                }
            }
        }

        and: "Create and save stored response into DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = max.plus(1)
        }
        def storedResponse = new StoredResponse(responseId: storedBidResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain targeting hb_pb"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap["hb_pb"] == String.format("%,.2f", max.setScale(precision, RoundingMode.DOWN))
    }

    def "PBS auction shouldn't delete bid and update targeting if price equal zero and dealId it present"() {
        given: "Default bid request with stored response"
        def storedBidResponseId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedBidResponseId, bidder: GENERIC)]
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true)
        }

        and: "Create and save stored response with zero prise into DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = 0
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber
        }
        def storedResponse = new StoredResponse(responseId: storedBidResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain proper targeting hb_pb"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap["hb_pb"] == '0.0'
        assert targetingKeyMap["hb_pb_generic"] == '0.0'
    }

    def "PBS auction should use default targeting prefix when ext.prebid.targeting.prefix is biggest that twenty"() {
        given: "Bid request with long targeting prefix"
        def prefix = PBSUtils.getRandomString(30)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.size() == 6
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }
    }

    def "PBS auction should use default targeting prefix when auction.config.targeting.prefix is biggest that twenty"() {
        given: "Bid request with targeting"
        def prefix = PBSUtils.getRandomString(30)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.size() == 6
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }
    }

    def "PBS auction should default targeting prefix when ext.prebid.targeting.prefix is #prefix"() {
        given: "Bid request with invalid targeting prefix"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.size() == 6
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }

        where:
        prefix << [null, ""]
    }

    def "PBS auction should default targeting prefix when auction.targeting.prefix is #prefix"() {
        given: "Bid request with targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.size() == 6
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }

        where:
        prefix << [null, ""]
    }

    def "PBS auction should update targeting prefix when ext.prebid.targeting.prefix specified"() {
        given: "Bid request with targeting prefix"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain targeting with requested prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS auction should update prefix name for targeting when account specified"() {
        given: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain targeting key with specified prefix in account level"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS auction should update targeting prefix and take precedence request level over account when prefix specified in both place"() {
        given: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: "account_"))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain targeting key with specified prefix in account level"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS amp should trim targeting prefix when ext.prebid.targeting.prefix targeting is biggest that twenty"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def prefix = PBSUtils.getRandomString(30)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain default targeting prefix"
        def targeting = ampResponse.targeting
        assert targeting.size() == TARGETING_KEYS_SIZE
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }
    }

    def "PBS amp should trim targeting prefix when auction.config.targeting.prefix targeting is biggest that twenty"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def prefix = PBSUtils.getRandomString(30)
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config))
        accountDao.save(account)

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert targeting.size() == TARGETING_KEYS_SIZE
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }
    }

    def "PBS amp should default targeting prefix when auction.config.targeting.prefix is #prefix"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }

        where:
        prefix << [null, ""]
    }

    def "PBS amp should default targeting prefix when ext.prebid.targeting is #prefix"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }

        where:
        prefix << [null, ""]
    }

    def "PBS amp should update targeting prefix when specified in account prefix"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS amp should use custom prefix for targeting when stored request ext.prebid.targeting.prefix specified"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain custom targeting prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS amp should take precedence from ext.prebid.targeting.prefix when specified in account targeting prefix"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: "account_"))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS amp should move targeting key to imp.ext.data"() {
        given: "Create targeting"
        def targeting = new org.prebid.server.functional.model.request.amp.Targeting().tap {
            any = PBSUtils.randomString
        }

        and: "Encode Targeting to String"
        def encodeTargeting = URLEncoder.encode(encode(targeting), StandardCharsets.UTF_8)

        and: "Amp request with targeting"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.targeting = encodeTargeting
        }

        and: "Default BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain value from targeting in imp.ext.data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.imp[0].ext.data.any == targeting.any
    }

    def "PBS amp should use long account targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given: "PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": prefixMaxChars as String])

        and: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def prefix = PBSUtils.getRandomString(prefixMaxChars - TARGETING_PREFIX_LENGTH)
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = prebidServerService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS amp should use long request targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given: "PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": prefixMaxChars as String])

        and: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Bid request with prefix"
        def prefix = PBSUtils.getRandomString(prefixMaxChars - TARGETING_PREFIX_LENGTH)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = prebidServerService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS auction should use long request targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given: "PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": prefixMaxChars as String])

        and: "Bid request with prefix"
        def prefix = PBSUtils.getRandomString(prefixMaxChars - TARGETING_PREFIX_LENGTH)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = bidResponse.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS auction should use long account targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given: "PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": prefixMaxChars as String])

        and: "Bid request with empty targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def prefix = PBSUtils.getRandomString(prefixMaxChars - TARGETING_PREFIX_LENGTH)
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = bidResponse.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix) }
    }

    def "PBS amp should ignore and add a warning to ext.warnings when value of the account prefix is longer then settings.targeting.truncate-attr-chars"() {
        given: "PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2, 10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": targetingChars as String])

        and: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def prefix = PBSUtils.getRandomString(targetingChars + 1)
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = prebidServerService.sendAmpRequest(ampRequest)

        then: "Amp response should contain warning"
        assert ampResponse.ext?.warnings[TARGETING]*.message == ["Key prefix value is dropped to default. " +
                                                                         "Decrease custom prefix length or increase truncateattrchars by " +
                                                                         "${prefix.length() + TARGETING_PREFIX_LENGTH - targetingChars}"]
    }

    def "PBS amp should ignore and add a warning to ext.warnings when value of the request prefix is longer then settings.targeting.truncate-attr-chars"() {
        given: "PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2, 10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": targetingChars as String])

        and: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Bid request with prefix"
        def prefix = PBSUtils.getRandomString(targetingChars)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: PBSUtils.getRandomString(targetingChars))
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = prebidServerService.sendAmpRequest(ampRequest)

        then: "Amp response should contain warning"
        assert ampResponse.ext?.warnings[TARGETING]*.message == ["Key prefix value is dropped to default. " +
                                                                         "Decrease custom prefix length or increase truncateattrchars by " +
                                                                         "${prefix.length() + TARGETING_PREFIX_LENGTH - targetingChars}"]
    }

    def "PBS auction should ignore and add a warning to ext.warnings when value of the request prefix is longer then settings.targeting.truncate-attr-chars"() {
        given: "PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2, 10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": targetingChars as String])

        and: "Bid request with prefix"
        def prefixSize = targetingChars + 1
        def prefix = PBSUtils.getRandomString(prefixSize)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        def targeting = bidResponse.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }
        assert bidResponse.ext?.warnings[TARGETING]*.message == ["Key prefix value is dropped to default. " +
                                                                         "Decrease custom prefix length or increase truncateattrchars by " +
                                                                         "${prefix.length() + TARGETING_PREFIX_LENGTH - targetingChars}"]
    }

    def "PBS auction should ignore and add a warning to ext.warnings when value of the account prefix is longer then settings.targeting.truncate-attr-chars"() {
        given: "PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2, 10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars": targetingChars as String])

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def prefix = PBSUtils.getRandomString(targetingChars + 1)
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: config))
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        def targeting = bidResponse.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(DEFAULT_TARGETING_PREFIX) }
        assert bidResponse.ext?.warnings[TARGETING]*.message == ["Key prefix value is dropped to default. " +
                                                                         "Decrease custom prefix length or increase truncateattrchars by " +
                                                                         "${prefix.length() + TARGETING_PREFIX_LENGTH - targetingChars}"]
    }

    def "PBS amp should apply data from query to ext.prebid.amp.data"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def unknownValue = PBSUtils.randomString
        def secondUnknownValue = PBSUtils.randomNumber
        defaultPbsService.sendAmpRequestWithAdditionalQueries(ampRequest, ["unknown_field"       : unknownValue,
                                                                           "second_unknown_field": secondUnknownValue])

        then: "Amp should contain data from query request"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        def ampData = bidderRequests.ext.prebid.amp.data
        assert ampData.unknownField == unknownValue
        assert ampData.secondUnknownField == secondUnknownValue
    }

    def "PBS amp should always send hb_env=amp when stored request does not contain app"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain amp hb_env"
        def targeting = ampResponse.targeting
        assert targeting["hb_env"] == HB_ENV_AMP
    }

    def "PBS auction should throw error when price granularity from original request is empty"() {
        given: "Default bidRequest with empty price granularity"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(priceGranularity: PriceGranularity.getDefault(UNKNOWN))
        }

        and: "Account in the DB"
        def account = createAccountWithPriceGranularity(bidRequest.accountId, PBSUtils.getRandomEnum(PriceGranularityType))
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == 'Invalid request format: Price granularity error: empty granularity definition supplied'
    }

    def "PBS auction should prioritize price granularity from original request over account config"() {
        given: "Default bidRequest with price granularity"
        def requestPriceGranularity = PriceGranularity.getDefault(priceGranularity as PriceGranularityType)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(priceGranularity: requestPriceGranularity)
        }

        and: "Account in the DB"
        def accountAuctionConfig = new AccountAuctionConfig(priceGranularity: PBSUtils.getRandomEnum(PriceGranularityType))
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should include price granularity from bidRequest"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == requestPriceGranularity

        where:
        priceGranularity << (PriceGranularityType.values() - UNKNOWN as List<PriceGranularityType>)
    }

    def "PBS amp should prioritize price granularity from original request over account config"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default ampStoredRequest"
        def requestPriceGranularity = PriceGranularity.getDefault(priceGranularity)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(priceGranularity: requestPriceGranularity)
            setAccountId(ampRequest.account)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = createAccountWithPriceGranularity(ampRequest.account, PBSUtils.getRandomEnum(PriceGranularityType))
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "BidderRequest should include price granularity from bidRequest"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == requestPriceGranularity

        where:
        priceGranularity << (PriceGranularityType.values() - UNKNOWN as List<PriceGranularityType>)
    }

    def "PBS auction should include price granularity from account config when original request doesn't contain price granularity"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(false)
        }

        and: "Account in the DB"
        def account = createAccountWithPriceGranularity(bidRequest.accountId, priceGranularity)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should include price granularity from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == PriceGranularity.getDefault(priceGranularity)

        where:
        priceGranularity << (PriceGranularityType.values() - UNKNOWN as List<PriceGranularityType>)
    }

    def "PBS auction should include price granularity from account config with different name case when original request doesn't contain price granularity"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(false)
        }

        and: "Account in the DB"
        def account = createAccountWithPriceGranularity(bidRequest.accountId, priceGranularity)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should include price granularity from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == PriceGranularity.getDefault(priceGranularity)

        where:
        priceGranularity << (PriceGranularityType.values() - UNKNOWN as List<PriceGranularityType>)
    }

    def "PBS auction should include price granularity from default account config when original request doesn't contain price granularity"() {
        given: "Pbs with default account that include privacySandbox configuration"
        def priceGranularity = PBSUtils.getRandomEnum(PriceGranularityType, [UNKNOWN])
        def accountAuctionConfig = new AccountAuctionConfig(priceGranularity: priceGranularity)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(accountConfig)])

        and: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(false)
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should include price granularity from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == PriceGranularity.getDefault(priceGranularity)
    }

    def "PBS auction should include include default price granularity when original request and account config doesn't contain price granularity"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(false)
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should include default price granularity"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == PriceGranularity.default

        where:
        accountAuctionConfig << [
                null,
                new AccountAuctionConfig(),
                new AccountAuctionConfig(priceGranularity: UNKNOWN)]
    }

    def "PBS amp should throw error when price granularity from original request is empty"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default ampStoredRequest with empty price granularity"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(priceGranularity: PriceGranularity.getDefault(UNKNOWN))
            setAccountId(ampRequest.account)
        }

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = createAccountWithPriceGranularity(ampRequest.account, PBSUtils.getRandomEnum(PriceGranularityType))
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == 'Invalid request format: Price granularity error: empty granularity definition supplied'
    }

    def "PBS amp should include price granularity from account config when original request doesn't contain price granularity"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default ampStoredRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = Targeting.createWithAllValuesSetTo(false)
            setAccountId(ampRequest.account)
        }

        and: "Account in the DB"
        def account = createAccountWithPriceGranularity(ampRequest.account, priceGranularity)
        accountDao.save(account)

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "BidderRequest should include price granularity from account config"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest?.ext?.prebid?.targeting?.priceGranularity == PriceGranularity.getDefault(priceGranularity)

        where:
        priceGranularity << (PriceGranularityType.values() - UNKNOWN as List<PriceGranularityType>)
    }

    def "PBS shouldn't add bid ranked for request when account config for auction.ranking disabled or default"() {
        given: "Bid request with enabled preferDeals"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true)
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Bid response with 3 bids where deal bid has higher price"
        def imp = bidRequest.imp.first
        def bids = [Bid.getDefaultBid(imp), Bid.getDefaultBid(imp), Bid.getDefaultBid(imp)]
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = bids
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS bids in response shouldn't contain ranks"
        assert response?.seatbid?.bid?.ext?.prebid?.rank?.flatten() == [null] * MAX_BIDS_RANKING

        where:
        accountAuctionConfig << [
                null,
                new AccountAuctionConfig(),
                new AccountAuctionConfig(ranking: new AccountRankingConfig()),
                new AccountAuctionConfig(ranking: new AccountRankingConfig(enabled: null)),
                new AccountAuctionConfig(ranking: new AccountRankingConfig(enabled: false))
        ]
    }

    def "PBS should add bid ranked and rank by deals for default request when auction.ranking and preferDeals are enabled"() {
        given: "Bid request with enabled preferDeals"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = true
            }
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPrice = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.price = bidPrice + 1
        }
        def bidWithDeal = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidBiggerPrice, bidWithDeal]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank single bid"
        verifyAll(response.seatbid.first.bid) {
            it.id == [bidWithDeal.id]
            it.price == [bidWithDeal.price]
            it.ext.prebid.rank == [MAIN_RANK]
        }
    }

    def "PBS should add bid ranked and rank by price for default request when auction.ranking is enabled and preferDeals disabled"() {
        given: "Bid request with disabled preferDeals"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = false
            }
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPrice = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.price = bidPrice + 1
        }
        def bidWithDealId = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidBiggerPrice, bidWithDealId]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank single bid"
        verifyAll(response.seatbid.first.bid) {
            it.id == [bidBiggerPrice.id]
            it.price == [bidBiggerPrice.price]
            it.ext.prebid.rank == [MAIN_RANK]
        }
    }

    def "PBS should add bid ranked and rank by price for request with multiBid when auction.ranking is enabled and preferDeals disabled"() {
        given: "Bid request with disabled preferDeals"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = false
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPrice = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.price = bidPrice + 1
        }
        def bidBDeal = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidBiggerPrice, bidBDeal]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank bid with higher price as top priority"
        def bids = response.seatbid.first.bid
        assert bids.find(it -> it.id == bidBiggerPrice.id).ext.prebid.rank == 1
        assert bids.find(it -> it.id == bidBDeal.id).ext.prebid.rank == 2
    }

    def "PBS should add bid ranked and rank by price for multiple media types request when auction.ranking is enabled and preferDeals disabled"() {
        given: "Bid request with disabled preferDeals"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.video = Video.getDefaultVideo()
            it.imp.first.nativeObj = Native.getDefaultNative()
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = false
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPrice = Bid.getDefaultMultiTypesBids(bidRequest.imp.first).first.tap {
            it.price = bidPrice + 1
        }
        def bidBDeal = Bid.getDefaultMultiTypesBids(bidRequest.imp.first).last.tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidBiggerPrice, bidBDeal]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank bid with higher price as top priority"
        assert !response.ext.warnings
        def bids = response.seatbid.first.bid
        assert bids.find(it -> it.id == bidBiggerPrice.id).ext.prebid.rank == 1
        assert bids.find(it -> it.id == bidBDeal.id).ext.prebid.rank == 2
    }

    def "PBS should properly rank bids when request with multibid contains some invalid bid"() {
        given: "Bid request with disabled preferDeals"
        def bidRequest = BidRequest.getDefaultVideoRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = false
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with multiple bids"
        def bidPrice = PBSUtils.randomPrice
        def higherPriceBid = Bid.getDefaultBid(bidRequest.imp.first).tap {
            price = bidPrice + 2
        }

        def middlePriceBid = Bid.getDefaultBid(bidRequest.imp.first).tap {
            price = bidPrice + 1
            adm = null
        }

        def lowerPriceBid = Bid.getDefaultBid(bidRequest.imp.first).tap {
            price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [lowerPriceBid, middlePriceBid, higherPriceBid]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank bid with higher price as top priority"
        def bids = response.seatbid.first.bid
        assert bids.find(it -> it.id == higherPriceBid.id).ext.prebid.rank == 1
        assert bids.find(it -> it.id == lowerPriceBid.id).ext.prebid.rank == 2

        and: "PBS should contain error for invalid bid"
        response.ext.errors[ErrorType.GENERIC]?.message ==
                ["BidId `${middlePriceBid.id}` validation messages: Error: Bid \"${middlePriceBid.id}\" with video type missing adm and nurl"]
    }

    def "PBS should assign bid ranks across all seatbids combined when the request contains imps with multiple bidders"() {
        given: "PBS config with openX bidder"
        def endpoint = '/openx-auction'
        def pbsConfig = ["adapters.openx.enabled" : "true",
                         "adapters.openx.endpoint": "$networkServiceContainer.rootUri$endpoint".toString()]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)
        def openxBidder = new Bidder(networkServiceContainer, endpoint)

        and: "Bid request with multiple bidders"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = true
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: WILDCARD, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with multiple bids"
        def bidPrice = PBSUtils.randomPrice
        def genericBid = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.price = bidPrice + 1
        }
        def openxBid = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
        }
        def bidResponseGeneric = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [genericBid], seat: GENERIC)]
        }
        def bidResponseOpenx = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [openxBid], seat: OPENX)]
        }
        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponseGeneric)
        openxBidder.setResponse(bidRequest.id, bidResponseOpenx)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS should rank OpenX bid higher than Generic bid"
        assert response.seatbid.findAll { it.seat == OPENX }.bid.ext.prebid.rank.flatten() == [MAIN_RANK]
        assert response.seatbid.findAll { it.seat == GENERIC }.bid.ext.prebid.rank.flatten() == [SUBORDINATE_RANK]

        cleanup: "Stop and remove pbs container and bidder response"
        pbsServiceFactory.removeContainer(pbsConfig)
        openxBidder.reset()
    }

    def "PBS should assign bid ranks for each imp separately when request has multiple imps and multiBid is configured"() {
        given: "Bid request with multiple imps"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.nativeObj = Native.getDefaultNative()
            imp.add(Imp.getDefaultImpression(VIDEO))
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = requestPreferDeals
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with multiple bids"
        def bidPrice = PBSUtils.randomPrice
        def bidLowerPrice = Bid.getDefaultBid(bidRequest.imp.first).tap {
            price = bidPrice
            mediaType = BidMediaType.NATIVE
        }
        def bidHigherPrice = Bid.getDefaultBid(bidRequest.imp.first).tap {
            price = bidPrice + 1
        }
        def bidWithDeal = Bid.getDefaultBid(bidRequest.imp.last).tap {
            dealid = PBSUtils.randomNumber
            price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidLowerPrice, bidHigherPrice, bidWithDeal]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank bids for first imp"
        def bids = response.seatbid.first.bid
        def firstImpBidders = bids.findAll { it.impid == bidRequest.imp.id.first() }
        assert firstImpBidders.find { it.id == bidHigherPrice.id }.ext.prebid.rank == 1
        assert firstImpBidders.find { it.id == bidLowerPrice.id }.ext.prebid.rank == 2

        and: "should separately rank bids for second imp"
        def secondImpBidders = bids.findAll { it.impid == bidRequest.imp.id.last() }
        assert secondImpBidders*.ext.prebid.rank == [MAIN_RANK]

        where:
        requestPreferDeals << [null, false, true]
    }

    def "PBS should ignore bid ranked from original response when auction.ranking enabled"() {
        given: "Bid request with disabled preferDeals"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = false
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPrice = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.price = bidPrice + 1
            it.ext = new BidExt(prebid: new Prebid(rank: PBSUtils.randomNumber))
        }
        def bidBDeal = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
            it.ext = new BidExt(prebid: new Prebid(rank: PBSUtils.randomNumber))
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidBiggerPrice, bidBDeal]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank bid with higher price as top priority"
        def bids = response.seatbid.first.bid
        assert bids.find(it -> it.id == bidBiggerPrice.id).ext.prebid.rank == 1
        assert bids.find(it -> it.id == bidBDeal.id).ext.prebid.rank == 2
    }

    def "PBS should add bid ranked and rank by price for request with stored imp when auction.ranking enabled"() {
        given: "Bid request with disabled preferDeals"
        def storedRequestId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp.first.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true).tap {
                preferDeals = false
            }
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def account = getAccountConfigWithAuctionRanking(bidRequest.accountId)
        accountDao.save(account)

        and: "Save storedImp into DB"
        def impression = Imp.getDefaultImpression(MediaType.BANNER).tap {
            id = storedRequestId
            video = Video.getDefaultVideo()
        }
        def storedImp = StoredImp.getStoredImp(bidRequest.accountId, impression)
        storedImpDao.save(storedImp)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPrice = Bid.getDefaultMultiTypesBids(impression).first.tap {
            it.price = bidPrice + 1
            impid = bidRequest.imp.id.first
        }
        def bidBDeal = Bid.getDefaultMultiTypesBids(impression).last.tap {
            impid = bidRequest.imp.id.first
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = [bidBiggerPrice, bidBDeal]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should rank bid with higher price as top priority"
        def bids = response.seatbid.first.bid
        assert bids.find(it -> it.id == bidBiggerPrice.id).ext.prebid.rank == 1
        assert bids.find(it -> it.id == bidBDeal.id).ext.prebid.rank == 2
    }

    def "PBS shouldn't rank bids for request with stored imp when auction.ranking default"() {
        given: "Bid request with enabled preferDeals"
        def storedRequestId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp.first.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true)
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(status: ACTIVE, auction: new AccountAuctionConfig())
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Save storedImp into DB"
        def impression = Imp.getDefaultImpression(MediaType.BANNER).tap {
            id = storedRequestId
            video = Video.getDefaultVideo()
            nativeObj = Native.getDefaultNative()
        }
        def storedImp = StoredImp.getStoredImp(bidRequest.accountId, impression)
        storedImpDao.save(storedImp)

        and: "Bid response with 2 bids where deal bid has lower price"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid = Bid.getDefaultMultiTypesBids(impression) { impid = bidRequest.imp.id.first }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS bids in response shouldn't contain ranks"
        assert response?.seatbid?.bid?.ext?.prebid?.rank?.flatten() == [null] * MAX_BIDS_RANKING
    }

    def "PBS should copy bid ranked from stored response when auction.ranking #auction"() {
        given: "Bid request with enabled preferDeals"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.targeting = Targeting.createWithAllValuesSetTo(true)
            it.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: MAX_BIDS_RANKING)]
            enableCache()
            ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(status: ACTIVE, auction: auction)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Stored response in DB"
        def bidPrice = PBSUtils.randomPrice
        def bidBiggerPriceRanking = PBSUtils.randomNumber
        def bidBiggerPrice = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.price = bidPrice + 1
            it.ext = new BidExt(prebid: new Prebid(rank: bidBiggerPriceRanking))
        }
        def bidBDealRanking = PBSUtils.randomNumber
        def bidBDeal = Bid.getDefaultBid(bidRequest.imp[0]).tap {
            it.dealid = PBSUtils.randomNumber
            it.price = bidPrice
            it.ext = new BidExt(prebid: new Prebid(rank: bidBDealRanking))
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: new SeatBid(bid: [bidBiggerPrice, bidBDeal], seat: GENERIC))
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should copy bid ranked from stored response"
        def bids = response.seatbid.first.bid
        assert bids.find(it -> it.id == bidBiggerPrice.id).ext.prebid.rank == bidBiggerPriceRanking
        assert bids.find(it -> it.id == bidBDeal.id).ext.prebid.rank == bidBDealRanking

        where:
        auction << [
                null,
                new AccountAuctionConfig(),
                new AccountAuctionConfig(ranking: new AccountRankingConfig()),
                new AccountAuctionConfig(ranking: new AccountRankingConfig(enabled: null)),
                new AccountAuctionConfig(ranking: new AccountRankingConfig(enabled: false)),
                new AccountAuctionConfig(ranking: new AccountRankingConfig(enabled: true))
        ]
    }

    Account createAccountWithPriceGranularity(String accountId, PriceGranularityType priceGranularity) {
        def accountAuctionConfig = new AccountAuctionConfig(priceGranularity: priceGranularity)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        new Account(uuid: accountId, config: accountConfig)
    }

    Account getAccountConfigWithAuctionRanking(String accountId, Boolean auctionRankingEnablement = true) {
        def accountAuctionConfig = new AccountAuctionConfig(ranking: new AccountRankingConfig(enabled: auctionRankingEnablement))
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        new Account(uuid: accountId, config: accountConfig)
    }

    private static PrebidServerService getEnabledWinBidsPbsService() {
        pbsServiceFactory.getService(["auction.cache.only-winning-bids": "true"])
    }

    private static PrebidServerService getDisabledWinBidsPbsService() {
        pbsServiceFactory.getService(["auction.cache.only-winning-bids": "false"])
    }
}
