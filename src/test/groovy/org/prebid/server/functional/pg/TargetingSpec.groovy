package org.prebid.server.functional.pg

import org.prebid.server.functional.model.deals.lineitem.LineItemSize
import org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator
import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.UserTime
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared
import spock.lang.Unroll

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static java.time.temporal.WeekFields.SUNDAY_START
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
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.DOW
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.HOUR
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.INVALID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.PAGE_POSITION
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.REFERRER
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SITE_DOMAIN
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

        SITE_DOMAIN   | BidRequest.defaultBidRequest.tap {
            site.domain = stringTargetingValue
        }

        SITE_DOMAIN   | BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap { domain = stringTargetingValue }
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
