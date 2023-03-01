package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppExtData
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.BidderConfig
import org.prebid.server.functional.model.request.auction.BidderConfigOrtb
import org.prebid.server.functional.model.request.auction.ExtPrebidBidderConfig
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.SiteExt
import org.prebid.server.functional.model.request.auction.SiteExtData
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.IN
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.INTERSECTS
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.MATCHES
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_BUYER_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_BUYER_IDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_KEYWORDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_LANGUAGE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_UID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_UIDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_KEYWORDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_YOB

class TargetingFirstPartyDataSpec extends BasePgSpec {

    @Shared
    String stringTargetingValue = PBSUtils.randomString
    @Shared
    Integer integerTargetingValue = PBSUtils.randomNumber

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should support both scalar and array String inputs by '#ufpdTargetingType' for INTERSECTS matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                ext = new UserExt(data: userExtData)
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(ufpdTargetingType, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        ufpdTargetingType | userExtData
        UFPD_BUYER_UID    | new UserExtData(buyeruid: stringTargetingValue)
        UFPD_KEYWORDS     | new UserExtData(keywords: [stringTargetingValue])
    }

    def "PBS should support both scalar and array Integer inputs by '#ufpdTargetingType' for INTERSECTS matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                ext = new UserExt(data: userExtData)
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(ufpdTargetingType, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        ufpdTargetingType | userExtData
        UFPD_BUYER_UID    | new UserExtData(buyeruid: stringTargetingValue)
        UFPD_BUYER_UIDS   | new UserExtData(buyeruids: [stringTargetingValue])
    }

