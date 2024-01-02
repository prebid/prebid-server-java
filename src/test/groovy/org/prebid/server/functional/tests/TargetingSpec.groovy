package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.AdServerTargeting
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidCache
import org.prebid.server.functional.model.request.auction.PriceGranularity
import org.prebid.server.functional.model.request.auction.Range
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.IgnoreRest
import spock.lang.RepeatUntilFailure

import java.math.RoundingMode

import java.nio.charset.StandardCharsets
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.auction.ErrorType.TARGETING
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class TargetingSpec extends BaseSpec {

    private static final Integer TARGETING_PARAM_NAME_MAX_LENGTH = 20
    private static final Integer MAX_AMP_TARGETING_TRUNCATION_LENGTH = 11
    private static final String DEFAULT_TARGETING_PREFIX = "hb"
    private static final Integer TARGETING_PREFIX_LENGTH = 11
    private static final Integer MAX_TRUNCATE_ATTR_CHARS = 255

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
                    ranges = [new Range(max: max, increment: PBSUtils.randomDecimal)]}
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
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}
    }

    def "PBS auction should use default targeting prefix when auction.config.targeting.prefix is biggest that twenty"() {
        given: "Bid request with targeting"
        def prefix = PBSUtils.getRandomString(30)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId,config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.size() == 6
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}
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
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}

        where: prefix << [null, ""]
    }

    def "PBS auction should default targeting prefix when auction.targeting.prefix is #prefix"() {
        given: "Bid request with targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId,config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.size() == 6
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}

        where: prefix << [null, ""]
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
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
    }

    def "PBS auction should update prefix name for targeting when account specified"() {
        given: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting()
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: bidRequest.accountId,config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain targeting key with specified prefix in account level"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
    }

    def "PBS auction should update targeting prefix and take precedence request level over account when prefix specified in both place"() {
        given: "Default bid request"
        def prefix = PBSUtils.getRandomString(4) + "_"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        and: "Account in the DB"
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: "account_"))
        def account = new Account(uuid: bidRequest.accountId,config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain targeting key with specified prefix in account level"
        def targeting = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
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
        assert targeting.size() == 12
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}
    }

    def "PBS amp should trim targeting prefix when auction.config.targeting.prefix targeting is biggest that twenty"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def prefix = PBSUtils.getRandomString(30)
        def config = new AccountAuctionConfig(targeting: new Targeting(prefix: prefix))
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config) )
        accountDao.save(account)

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert targeting.size() == 12
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}
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
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}

        where: prefix << [null, ""]
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
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}

        where: prefix << [null, ""]
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
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
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
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
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
        def account = new Account(uuid: ampRequest.account, config: new AccountConfig(auction: config) )
        accountDao.save(account)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Amp response should contain targeting response with custom prefix"
        def targeting = ampResponse.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
    }

    def "PBS amp should move targeting key to imp.ext.data"() {
        given: "Create targeting"
        def targeting = new org.prebid.server.functional.model.request.amp.Targeting().tap {
            any = PBSUtils.randomString
        }

        and: "Encode Targeting to String"
        def encodeTargeting =  URLEncoder.encode(encode(targeting), StandardCharsets.UTF_8)

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
        given:"PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  prefixMaxChars as String])

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
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
    }

    def "PBS amp should use long request targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given:"PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  prefixMaxChars as String])

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
        assert targeting.keySet().every { it -> it.startsWith(prefix)}
    }

    def "PBS auction should use long request targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given:"PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  prefixMaxChars as String])

        and:"Bid request with prefix"
        def prefix = PBSUtils.getRandomString(prefixMaxChars - TARGETING_PREFIX_LENGTH)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = new Targeting(prefix: prefix)
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain default targeting prefix"
        def targeting = bidResponse.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targeting.isEmpty()
        assert targeting.keySet().every{it -> it.startsWith(prefix)}
    }

    def "PBS auction should use long account targeting prefix when settings.targeting.truncate-attr-chars override"() {
        given:"PBS config with setting.targeting"
        def prefixMaxChars = PBSUtils.getRandomNumber(35, MAX_TRUNCATE_ATTR_CHARS)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  prefixMaxChars as String])

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
        assert targeting.keySet().every{it -> it.startsWith(prefix)}
    }

    def "PBS amp should ignore and add a warning to ext.warnings when value of the account prefix is longer then settings.targeting.truncate-attr-chars"() {
        given:"PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2, 10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  targetingChars as String])

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
        given:"PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2 ,10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  targetingChars as String])

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
        given:"PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2,10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  targetingChars as String])

        and:"Bid request with prefix"
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
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}
        assert bidResponse.ext?.warnings[TARGETING]*.message == ["Key prefix value is dropped to default. " +
                                    "Decrease custom prefix length or increase truncateattrchars by " +
                                    "${prefix.length() + TARGETING_PREFIX_LENGTH - targetingChars}"]
    }

    def "PBS auction should ignore and add a warning to ext.warnings when value of the account prefix is longer then settings.targeting.truncate-attr-chars"() {
        given: "PBS config with setting.targeting"
        def targetingChars = PBSUtils.getRandomNumber(2, 10)
        def prebidServerService = pbsServiceFactory.getService(
                ["settings.targeting.truncate-attr-chars":  targetingChars as String])

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
        assert targeting.keySet().every{it -> it.startsWith(DEFAULT_TARGETING_PREFIX)}
        assert bidResponse.ext?.warnings[TARGETING]*.message == ["Key prefix value is dropped to default. " +
                                    "Decrease custom prefix length or increase truncateattrchars by " +
                                    "${prefix.length() + TARGETING_PREFIX_LENGTH - targetingChars}"]
    }

    private PrebidServerService getEnabledWinBidsPbsService() {
        pbsServiceFactory.getService(["auction.cache.only-winning-bids": "true"])
    }

    private PrebidServerService getDisabledWinBidsPbsService() {
        pbsServiceFactory.getService(["auction.cache.only-winning-bids": "false"])
    }
}
