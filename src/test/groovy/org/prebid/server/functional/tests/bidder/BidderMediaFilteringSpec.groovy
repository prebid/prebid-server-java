package org.prebid.server.functional.tests.bidder

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENER_X
import static org.prebid.server.functional.model.request.auction.Asset.getTitleAsset
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class BidderMediaFilteringSpec extends BaseSpec {

    private static final Map<String, String> BIDDER_CONFIG = [
            "adapters.${GENERIC.value}.enabled"                                            : "true",
            "adapters.${GENERIC.value}.meta-info.vendor-id"                                : "0",
            "adapters.${GENERIC.value}.meta-info.site-media-types"                         : "banner,video,native",
            "adapters.${GENERIC.value}.meta-info.app-media-types"                          : "banner,video,native",
            "adapters.${GENERIC.value}.meta-info.dooh-media-types"                         : "banner,video,native,audio",

            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.enabled"                     : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.meta-info.vendor-id"         : "0",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.meta-info.site-media-types"  : "native,video",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.meta-info.app-media-types"   : "video,native",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.meta-info.dooh-media-types"  : "banner,video,audio",

            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.enabled"                   : "true",
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.meta-info.vendor-id"       : '0',
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.meta-info.site-media-types": "",
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.meta-info.app-media-types" : "",
            "adapters.${GENERIC.value}.aliases.${GENER_X.value}.meta-info.dooh-media-types": "",
    ]

    private static final filterMediaService = pbsServiceFactory.getService(
            BIDDER_CONFIG + ['auction.filter-imp-media-type.enabled': 'true']
    )

    private static final freeMediaService = pbsServiceFactory.getService(
            BIDDER_CONFIG + ['auction.filter-imp-media-type.enabled': 'false']
    )

    def "PBS should reject #channel request when bidder supports no media types"() {
        given: "Bid request for bidder without supported media types"
        def bidRequest = getBidderRequest(channel, GENER_X)

        when: "PBS processes auction request"
        def response = filterMediaService.sendAuctionRequest(bidRequest)

        then: "Response should contain no bids"
        assert response.seatbid.isEmpty()

        and: "Response should warn about unsupported media types"
        assert response.ext?.warnings[ErrorType.GENER_X]*.code == [2]
        assert response.ext?.warnings[ErrorType.GENER_X]*.message == ["Bidder does not support any media types."]

        where:
        channel << [SITE, APP, DOOH]
    }

    def "PBS should accept #channel request when bidder supports no media types and filtering is disabled"() {
        given: "Bid request for bidder without supported media types"
        def bidRequest = getBidderRequest(channel, GENER_X)

        when: "PBS processes auction request"
        def response = freeMediaService.sendAuctionRequest(bidRequest)

        then: "Response should contain bids"
        assert response.seatbid

        and: "Response should contain no errors"
        assert !response.ext?.errors

        where:
        channel << [SITE, APP, DOOH]
    }

    def "PBS should remove unsupported banner and retain native media type in SITE impression"() {
        given: "SITE request with banner and native media types"
        def bidRequest = getBidderRequest(SITE, ALIAS).tap {
            site = Site.defaultSite
            imp[0].banner = Banner.defaultBanner
            imp[0].nativeObj = Native.defaultNative
        }

        and: "Bidder response for the impression"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = filterMediaService.sendAuctionRequest(bidRequest)

        then: "Bidder should receive native media type without banner"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.imp[0]?.banner
        assert bidderRequest.imp[0]?.nativeObj

        and: "Response should contain no warnings"
        assert !response.ext?.warnings
    }

    def "PBS should retain unsupported banner in SITE impression when filtering is disabled"() {
        given: "SITE request with banner and native media types"
        def bidRequest = getBidderRequest(SITE, ALIAS).tap {
            site = Site.defaultSite
            imp[0].banner = Banner.defaultBanner
            imp[0].nativeObj = Native.defaultNative
        }

        when: "PBS processes auction request"
        def response = freeMediaService.sendAuctionRequest(bidRequest)

        then: "Bidder should receive both media types"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.banner
        assert bidderRequest.imp[0]?.nativeObj

        and: "Response should contain no errors"
        assert !response.ext?.errors
    }

    def "PBS should remove unsupported banner impression and retain native impression in SITE request"() {
        given: "SITE request with banner and native impressions"
        def nativeImp = Imp.getDefaultImpression(NATIVE).tap {
            ext.prebid.bidder.generic = null
            ext.prebid.bidder.alias = new Generic()
        }
        def bidRequest = getBidderRequest(SITE, ALIAS).tap {
            site = Site.defaultSite
            imp << nativeImp
        }

        and: "Bidder response for supported native impression"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid = [Bid.getDefaultBid(nativeImp)]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = filterMediaService.sendAuctionRequest(bidRequest)

        then: "Bidder should receive only the native impression"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.id == [nativeImp.id]
        assert bidderRequest.imp.first().nativeObj

        and: "Response should warn about the removed banner impression"
        assert response.ext?.warnings[ErrorType.ALIAS]*.code == [2]
        assert response.ext?.warnings[ErrorType.ALIAS]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from the " +
                         "request for this bidder." as String]

        and: "Response should contain bids"
        assert response.seatbid
    }

    def "PBS should reject SITE request when all impressions have unsupported media types"() {
        given: "SITE request with unsupported banner impression"
        def bidRequest = getBidderRequest(SITE, ALIAS).tap {
            site = Site.defaultSite
            imp.first().banner = Banner.defaultBanner
        }

        when: "PBS processes auction request"
        def response = filterMediaService.sendAuctionRequest(bidRequest)

        then: "Bidder should not receive a request"
        assert bidder.getRequestCount(bidRequest.id) == 0

        and: "Response should warn about the removed impression and empty request"
        assert response.ext?.warnings[ErrorType.ALIAS]*.code == [2, 2]
        assert response.ext?.warnings[ErrorType.ALIAS]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from the request for this bidder." as String,
                 "Bid request contains 0 impressions after filtering."]

        and: "Response should contain no bids"
        assert response.seatbid.isEmpty()
    }

    def "PBS should reject DOOH request when all impressions have unsupported media types"() {
        given: "DOOH request with unsupported native impression"
        def bidRequest = getBidderRequest(DOOH, ALIAS).tap {
            imp.first().banner = null
            imp.first().nativeObj = Native.defaultNative
        }

        when: "PBS processes auction request"
        def response = filterMediaService.sendAuctionRequest(bidRequest)

        then: "Bidder should not receive a request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Response should warn about the removed impression and empty request"
        assert response.ext?.warnings[ErrorType.ALIAS]*.code == [2, 2]
        assert response.ext?.warnings[ErrorType.ALIAS]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from the request for this bidder." as String,
                 "Bid request contains 0 impressions after filtering."]

        and: "Response should contain no bids"
        assert !response.seatbid
    }

    def "PBS should retain #mediaType impression and remove unsupported native impression in DOOH request"() {
        given: "DOOH request with supported and unsupported impressions"
        def bidRequest = getBidderRequest(DOOH, ALIAS).tap {
            imp = [Imp.getDefaultImpression(mediaType), Imp.getDefaultImpression(NATIVE)]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[1].ext.prebid.bidder.generic = null
            imp[1].ext.prebid.bidder.alias = new Generic()
        }

        and: "Bidder response for supported impression"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [titleAsset])
            seatbid[0].bid[0].nurl = PBSUtils.randomString
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = filterMediaService.sendAuctionRequest(bidRequest)

        then: "Bidder should receive only the supported impression"
        assert bidder.getBidderRequest(bidRequest.id).imp.id == [bidRequest.imp.first().id]

        and: "Response should warn about the removed native impression"
        assert response.ext?.warnings[ErrorType.ALIAS]*.code == [2]
        assert response.ext?.warnings[ErrorType.ALIAS]*.message ==
                ["Imp ${bidRequest.imp[1].id} does not have a supported media type and has been removed from the request for this bidder." as String]

        and: "Response should contain bids"
        assert response.seatbid

        where:
        mediaType << [BANNER, VIDEO, AUDIO]
    }

    private static BidRequest getBidderRequest(DistributionChannel channel, BidderName bidder) {
        return BidRequest.getDefaultBidRequest(channel).tap {
            if (bidder != GENERIC) {
                imp.first().ext.prebid.bidder.with {
                    generic = null

                    if (bidder == GENER_X) {
                        generX = new Generic()
                    } else if (bidder == ALIAS) {
                        alias = new Generic()
                    }
                }
            }
        }
    }
}
