package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppExtData
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
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

import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.IN
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.INTERSECTS
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.MATCHES
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_BUYER_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_BUYER_IDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_KEYWORDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_LANGUAGE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_IDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_KEYWORDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_LANGUAGE
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
        UFPD_LANGUAGE     | new UserExtData(language: stringTargetingValue)
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
                                              .addTargeting(ufpdTargetingType, INTERSECTS, [integerTargetingValue])
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
        UFPD_BUYER_ID     | new UserExtData(buyerid: integerTargetingValue)
        UFPD_BUYER_IDS    | new UserExtData(buyerids: [integerTargetingValue])
    }

    def "PBS should support taking Site First Party Data from 3 different sources"() {
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
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp = [Imp.defaultImpression.tap {
                        banner = Banner.defaultBanner
                        ext.context = new ImpExtContext(data: new ImpExtContextData(language: stringTargetingValue))
                    }]
                },
                BidRequest.defaultBidRequest.tap {
                    site = Site.defaultSite.tap {
                        ext = new SiteExt(data: new SiteExtData(language: stringTargetingValue))
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    app = new App(id: PBSUtils.randomString).tap {
                        ext = new AppExt(data: new AppExtData(language: stringTargetingValue))
                    }
                }
        ]
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
        UFPD_LANGUAGE     | { it.language = stringTargetingValue } | stringTargetingValue  | INTERSECTS
        UFPD_LANGUAGE     | { it.language = stringTargetingValue } | stringTargetingValue  | IN
        UFPD_YOB          | { it.yob = integerTargetingValue }     | integerTargetingValue | INTERSECTS
        UFPD_YOB          | { it.yob = integerTargetingValue }     | integerTargetingValue | IN
    }

    def "PBS should support taking User FPD from bidRequest.user by MATCHES matching function"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                it.language = stringTargetingValue
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_LANGUAGE, MATCHES, stringTargetingValue)
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
        siteLanguage          | appLanguage           | impLanguage
        stringTargetingValue  | PBSUtils.randomString | PBSUtils.randomString
        PBSUtils.randomString | stringTargetingValue  | PBSUtils.randomString
        PBSUtils.randomString | PBSUtils.randomString | stringTargetingValue
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
        siteLanguage          | appLanguage           | impLanguage
        stringTargetingValue  | PBSUtils.randomString | PBSUtils.randomString
        PBSUtils.randomString | stringTargetingValue  | PBSUtils.randomString
        PBSUtils.randomString | PBSUtils.randomString | stringTargetingValue
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
        def bidRequest = getUserFpdBidRequest(userLanguage, userExtDataLanguage)

        and: "Planner response with MATCHES 1 of user FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_LANGUAGE, MATCHES, stringTargetingValue)
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
        userLanguage          | userExtDataLanguage
        stringTargetingValue  | PBSUtils.randomString
        PBSUtils.randomString | stringTargetingValue
    }

    def "PBS should be able to match user FPD targeting taken from different sources by IN matching function"() {
        given: "Bid request with set user FPD in different request places"
        def userLanguage = PBSUtils.randomString
        def userExtDataLanguage = PBSUtils.randomString
        def bidRequest = getUserFpdBidRequest(userLanguage, userExtDataLanguage)

        and: "Planner response with IN all of user FPD values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(UFPD_LANGUAGE, IN, [userLanguage, userExtDataLanguage, PBSUtils.randomString])
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

    private BidRequest getSiteFpdBidRequest(String siteLanguage, String appLanguage, String impLanguage) {
        BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite.tap {
                ext = new SiteExt(data: new SiteExtData(language: siteLanguage))
            }
            app = new App(id: PBSUtils.randomString).tap {
                ext = new AppExt(data: new AppExtData(language: appLanguage))
            }
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext.context = new ImpExtContext(data: new ImpExtContextData(language: impLanguage))
            }]
        }
    }

    private BidRequest getUserFpdBidRequest(String userLanguage, String userExtDataLanguage) {
        BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                language = userLanguage
                ext = new UserExt(data: new UserExtData(language: userExtDataLanguage))
            }
        }
    }
}