    def "PBS should support taking Site First Party Data from #place source"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        place               | bidRequest
        "imp[].ext.context" | BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                ext.context = new ImpExtContext(data: new ImpExtContextData(language: stringTargetingValue))
            }]
        }
        "site"              | BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite.tap {
                ext = new SiteExt(data: new SiteExtData(language: stringTargetingValue))
            }
        }
        "app"               | BidRequest.defaultBidRequest.tap {
            app = new App(id: PBSUtils.randomString).tap {
                ext = new AppExt(data: new AppExtData(language: stringTargetingValue))
            }
        }
        "imp[].ext.data"    | BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                ext.data = new ImpExtContextData(language: stringTargetingValue)
            }]
        }
    }

    def "PBS should support String array input for Site First Party Data to be matched by INTERSECTS matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext.context = new ImpExtContext(data: new ImpExtContextData(keywords: [stringTargetingValue]))
            }]
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_KEYWORDS, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support both scalar and array Integer inputs in Site First Party Data ('#targetingType') by INTERSECTS matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext.context = new ImpExtContext(data: impExtContextData)
            }]
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, INTERSECTS, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType  | impExtContextData
        SFPD_BUYER_ID  | new ImpExtContextData(buyerId: integerTargetingValue)
        SFPD_BUYER_IDS | new ImpExtContextData(buyerIds: [integerTargetingValue])
    }

    def "PBS shouldn't throw a NPE for Site First Party Data when its Ext is absent and targeting INTERSECTS matching type is selected"() {
        given: "Bid request with set site first party data in bidRequest.site"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite.tap {
                keywords = stringTargetingValue
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_KEYWORDS, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS successfully processed request"
        notThrown(PrebidServerException)

        and: "PBS hasn't had PG auction as request targeting is not specified in the right place"
        assert !auctionResponse.ext?.debug?.pgmetrics
    }

    def "PBS should support taking User FPD from bidRequest.user by #matchingFunction matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                updateUserFieldGeneric(it)
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(ufpdTargetingType, matchingFunction, [targetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        ufpdTargetingType | updateUserFieldGeneric                 | targetingValue        | matchingFunction
        UFPD_BUYER_UID    | { it.buyeruid = stringTargetingValue } | stringTargetingValue  | INTERSECTS
        UFPD_BUYER_UID    | { it.buyeruid = stringTargetingValue } | stringTargetingValue  | IN
        UFPD_YOB          | { it.yob = integerTargetingValue }     | integerTargetingValue | INTERSECTS
        UFPD_YOB          | { it.yob = integerTargetingValue }     | integerTargetingValue | IN
    }

    def "PBS should support taking User FPD from bidRequest.user by MATCHES matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                it.buyeruid = stringTargetingValue
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_BUYER_UID, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should be able to match site FPD targeting taken from different sources by INTERSECTS matching function"() {
        given: "Bid request with set site FPD in different request places"
        def bidRequest = getSiteFpdBidRequest(siteLanguage, appLanguage, impLanguage)

        and: "Planner response with INTERSECTS 1 of site FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, INTERSECTS, [stringTargetingValue, PBSUtils.randomString])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        siteLanguage         | appLanguage          | impLanguage
        stringTargetingValue | null                 | PBSUtils.randomString
        null                 | stringTargetingValue | PBSUtils.randomString
        null                 | null                 | stringTargetingValue
    }

    def "PBS should be able to match site FPD targeting taken from different sources by MATCHES matching function"() {
        given: "Bid request with set site FPD in different request places"
        def bidRequest = getSiteFpdBidRequest(siteLanguage, appLanguage, impLanguage)

        and: "Planner response with MATCHES 1 of site FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        siteLanguage         | appLanguage          | impLanguage
        stringTargetingValue | null                 | PBSUtils.randomString
        null                 | stringTargetingValue | PBSUtils.randomString
        null                 | null                 | stringTargetingValue
    }

    def "PBS should be able to match site FPD targeting taken from different sources by IN matching function"() {
        given: "Bid request with set site FPD in different request places"
        def siteLanguage = PBSUtils.randomString
        def appLanguage = PBSUtils.randomString
        def impLanguage = PBSUtils.randomString
        def bidRequest = getSiteFpdBidRequest(siteLanguage, appLanguage, impLanguage)

        and: "Planner response with IN all of site FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, IN, [siteLanguage, appLanguage, impLanguage, PBSUtils.randomString])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should be able to match user FPD targeting taken from different sources by MATCHES matching function"() {
        given: "Bid request with set user FPD in different request places"
        def bidRequest = getUserFpdBidRequest(userBuyerUid, userExtDataBuyerUid)

        and: "Planner response with MATCHES 1 of user FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_BUYER_UID, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize

        where:
        userBuyerUid          | userExtDataBuyerUid
        stringTargetingValue  | PBSUtils.randomString
        PBSUtils.randomString | stringTargetingValue
    }

    def "PBS should be able to match user FPD targeting taken from different sources by IN matching function"() {
        given: "Bid request with set user FPD in different request places"
        def userBuyerUid = PBSUtils.randomString
        def userExtDataBuyerUid = PBSUtils.randomString
        def bidRequest = getUserFpdBidRequest(userBuyerUid, userExtDataBuyerUid)

        and: "Planner response with IN all of user FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_BUYER_UID, IN, [userBuyerUid, userExtDataBuyerUid, PBSUtils.randomString])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize
    }

    def "PBS should support targeting by SITE First Party Data when request ext prebid bidder config is given"() {
        given: "Bid request with set Site specific bidder config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def site = new Site().tap {
                ext = new SiteExt(data: new SiteExtData(language: stringTargetingValue))
            }
            def bidderConfig = new ExtPrebidBidderConfig(bidders: [GENERIC],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(site: site)))
            ext = new BidRequestExt(prebid: new Prebid(debug: 1, bidderConfig: [bidderConfig]))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, matchingFunction, matchingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        matchingFunction | matchingValue
        INTERSECTS       | [stringTargetingValue, PBSUtils.randomString]
        MATCHES          | stringTargetingValue
    }

    def "PBS should support targeting by USER First Party Data when request ext prebid bidder config is given"() {
        given: "Bid request with set User specific bidder config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def user = new User().tap {
                ext = new UserExt(data: new UserExtData(buyeruid: stringTargetingValue))
            }
            def bidderConfig = new ExtPrebidBidderConfig(bidders: [GENERIC],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(user: user)))
            ext = new BidRequestExt(prebid: new Prebid(debug: 1, bidderConfig: [bidderConfig]))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_BUYER_UID, matchingFunction, matchingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        matchingFunction | matchingValue
        INTERSECTS       | [stringTargetingValue, PBSUtils.randomString]
        MATCHES          | stringTargetingValue
    }

    def "PBS shouldn't target by SITE First Party Data when request ext prebid bidder config with not matched bidder is given"() {
        given: "Bid request with request not matched bidder"
        def notMatchedBidder = APPNEXUS
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def site = new Site().tap {
                ext = new SiteExt(data: new SiteExtData(language: stringTargetingValue))
            }
            def bidderConfig = new ExtPrebidBidderConfig(bidders: [notMatchedBidder],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(site: site)))
            ext = new BidRequestExt(prebid: new Prebid(debug: 1, bidderConfig: [bidderConfig]))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, matchingFunction, matchingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG auction"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        matchingFunction | matchingValue
        INTERSECTS       | [stringTargetingValue, PBSUtils.randomString]
        MATCHES          | stringTargetingValue
    }

    def "PBS shouldn't target by USER First Party Data when request ext prebid bidder config with not matched bidder is given"() {
        given: "Bid request with request not matched bidder"
        def notMatchedBidder = APPNEXUS
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def user = new User().tap {
                ext = new UserExt(data: new UserExtData(buyeruid: stringTargetingValue))
            }
            def bidderConfig = new ExtPrebidBidderConfig(bidders: [notMatchedBidder],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(user: user)))
            ext = new BidRequestExt(prebid: new Prebid(debug: 1, bidderConfig: [bidderConfig]))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_BUYER_UID, matchingFunction, matchingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG auction"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        matchingFunction | matchingValue
        INTERSECTS       | [stringTargetingValue, PBSUtils.randomString]
        MATCHES          | stringTargetingValue
    }

    def "PBS should support targeting by SITE First Party Data when a couple of request ext prebid bidder configs are given"() {
        given: "Bid request with 1 not matched Site specific bidder config and 1 matched"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def bidderConfigSite = new Site().tap {
                ext = new SiteExt(data: new SiteExtData(language: stringTargetingValue))
            }
            def bidderConfig = new ExtPrebidBidderConfig(bidders: [GENERIC],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(site: bidderConfigSite)))
            ext = new BidRequestExt(prebid: new Prebid(debug: 1, bidderConfig: [bidderConfig]))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, matchingFunction, matchingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        matchingFunction | matchingValue
        INTERSECTS       | [stringTargetingValue, PBSUtils.randomString]
        MATCHES          | stringTargetingValue
    }

    def "PBS should support targeting by USER First Party Data when a couple of request ext prebid bidder configs are given"() {
        given: "Bid request with 1 not matched User specific bidder config and 1 matched"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def bidderConfigUser = new User().tap {
                ext = new UserExt(data: new UserExtData(buyeruid: stringTargetingValue))
            }
            def bidderConfig = new ExtPrebidBidderConfig(bidders: [GENERIC],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(user: bidderConfigUser)))
            ext = new BidRequestExt(prebid: new Prebid(debug: 1, bidderConfig: [bidderConfig]))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_BUYER_UID, matchingFunction, matchingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        matchingFunction | matchingValue
        INTERSECTS       | [stringTargetingValue, PBSUtils.randomString]
        MATCHES          | stringTargetingValue
    }

    private BidRequest getSiteFpdBidRequest(String siteLanguage, String appLanguage, String impLanguage) {
        BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite.tap {
                ext = new SiteExt(data: new SiteExtData(language: siteLanguage))
            }
            app = appLanguage != null
                    ? new App(id: PBSUtils.randomString).tap {
                        ext = new AppExt(data: new AppExtData(language: appLanguage))
                    }
                    : null
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext.context = new ImpExtContext(data: new ImpExtContextData(language: impLanguage))
            }]
        }
    }

    private BidRequest getUserFpdBidRequest(String userBuyerUid, String userExtDataBuyerUid) {
        BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                buyeruid = userBuyerUid
                ext = new UserExt(data: new UserExtData(buyeruid: userExtDataBuyerUid))
            }
        }
    }
}
