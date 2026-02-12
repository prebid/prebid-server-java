package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidderConfig
import org.prebid.server.functional.model.request.auction.BidderConfigOrtb
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.EidPermission
import org.prebid.server.functional.model.request.auction.ExtPrebidBidderConfig
import org.prebid.server.functional.model.request.auction.ExtRequestPrebidData
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.request.auction.Uid
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtPrebid
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.response.BidderErrorCode
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class BidderInsensitiveCaseSpec extends BaseSpec {

    def "PBS auction should match imp[0].ext.prebid.bidder name when bidder name with another case strategy"() {
        given: "Default basic BidRequest with GeNeRIC bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should preform bidder request"
        assert bidder.getBidderRequest(bidRequest.id)
    }

    def "PBS auction should match with storedBidResponse.bidder name when original bidder name in another case strategy"() {
        given: "Default basic BidRequest with GeNeRIC bidder"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            imp[0].ext.prebid.tap {
                storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC_CAMEL_CASE)]
            }
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest, GENERIC_CAMEL_CASE)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings
    }

    def "PBS auction should match original bidder name with requested bidder in ext.prebid.data.bidders when request bidder in another case strategy"() {
        given: "Default bid request"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            ext.prebid.tap {
                data = new ExtRequestPrebidData(bidders: [extRequestPrebidDataBidder])
                bidderConfig = [new ExtPrebidBidderConfig(bidders: [prebidBidderConfigBidder], config: new BidderConfig(
                        ortb2: new BidderConfigOrtb(site: Site.configFPDSite, user: User.configFPDUser)))]
            }
        }

        when: "PBS processes amp request"
        defaultPbsService.sendAuctionRequest(ampStoredRequest)

        then: "Bidder request should contain certain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        def ortb2 = ampStoredRequest.ext.prebid.bidderConfig[0].config.ortb2
        verifyAll(bidderRequest) {
            ortb2.site.name == site.name
            ortb2.site.domain == site.domain
            ortb2.site.cat == site.cat
            ortb2.site.sectionCat == site.sectionCat
            ortb2.site.pageCat == site.pageCat
            ortb2.site.page == site.page
            ortb2.site.ref == site.ref
            ortb2.site.search == site.search
            ortb2.site.keywords == site.keywords
            ortb2.site.ext.data.language == site.ext.data.language

            ortb2.user.yob == user.yob
            ortb2.user.gender == user.gender
            ortb2.user.keywords == user.keywords
            ortb2.user.ext.data.keywords == user.ext.data.keywords
            ortb2.user.ext.data.buyeruid == user.ext.data.buyeruid
            ortb2.user.ext.data.buyeruids == user.ext.data.buyeruids
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        verifyAll(bidderRequest) {
            !imp[0].ext.rp
        }

        where:
        extRequestPrebidDataBidder | prebidBidderConfigBidder
        GENERIC_CAMEL_CASE.value   | GENERIC_CAMEL_CASE
    }

    def "PBS should match adjust bid price bidder name when original bidder in another case strategy"() {
        given: "Default bid request with bid adjustment"
        def bidAdjustmentFactor = 0.9
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC_CAMEL_CASE): bidAdjustmentFactor])
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, GENERIC_CAMEL_CASE)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first()?.bid?.first()?.price == bidResponse.seatbid.first().bid.first().price *
                bidAdjustmentFactor
    }

    def "PBS should match bidder in ext.prebid.data.eidpermissions.bidders when original bidder in another case strategy"() {
        given: "Default bid request with bid adjustment"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            def url = "sharedid.org"
            user = User.getRootFPDUser().tap {
                eids = [new Eid(source: url, uids: [new Uid(id: "2")])]
            }
            ext.prebid.data = new ExtRequestPrebidData(eidpermissions:
                    [new EidPermission(source: url, bidders: [GENERIC_CAMEL_CASE])])
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should preform bidder request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids
    }

    def "PBS should return seatbid[].bid[].ext.prebid.targeting for non-winning bid in multi-bid response bidder name in another case strategy"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            ext.prebid.targeting = new Targeting(includeBidderKeys: true)
        }

        and: "Set maxbids = 2 for default bidder"
        def maxBids = 2
        def multiBid = new MultiBid(bidder: GENERIC_CAMEL_CASE, maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        bidRequest.ext.prebid.multibid = [multiBid]

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, GENERIC_CAMEL_CASE)
        def anotherBid = Bid.getDefaultBid(bidRequest.imp.first()).tap {
            price = bidResponse.seatbid.first().bid.first().price - 0.1
        }
        bidResponse.seatbid.first().bid << anotherBid

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return targeting for non-winning bid"
        assert response.seatbid?.first()?.bid?.last()?.ext?.prebid?.targeting
    }

    def "PBS should populate bidder request buyeruid from buyeruids when buyeruids with appropriate bidder present in request"() {
        given: "Bid request with buyeruids"
        def buyeruid = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC_CAMEL_CASE): buyeruid])))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain buyeruid from the user.ext.prebid.buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == buyeruid
    }

    def "PBS should be able to match requested bidder with original bidder name in ext.prebid.aliase"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            ext.prebid.aliases = [(ALIAS.value): GENERIC_CAMEL_CASE]
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the same url for both"
        def responseDebug = response.ext.debug
        assert responseDebug.httpcalls.size() == 2
        assert responseDebug.httpcalls[GENERIC_CAMEL_CASE.value]*.uri == responseDebug.httpcalls[ALIAS.value]*.uri

        and: "Resolved request should contain aliases as in request"
        assert responseDebug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases

        and: "Bidder request should contain request per-alies"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
    }

    def "PBS should respond with requested bidder when requested bidder in another case strategy"() {
        given: "Bid request with alwaysIncludeDeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: false, alwaysIncludeDeals: true)
        }

        and: "Bid response with 2 bids where deal bid has higher price"
        def bidPrice = PBSUtils.randomPrice
        def dealBidPrice = bidPrice + 1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, GENERIC_CAMEL_CASE).tap {
            seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp[0]).tap { it.price = bidPrice }
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber
            seatbid[0].bid[0].price = dealBidPrice
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)
        def bidderName = GENERIC_CAMEL_CASE.value

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        def notBidderKeys = targetingKeyMap.findAll { !it.key.endsWith(bidderName) }
        notBidderKeys.each { assert targetingKeyMap.containsKey("${it.key}_$bidderName" as String) }
    }

    def "PBS should respond errors with same bidder name which bidder name came in request with another case strategy"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                ["adapter-defaults.enabled": "false",
                 "adapters.generic.enabled": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.GENERIC_CAMEL_CASE]*.code == [2]
    }

    def "PBS should respond warnings with same bidder name which bidder name came in request with another case strategy"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"     : "true",
                 "adapters.generic.meta-info.app-media-types": ""])

        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain empty seatbid"
        assert response.seatbid.isEmpty()

        and: "Response should contain error"
        assert response.ext?.warnings[ErrorType.GENERIC_CAMEL_CASE]*.code == [BidderErrorCode.BAD_INPUT]
        assert response.ext?.warnings[ErrorType.GENERIC_CAMEL_CASE]*.message ==
                ["Bidder does not support any media types."]
    }

    def "PBS should respond responsetimemillis with same bidder name which bidder name came in request with another case strategy"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
        }

        when: "PBS processes auction request"
        def auctionRequest = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should preform bidder request"
        assert auctionRequest.ext.responsetimemillis[GENERIC_CAMEL_CASE.value]
    }

    def "PBS should respond httpcalls with same bidder name which bidder name came in request with another case strategy"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                genericCamelCase = new Generic()
                generic = null
            }
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain http calls"
        assert response.ext?.debug?.httpcalls[GENERIC_CAMEL_CASE.value]
    }

    def "PBS cookie sync request shouldn't reflect error when request bidder in another case strategy"() {
        given: "Default cookie sync request"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC_CAMEL_CASE]
        }

        when: "PBS processes cookie sync request"
        def response = defaultPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should return requested bidder"
        assert response.getBidderUserSync(GENERIC_CAMEL_CASE)
    }
}
