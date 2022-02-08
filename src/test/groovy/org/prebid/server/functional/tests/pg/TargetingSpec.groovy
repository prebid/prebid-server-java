package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.bidder.Rubicon
import org.prebid.server.functional.model.deals.lineitem.LineItemSize
import org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator
import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppExtData
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Bidder
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExt
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.SiteExt
import org.prebid.server.functional.model.request.auction.SiteExtData
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.request.auction.UserTime
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared
import spock.lang.Unroll

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static java.time.temporal.WeekFields.SUNDAY_START
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.NOT
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.OR
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.UPPERCASE_AND
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.IN
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.INTERSECTS
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.MATCHES
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.WITHIN
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_AD_SLOT
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_MEDIA_TYPE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_SIZE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.APP_BUNDLE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.BIDP_ACCOUNT_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.DOW
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.HOUR
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.INVALID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.PAGE_POSITION
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.REFERRER
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_BUYER_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_BUYER_IDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_KEYWORDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SFPD_LANGUAGE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SITE_DOMAIN
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_IDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_KEYWORDS
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_LANGUAGE
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class TargetingSpec extends BasePgSpec {

    @Shared
    String stringTargetingValue = PBSUtils.randomString
    @Shared
    Integer integerTargetingValue = PBSUtils.randomNumber

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    @Unroll
    def "PBS should invalidate line items when targeting has #reason"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = targeting
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG deals auction as line item hasn't passed validation"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        reason                                         | targeting

        "two root nodes"                               | Targeting.invalidTwoRootNodesTargeting

        "invalid boolean operator"                     | new Targeting.Builder(BooleanOperator.INVALID).addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                                       .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                                                                       .build()

        "uppercase boolean operator"                   | new Targeting.Builder(UPPERCASE_AND).addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                             .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                                                             .build()

        "invalid targeting type"                       | Targeting.defaultTargetingBuilder
                                                                  .addTargeting(INVALID, INTERSECTS, [PBSUtils.randomString])
                                                                  .build()

        "'in' matching type value as not list"         | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, IN, BANNER)
                                                                                .build()

        "'intersects' matching type value as not list" | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, BANNER)
                                                                                .build()

        "'within' matching type value as not list"     | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, WITHIN, BANNER)
                                                                                .build()

        "'matches' matching type value as list"        | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, MATCHES, [BANNER])
                                                                                .build()

        "null targeting height and width"              | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [new LineItemSize(w: null, h: null)])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                                                .build()
    }

    @Unroll
    def "PBS should invalidate line items with not supported '#matchingFunction' matching function by '#targetingType' targeting type"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, matchingFunction, [PBSUtils.randomString])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG deals auction as line item hasn't passed validation"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        matchingFunction | targetingType
        INTERSECTS       | SITE_DOMAIN
        WITHIN           | SITE_DOMAIN
        INTERSECTS       | REFERRER
        WITHIN           | REFERRER
        INTERSECTS       | APP_BUNDLE
        WITHIN           | APP_BUNDLE
        INTERSECTS       | AD_UNIT_AD_SLOT
        WITHIN           | AD_UNIT_AD_SLOT
    }

    @Unroll
    def "PBS should support line item targeting by string '#targetingType' targeting type"() {
        given: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType | bidRequest

        REFERRER      | BidRequest.defaultBidRequest.tap {
            site.page = stringTargetingValue
        }

        APP_BUNDLE    | BidRequest.defaultBidRequest.tap {
            app = App.defaultApp.tap { bundle = stringTargetingValue }
        }

        UFPD_LANGUAGE | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                language = stringTargetingValue
            }
        }
    }

    @Unroll
    def "PBS should support both scalar and array String inputs by User First Party Data '#targetingType' for intersects matching function"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType | bidRequest

        UFPD_LANGUAGE | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                ext = new UserExt(data: new UserExtData(language: stringTargetingValue))
            }
        }

        UFPD_KEYWORDS | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                ext = new UserExt(data: new UserExtData(keywords: [stringTargetingValue]))
            }
        }
    }

    @Unroll
    def "PBS should support both scalar and array Integer inputs by User First Party Data '#targetingType' for intersects matching function"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, INTERSECTS, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType  | bidRequest

        UFPD_BUYER_ID  | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                ext = new UserExt(data: new UserExtData(buyerid: integerTargetingValue))
            }
        }

        UFPD_BUYER_IDS | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                ext = new UserExt(data: new UserExtData(buyerids: [integerTargetingValue]))
            }
        }
    }

    @Unroll
    def "PBS should support taking Site First Party Data from 3 different sources"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SFPD_LANGUAGE, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

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
                    app = App.defaultApp.tap {
                        ext = new AppExt(data: new AppExtData(language: stringTargetingValue))
                    }
                }
        ]
    }

    def "PBS should support String array input for Site First Party Data to be matched by intersects matching function"() {
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
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    @Unroll
    def "PBS should support both scalar and array Integer inputs in Site First Party Data ('#targetingType') by intersects matching function"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, INTERSECTS, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType  | bidRequest
        SFPD_BUYER_ID  | BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext.context = new ImpExtContext(data: new ImpExtContextData(buyerid: integerTargetingValue))
            }]
        }

        SFPD_BUYER_IDS | BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext.context = new ImpExtContext(data: new ImpExtContextData(buyerids: [integerTargetingValue]))
            }]
        }
    }

    def "PBS should support targeting matching by bidder parameters"() {
        given: "Bid request with specified bidder parameter"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext = ImpExt.defaultImpExt
                ext.prebid.bidder = new Bidder(rubicon: Rubicon.default.tap { accountId = integerTargetingValue })
            }]
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].source = RUBICON.name().toLowerCase()
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(BIDP_ACCOUNT_ID, INTERSECTS, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    @Unroll
    def "PBS doesn't throw a NPE for '#targetingType' when its Ext is absent and targeting Intersects matching type is selected"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, INTERSECTS, [stringTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS successfully processed request"
        notThrown(PrebidServerException)

        and: "PBS hasn't had PG auction as request targeting is not specified in the right place"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        targetingType | bidRequest
        SFPD_KEYWORDS | BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite.tap {
                keywords = stringTargetingValue
            }
        }

        UFPD_LANGUAGE | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                language = stringTargetingValue
            }
        }
    }

    def "PBS should support line item targeting by page position targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.pos = integerTargetingValue
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(PAGE_POSITION, IN, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by userdow targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def weekDay = ZonedDateTime.now(ZoneId.from(UTC)).dayOfWeek.get(SUNDAY_START.dayOfWeek())
            user = User.defaultUser.tap {
                ext = new UserExt(time: new UserTime(userdow: weekDay))
            }
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(DOW, IN, [ZonedDateTime.now(ZoneId.from(UTC)).dayOfWeek.get(SUNDAY_START.dayOfWeek())])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by userhour targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def hour = ZonedDateTime.now(ZoneId.from(UTC)).hour
            user = User.defaultUser.tap {
                ext = new UserExt(time: new UserTime(userhour: hour))
            }
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(HOUR, IN, [ZonedDateTime.now(ZoneId.from(UTC)).hour])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by '#targetingType' targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(HOUR, IN, [ZonedDateTime.now(ZoneId.from(UTC)).hour])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType                       | targetingValue

        "'\$or' root node with one match"   | new Targeting.Builder(OR).addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                       .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [VIDEO])
                                                                       .build()

        "'\$not' root node without matches" | new Targeting.Builder(NOT).buildNotBooleanOperatorTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [VIDEO])
    }

    @Unroll
    def "PBS should support line item domain targeting by #domainTargetingType"() {
        given: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SITE_DOMAIN, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize

        and: "Targeting recorded as matched"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedDomainTargeting?.size() == lineItemSize

        where:
        domainTargetingType     | bidRequest

        "site domain"           | BidRequest.defaultBidRequest.tap {
            site.domain = stringTargetingValue
        }

        "site publisher domain" | BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap { domain = stringTargetingValue }
        }
    }

    @Unroll
    def "PBS should support line item domain targeting"() {
        given: "Bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = siteDomain
            site.publisher = Publisher.defaultPublisher.tap { domain = sitePublisherDomain }
        }
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SITE_DOMAIN, IN, [siteDomain])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize

        and: "Targeting recorded as matched"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedDomainTargeting?.size() == lineItemSize

        where:
        siteDomain                | sitePublisherDomain
        "www.example.com"         | null
        "https://www.example.com" | null
        "www.example.com"         | "example.com"
    }

    @Unroll
    def "PBS should appropriately match '\$or', '\$not' line items targeting root node rules"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = targeting
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG deals auction as targeting differs"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        targeting << [new Targeting.Builder(OR).addTargeting(AD_UNIT_SIZE, INTERSECTS, [new LineItemSize(w: PBSUtils.randomNumber, h: PBSUtils.randomNumber)])
                                               .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [VIDEO])
                                               .build(),
                      new Targeting.Builder(NOT).buildNotBooleanOperatorTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])]
    }
}
