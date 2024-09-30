package org.prebid.server.functional.tests.module.ortb2blocking

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.Ortb2BlockingActionOverride
import org.prebid.server.functional.model.config.Ortb2BlockingAttributeConfig
import org.prebid.server.functional.model.config.Ortb2BlockingAttribute
import org.prebid.server.functional.model.config.Ortb2BlockingConditions
import org.prebid.server.functional.model.config.Ortb2BlockingConfig
import org.prebid.server.functional.model.config.Ortb2BlockingOverride
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.AUDIO_BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BADV
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BAPP
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BANNER_BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BCAT
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BTYPE
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.VIDEO_BATTR
import static org.prebid.server.functional.model.config.Stage.BIDDER_REQUEST
import static org.prebid.server.functional.model.config.Stage.RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_ADVERTISER_BLOCKED
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class Ortb2BlockingSpec extends ModuleBaseSpec {

    private static final Map OPENX_CONFIG = ["adapters.openx.enabled" : "true",
                                             "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static final String WILDCARD = '*'

    private final PrebidServerService pbsServiceWithEnabledOrtb2Blocking = pbsServiceFactory.getService(ortb2BlockingSettings + OPENX_CONFIG)

    def "PBS should send original array ortb2 attribute to bidder when enforce blocking is disabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [ortb2Attributes], attributeName)
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should contain proper ortb2 attributes from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
        PBSUtils.randomNumber | BTYPE
    }

    def "PBS should be able to send original array ortb2 attribute to bidder alias"() {
        given: "Default bid request with alias"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName).tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        when: "PBS processes the auction request"
        pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should contain proper ortb2 attributes from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [ortb2Attributes]*.toString()

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
        PBSUtils.randomNumber | BTYPE
    }

    def "PBS shouldn't send original single ortb2 attribute to bidder when enforce blocking is disabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, ortb2Attributes, attributeName)
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for the called bidder"
        assert response.ext.prebid.modules.errors.ortb2Blocking["ortb2-blocking-bidder-request"].first
                .contains("field in account configuration is not an array")

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        and: "PBS request shouldn't contain proper ortb2 attributes from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !getOrtb2Attributes(bidderRequest, attributeName)

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
        PBSUtils.randomNumber | BTYPE
    }

    def "PBS shouldn't send original inappropriate ortb2 attribute to bidder when blocking is disabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [ortb2Attributes], attributeName)
        accountDao.save(account)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for the called bidder"
        assert response.ext.prebid.modules.errors.ortb2Blocking["ortb2-blocking-bidder-request"].first
                .contains("field in account configuration has unexpected type")

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        and: "PBS request shouldn't contain proper ortb2 attributes from account config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !getOrtb2Attributes(bidderRequest, attributeName)

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomNumber | BADV
        PBSUtils.randomNumber | BAPP
        PBSUtils.randomNumber | BCAT
        PBSUtils.randomString | BANNER_BATTR
        PBSUtils.randomString | VIDEO_BATTR
        PBSUtils.randomString | AUDIO_BATTR
        PBSUtils.randomString | BTYPE
    }

    def "PBS shouldn't send original inappropriate ortb2 attribute to bidder when blocking is enabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain any seatbid"
        assert !response.seatbid

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
    }

    def "PBS should send only not matched ortb2 attribute to bidder when blocking is enabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([disallowedOrtb2Attributes], attributeName).tap {
            enforceBlocks = true
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, disallowedOrtb2Attributes, attributeName),
                                    getBidWithOrtb2Attribute(bidRequest.imp.first, allowedOrtb2Attributes, attributeName)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only allowed seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [allowedOrtb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        allowedOrtb2Attributes | disallowedOrtb2Attributes | attributeName
        PBSUtils.randomString  | PBSUtils.randomString     | BADV
        PBSUtils.randomString  | PBSUtils.randomString     | BAPP
        PBSUtils.randomString  | PBSUtils.randomString     | BCAT
        PBSUtils.randomNumber  | PBSUtils.randomNumber     | BANNER_BATTR
        PBSUtils.randomNumber  | PBSUtils.randomNumber     | VIDEO_BATTR
        PBSUtils.randomNumber  | PBSUtils.randomNumber     | AUDIO_BATTR
    }

    def "PBS should send original inappropriate ortb2 attribute to bidder when blocking is disabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = false
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain proper seatbid"
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
    }

    def "PBS should discard unknown adomain bids when enforcement is enabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BADV)

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdomain: true)
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BADV): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def allowedOrtb2Attributes = PBSUtils.randomString
        def bidPrice = PBSUtils.randomPrice
        def bidWithOutAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            adomain = null
            price = bidPrice + 1 // to guarantee higher priority by default settings
        }
        def bidWithAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            adomain = [allowedOrtb2Attributes]
            price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [bidWithOutAdomain, bidWithAdomain]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only allowed seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, BADV) == [allowedOrtb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings
    }

    def "PBS should not discard unknown adomain bids when enforcement is disabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BADV)

        and: "Account in the DB with blocking configuration"
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BADV): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidWithOutAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            adomain = null
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [bidWithOutAdomain]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only allowed seatbid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2BlockingAttributeConfig << [new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdomain: false),
                                         new Ortb2BlockingAttributeConfig(enforceBlocks: false, blockUnknownAdomain: true),
                                         new Ortb2BlockingAttributeConfig(enforceBlocks: true)]
    }

    def "PBS should discard unknown adv cat bids when enforcement is enabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BCAT)

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdvCat: true)
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BCAT): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def allowedOrtb2Attributes = PBSUtils.randomString
        def bidPrice = PBSUtils.randomPrice
        def bidWithOutAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            cat = null
            price = bidPrice + 1 // to guarantee higher priority by default settings
        }
        def bidWithAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            cat = [allowedOrtb2Attributes]
            price = bidPrice
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [bidWithOutAdomain, bidWithAdomain]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only allowed seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, BCAT) == [allowedOrtb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings
    }

    def "PBS should not discard unknown adv cat bids when enforcement is disabled"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BCAT)

        and: "Account in the DB with blocking configuration"
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BCAT): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidWithOutAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            cat = null
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [bidWithOutAdomain]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only allowed seatbid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2BlockingAttributeConfig << [new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdvCat: false),
                                         new Ortb2BlockingAttributeConfig(enforceBlocks: false, blockUnknownAdvCat: true),
                                         new Ortb2BlockingAttributeConfig(enforceBlocks: true)]
    }

    def "PBS should not discard bids with deals when allowed ortb2 attribute for deals is matched"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def attributes = [(attributeName): Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName, [ortb2Attributes]).tap {
            enforceBlocks = true
        }]
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, attributes)
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)
                                            .tap { dealid = PBSUtils.randomNumber }]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only allowed seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
    }

    def "PBS should discard bids with deals when allowed ortb2 attribute for deals is not matched"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def attributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([allowedOrtb2Attributes, dielsOrtb2Attributes], attributeName, [allowedOrtb2Attributes]).tap {
            enforceBlocks = true
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): attributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, dielsOrtb2Attributes, attributeName)
                                            .tap { dealid = PBSUtils.randomNumber }]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain any seatbid"
        assert !response.seatbid.bid.flatten().size()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        allowedOrtb2Attributes | dielsOrtb2Attributes  | attributeName
        PBSUtils.randomString  | PBSUtils.randomString | BADV
        PBSUtils.randomString  | PBSUtils.randomString | BAPP
        PBSUtils.randomString  | PBSUtils.randomString | BCAT
        PBSUtils.randomNumber  | PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber  | PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber  | PBSUtils.randomNumber | AUDIO_BATTR
    }

    def "PBS should be able to override enforcement by bidder"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName).tap {
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(bidders: [OPENX])
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = new Ortb2BlockingActionOverride(enforceBlocks: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: GENERIC),
                          new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: OPENX)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only openx seatbid"
        assert response.seatbid.size() == 1
        assert response.seatbid.first.seat == OPENX
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | AUDIO_BATTR
    }

    def "PBS should be able to override enforcement by media type"() {
        given: "Default bidRequest"
        def bannerImp = Imp.getDefaultImpression(BANNER)
        def videoImp = Imp.getDefaultImpression(VIDEO)
        def bidRequest = getBidRequestForOrtbAttribute(attributeName).tap {
            imp = [bannerImp, videoImp]
        }

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(mediaType: [BANNER])
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = new Ortb2BlockingActionOverride(enforceBlocks: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bannerImp, ortb2Attributes, attributeName)]),
                          new SeatBid(bid: [getBidWithOrtb2Attribute(videoImp, ortb2Attributes, attributeName)])]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only banner seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert response.seatbid.first.bid.first.impid == bannerImp.id
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | attributeName
        PBSUtils.randomString | BADV
        PBSUtils.randomString | BAPP
        PBSUtils.randomString | BCAT
    }

    def "PBS should be able to override enforcement by media type for battr attribute"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(mediaType: [mediaType])
        def ortb2Attribute = PBSUtils.randomNumber
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attribute], attributeName).tap {
            enforceBlocks = true
            actionOverrides = new Ortb2BlockingActionOverride(enforceBlocks: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attribute, attributeName)])]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain banner seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert response.seatbid.first.bid.first.impid == bidRequest.imp.first.id
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attribute]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        attributeName | mediaType
        BANNER_BATTR  | BANNER
        VIDEO_BATTR   | VIDEO
        AUDIO_BATTR   | AUDIO
    }

    def "PBS should be able to override enforcement by deal id"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingOverride(override: [ortb2Attributes], conditions: new Ortb2BlockingConditions(dealIds: [dealId.toString()]))
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName, [ortb2AttributesForDeals]).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, null, [blockingCondition])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)
                                            .tap { dealid = dealId }]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only seatbid with proper deal id"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        dealId                | ortb2Attributes       | ortb2AttributesForDeals | attributeName
        PBSUtils.randomNumber | PBSUtils.randomString | PBSUtils.randomString   | BADV
        PBSUtils.randomNumber | PBSUtils.randomString | PBSUtils.randomString   | BAPP
        PBSUtils.randomNumber | PBSUtils.randomString | PBSUtils.randomString   | BCAT
        PBSUtils.randomNumber | PBSUtils.randomNumber | PBSUtils.randomNumber   | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber | PBSUtils.randomNumber   | VIDEO_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber | PBSUtils.randomNumber   | AUDIO_BATTR
        WILDCARD              | PBSUtils.randomString | PBSUtils.randomString   | BADV
        WILDCARD              | PBSUtils.randomString | PBSUtils.randomString   | BAPP
        WILDCARD              | PBSUtils.randomString | PBSUtils.randomString   | BCAT
        WILDCARD              | PBSUtils.randomNumber | PBSUtils.randomNumber   | BANNER_BATTR
        WILDCARD              | PBSUtils.randomNumber | PBSUtils.randomNumber   | VIDEO_BATTR
        WILDCARD              | PBSUtils.randomNumber | PBSUtils.randomNumber   | AUDIO_BATTR
    }

    def "PBS should be able to override blocked ortb2 attribute by bidder"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(bidders: [GENERIC])
        def ortb2BlockingOverride = new Ortb2BlockingOverride(override: [overrideAttributes], conditions: blockingCondition)
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, [ortb2BlockingOverride], null)
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: GENERIC)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should override blocked ortb2 attribute"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [overrideAttributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | overrideAttributes    | attributeName
        PBSUtils.randomString | PBSUtils.randomString | BADV
        PBSUtils.randomString | PBSUtils.randomString | BAPP
        PBSUtils.randomString | PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber | VIDEO_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber | AUDIO_BATTR
    }

    def "PBS should be able to override blocked ortb2 attribute by media type"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(mediaType: [BANNER])
        def ortb2BlockingOverride = new Ortb2BlockingOverride(override: [overrideAttributes], conditions: blockingCondition)
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, [ortb2BlockingOverride], null)
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: GENERIC)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should override blocked ortb2 attribute"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [overrideAttributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | overrideAttributes    | attributeName
        PBSUtils.randomString | PBSUtils.randomString | BADV
        PBSUtils.randomString | PBSUtils.randomString | BAPP
        PBSUtils.randomString | PBSUtils.randomString | BCAT
        PBSUtils.randomNumber | PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber | BANNER_BATTR
    }

    def "PBS should be able to override block unknown adomain by bidder"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BADV).tap {
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(bidders: [OPENX])

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdomain: true).tap {
            actionOverrides = new Ortb2BlockingActionOverride(blockUnknownAdomain: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BADV): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidWithOutAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            adomain = null
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [bidWithOutAdomain], seat: GENERIC),
                          new SeatBid(bid: [bidWithOutAdomain], seat: OPENX)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only openx seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert response.seatbid.first.seat == OPENX

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings
    }

    def "PBS should be able to override block unknown adomain by media type"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BADV)

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(mediaType: [BANNER])

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdomain: true).tap {
            actionOverrides = new Ortb2BlockingActionOverride(blockUnknownAdomain: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BADV): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidWithOutAdomain = Bid.getDefaultBid(bidRequest.imp.first).tap {
            adomain = null
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [bidWithOutAdomain])]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain banner seatbid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings
    }

    def "PBS should be able to override block unknown adv-cat by bidder"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BCAT).tap {
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(bidders: [OPENX])

        and: "Account in the DB with blocking configuration"
        def ortb2BlockingAttributeConfig = new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdvCat: true).tap {
            actionOverrides = new Ortb2BlockingActionOverride(blockUnknownAdvCat: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BCAT): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidWithOutCat = Bid.getDefaultBid(bidRequest.imp.first).tap {
            cat = null
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [bidWithOutCat], seat: GENERIC),
                          new SeatBid(bid: [bidWithOutCat], seat: OPENX)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only openx seatbid"
        assert response.seatbid.bid.flatten().size() == 1
        assert response.seatbid.first.seat == OPENX

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings
    }

    def "PBS should be able to override block unknown adv-cat by media type"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(BCAT)

        and: "Account in the DB with blocking configuration"
        def blockingCondition = new Ortb2BlockingConditions(mediaType: [BANNER])
        def ortb2BlockingAttributeConfig = new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockUnknownAdvCat: true).tap {
            actionOverrides = new Ortb2BlockingActionOverride(blockUnknownAdvCat: [new Ortb2BlockingOverride(override: false, conditions: blockingCondition)])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(BCAT): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidWithOutCat = Bid.getDefaultBid(bidRequest.imp.first).tap {
            cat = null
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [bidWithOutCat])]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain banner seatbid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings
    }

    def "PBS should be able to override allowed ortb2 attribute for deals by deal ids"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def dealId = PBSUtils.randomNumber
        def blockingCondition = new Ortb2BlockingConditions(dealIds: [dealId.toString()])
        def ortb2BlockingOverride = new Ortb2BlockingOverride(override: [ortb2Attributes], conditions: blockingCondition)
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName, [dealOverrideAttributes]).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, null, [ortb2BlockingOverride])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)
                                            .tap { dealid = dealId }]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only seatbid with proper deal id"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == [ortb2Attributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | dealOverrideAttributes | attributeName
        PBSUtils.randomString | PBSUtils.randomString  | BADV
        PBSUtils.randomString | PBSUtils.randomString  | BAPP
        PBSUtils.randomString | PBSUtils.randomString  | BCAT
        PBSUtils.randomNumber | PBSUtils.randomNumber  | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber  | VIDEO_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber  | AUDIO_BATTR
    }

    def "PBS should use first override when multiple match same condition"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def firstOrtb2BlockingOverride = new Ortb2BlockingOverride(override: [firstOverrideAttributes], conditions: blockingCondition)
        def secondOrtb2BlockingOverride = new Ortb2BlockingOverride(override: [secondOverrideAttributes], conditions: blockingCondition)
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, [firstOrtb2BlockingOverride, secondOrtb2BlockingOverride], null)
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: GENERIC)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should override blocked ortb2 attribute"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [firstOverrideAttributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response should contain proper warning"
        assert response?.ext?.prebid?.modules?.warnings?.ortb2Blocking["ortb2-blocking-bidder-request"] ==
                ["More than one conditions matches request. Bidder: generic, request media types: [banner]"]

        where:
        blockingCondition                                | ortb2Attributes       | firstOverrideAttributes | secondOverrideAttributes | attributeName
        new Ortb2BlockingConditions(bidders: [GENERIC])  | PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BADV
        new Ortb2BlockingConditions(bidders: [GENERIC])  | PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BAPP
        new Ortb2BlockingConditions(bidders: [GENERIC])  | PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BCAT
        new Ortb2BlockingConditions(bidders: [GENERIC])  | PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | BANNER_BATTR
        new Ortb2BlockingConditions(bidders: [GENERIC])  | PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | VIDEO_BATTR
        new Ortb2BlockingConditions(bidders: [GENERIC])  | PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | AUDIO_BATTR
        new Ortb2BlockingConditions(mediaType: [BANNER]) | PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BADV
        new Ortb2BlockingConditions(mediaType: [BANNER]) | PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BAPP
        new Ortb2BlockingConditions(mediaType: [BANNER]) | PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BCAT
        new Ortb2BlockingConditions(mediaType: [BANNER]) | PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | BANNER_BATTR
        new Ortb2BlockingConditions(mediaType: [BANNER]) | PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | VIDEO_BATTR
        new Ortb2BlockingConditions(mediaType: [BANNER]) | PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | AUDIO_BATTR
    }

    def "PBS should prefer non wildcard override when multiple match same condition by bidder"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def firstOrtb2BlockingOverride = new Ortb2BlockingOverride(override: [firstOverrideAttributes], conditions: new Ortb2BlockingConditions(bidders: [BidderName.WILDCARD]))
        def secondOrtb2BlockingOverride = new Ortb2BlockingOverride(override: [secondOverrideAttributes], conditions: new Ortb2BlockingConditions(bidders: [GENERIC]))
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, [firstOrtb2BlockingOverride, secondOrtb2BlockingOverride], null)
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: GENERIC)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should override blocked ortb2 attribute"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [secondOverrideAttributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | firstOverrideAttributes | secondOverrideAttributes | attributeName
        PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BADV
        PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BAPP
        PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BCAT
        PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | VIDEO_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | AUDIO_BATTR
    }

    def "PBS should prefer non wildcard override when multiple match same condition by media type"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def firstOrtb2BlockingOverride = new Ortb2BlockingOverride(override: [firstOverrideAttributes], conditions: new Ortb2BlockingConditions(mediaType: [MediaType.WILDCARD]))
        def secondOrtb2BlockingOverride = new Ortb2BlockingOverride(override: [secondOverrideAttributes], conditions: new Ortb2BlockingConditions(mediaType: [BANNER]))
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig([ortb2Attributes], attributeName).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, [firstOrtb2BlockingOverride, secondOrtb2BlockingOverride], null)
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid = [new SeatBid(bid: [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)], seat: GENERIC)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should override blocked ortb2 attribute"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == [secondOverrideAttributes]*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes       | firstOverrideAttributes | secondOverrideAttributes | attributeName
        PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BADV
        PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BAPP
        PBSUtils.randomString | PBSUtils.randomString   | PBSUtils.randomString    | BCAT
        PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | BANNER_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | VIDEO_BATTR
        PBSUtils.randomNumber | PBSUtils.randomNumber   | PBSUtils.randomNumber    | AUDIO_BATTR
    }

    def "PBS should merge allowed bundle for deals overrides together"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName)

        and: "Account in the DB with blocking configuration"
        def dealId = PBSUtils.randomNumber
        def blockingCondition = new Ortb2BlockingConditions(dealIds: [dealId.toString()])
        def ortb2BlockingOverride = new Ortb2BlockingOverride(override: [ortb2Attributes.last], conditions: blockingCondition)
        def ortb2BlockingAttributeConfig = Ortb2BlockingAttributeConfig.getDefaultConfig(ortb2Attributes, attributeName, [ortb2Attributes.first]).tap {
            enforceBlocks = true
            actionOverrides = Ortb2BlockingActionOverride.getDefaultOverride(attributeName, null, [ortb2BlockingOverride])
        }
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [(attributeName): ortb2BlockingAttributeConfig])
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)
                                            .tap { dealid = dealId }]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain only seatbid with proper deal id"
        assert response.seatbid.bid.flatten().size() == 1
        assert getOrtb2Attributes(response.seatbid.first.bid.first, attributeName) == ortb2Attributes*.toString()

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        ortb2Attributes                                | attributeName
        [PBSUtils.randomString, PBSUtils.randomString] | BADV
        [PBSUtils.randomString, PBSUtils.randomString] | BCAT
        [PBSUtils.randomNumber, PBSUtils.randomNumber] | BANNER_BATTR
        [PBSUtils.randomNumber, PBSUtils.randomNumber] | VIDEO_BATTR
        [PBSUtils.randomNumber, PBSUtils.randomNumber] | AUDIO_BATTR
    }

    def "PBS should not be override from config when ortb2 attribute present in incoming request"() {
        given: "Default bidRequest"
        def bidRequest = getBidRequestForOrtbAttribute(attributeName, bidRequestAttribute)

        and: "Account in the DB with blocking configuration"
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, [ortb2Attributes], attributeName)
        accountDao.save(account)

        and: "Default bidder response with ortb2 attributes"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, ortb2Attributes, attributeName)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS request should contain original ortb2 attribute"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert getOrtb2Attributes(bidderRequest, attributeName) == getOrtb2Attributes(bidRequest, attributeName)

        and: "PBS response shouldn't contain any module errors"
        assert !response?.ext?.prebid?.modules?.errors

        and: "PBS response shouldn't contain any module warning"
        assert !response?.ext?.prebid?.modules?.warnings

        where:
        bidRequestAttribute     | ortb2Attributes       | attributeName
        [PBSUtils.randomString] | PBSUtils.randomString | BADV
        [PBSUtils.randomString] | PBSUtils.randomString | BAPP
        [PBSUtils.randomString] | PBSUtils.randomString | BCAT
        [PBSUtils.randomNumber] | PBSUtils.randomNumber | BANNER_BATTR
        [PBSUtils.randomNumber] | PBSUtils.randomNumber | VIDEO_BATTR
        [PBSUtils.randomNumber] | PBSUtils.randomNumber | AUDIO_BATTR
        [PBSUtils.randomNumber] | PBSUtils.randomNumber | BTYPE
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with rejected advertiser blocked status code"() {
        given: "Default bidRequest with returnAllBidStatus attribute"
        def bidRequest = getBidRequestForOrtbAttribute(BADV).tap {
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Default bidder response with aDomain"
        def aDomain = PBSUtils.randomString
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid = [getBidWithOrtb2Attribute(bidRequest.imp.first, aDomain, BADV)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB with blocking configuration"
        def attributes = [(BADV): new Ortb2BlockingAttributeConfig(enforceBlocks: true, blockedAdomain: [aDomain])]
        def account = getAccountWithOrtb2BlockingConfig(bidRequest.accountId, attributes)
        accountDao.save(account)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for the called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == ErrorType.GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_ADVERTISER_BLOCKED
    }

    private static Account getAccountWithOrtb2BlockingConfig(String accountId, Object ortb2Attributes, Ortb2BlockingAttribute attributeName) {
        getAccountWithOrtb2BlockingConfig(accountId, [(attributeName): Ortb2BlockingAttributeConfig.getDefaultConfig(ortb2Attributes, attributeName)])
    }

    private static Account getAccountWithOrtb2BlockingConfig(String accountId, Map<Ortb2BlockingAttribute, Ortb2BlockingAttributeConfig> attributes) {
        def blockingConfig = new Ortb2BlockingConfig(attributes: attributes)
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [BIDDER_REQUEST, RAW_BIDDER_RESPONSE])
        def moduleConfig = new PbsModulesConfig(ortb2Blocking: blockingConfig)
        def accountHooksConfig = new AccountHooksConfiguration(executionPlan: executionPlan, modules: moduleConfig)
        def accountConfig = new AccountConfig(hooks: accountHooksConfig)
        new Account(uuid: accountId, config: accountConfig)
    }

    private static BidRequest getBidRequestForOrtbAttribute(Ortb2BlockingAttribute attribute, List<Object> attributeValue = null) {
        switch (attribute) {
            case BADV:
                return BidRequest.defaultBidRequest.tap {
                    badv = attributeValue as List<String>
                }
            case BAPP:
                return BidRequest.defaultBidRequest.tap {
                    bapp = attributeValue as List<String>
                }
            case BANNER_BATTR:
                return BidRequest.defaultBidRequest.tap {
                    imp[0].banner.battr = attributeValue as List<Integer>
                }
            case VIDEO_BATTR:
                return BidRequest.defaultVideoRequest.tap {
                    imp[0].video.battr = attributeValue as List<Integer>
                }
            case AUDIO_BATTR:
                return BidRequest.defaultAudioRequest.tap {
                    imp[0].audio.battr = attributeValue as List<Integer>
                }
            case BCAT:
                return BidRequest.defaultBidRequest.tap {
                    bcat = attributeValue as List<String>
                }
            case BTYPE:
                return BidRequest.defaultBidRequest.tap {
                    imp[0].banner.btype = attributeValue as List<Integer>
                }
            default:
                throw new IllegalArgumentException("Unknown ortb2 attribute: $attribute")
        }
    }

    private static Bid getBidWithOrtb2Attribute(Imp imp, Object ortb2Attributes, Ortb2BlockingAttribute attributeName) {
        Bid.getDefaultBid(imp).tap {
            switch (attributeName) {
                case BADV:
                    adomain = (ortb2Attributes instanceof List) ? ortb2Attributes : [ortb2Attributes]
                    break
                case BAPP:
                    bundle = (ortb2Attributes instanceof List) ? ortb2Attributes.first : ortb2Attributes
                    break
                case BANNER_BATTR:
                    attr = (ortb2Attributes instanceof List) ? ortb2Attributes : [ortb2Attributes]
                    break
                case VIDEO_BATTR:
                    attr = (ortb2Attributes instanceof List) ? ortb2Attributes : [ortb2Attributes]
                    break
                case AUDIO_BATTR:
                    attr = (ortb2Attributes instanceof List) ? ortb2Attributes : [ortb2Attributes]
                    break
                case BCAT:
                    cat = (ortb2Attributes instanceof List) ? ortb2Attributes : [ortb2Attributes]
                    break
                case BTYPE:
                    break
                default:
                    throw new IllegalArgumentException("Unknown ortb2 attribute: $attributeName")
            }
        }
    }

    private static List<String> getOrtb2Attributes(BidRequest bidRequest, Ortb2BlockingAttribute attributeName) {
        switch (attributeName) {
            case BADV:
                return bidRequest.badv
            case BAPP:
                return bidRequest.bapp
            case BANNER_BATTR:
                return bidRequest.imp[0].banner.battr*.toString()
            case VIDEO_BATTR:
                return bidRequest.imp[0].video.battr*.toString()
            case AUDIO_BATTR:
                return bidRequest.imp[0].audio.battr*.toString()
            case BCAT:
                return bidRequest.bcat
            case BTYPE:
                return bidRequest.imp[0].banner.btype*.toString()
            default:
                throw new IllegalArgumentException("Unknown attribute type: $attributeName")
        }
    }

    private static List<String> getOrtb2Attributes(Bid bid, Ortb2BlockingAttribute attributeName) {
        switch (attributeName) {
            case BADV:
                return bid.adomain
            case BAPP:
                return [bid.bundle]
            case BANNER_BATTR:
                return bid.attr*.toString()
            case VIDEO_BATTR:
                return bid.attr*.toString()
            case AUDIO_BATTR:
                return bid.attr*.toString()
            case BCAT:
                return bid.cat
            case BTYPE:
                return null
            default:
                throw new IllegalArgumentException("Unknown attribute type: $attributeName")
        }
    }
}
