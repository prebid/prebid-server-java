package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.PreferredMediaType
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Bidder
import org.prebid.server.functional.model.request.auction.Native

import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER

class FilterMultiFormatSpec extends BaseSpec {

    def "PBS should response with all requested media type when default adapters multi format true in config and at account level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: BANNER))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert bidderRequest.imp[0].audio
    }

    def "PBS should response with all requested media type when default adapters multi format true in config and at request level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert bidderRequest.imp[0].audio
    }

    def "PBS should response with one requested preferred media type when default adapters multi format false in config and at account level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: BANNER))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should response with one requested preferred media type when default adapters multi format false in config and at request level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should response with all requested media type when multi format true in config and at request level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.banner
        assert bidderRequest.imp.audio
    }

    def "PBS should response with all requested media type when multi format true in config and at account level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: BANNER))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.banner
        assert bidderRequest.imp.audio
    }

    def "PBS should response with one requested preferred media type when multi format false in config and at account level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: BANNER))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should response with one requested preferred media type when multi format false in config and at request level specified preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should response with warning and don't make a bidder call when requested multi format doesn't specified at account preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
            imp[0].nativeObj = Native.defaultNative
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: BANNER))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't make bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain warning"
        assert bidResponse.ext.warnings[GENERIC]?.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a media type after filtering and has been removed from the request for this bidder.",
                 "Bid request contains 0 impressions after filtering."]
    }

    def "PBS should response with warning and don't make a bidder call when requested multi format doesn't specified at request preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type and preferredMediaType BANNER"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
            imp[0].nativeObj = Native.defaultNative
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't make bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain warning"
        assert bidResponse.ext.warnings[GENERIC]?.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a media type after filtering and has been removed from the request for this bidder.",
                 "Bid request contains 0 impressions after filtering."]
    }

    def "PBS shouldn't response with warning and make a bidder call when doesn't requested multi format that specified at account preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: BANNER))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.imp.audio

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext.warnings
    }

    def "PBS shouldn't response with warning and make a bidder call when doesn't requested multi format that specified at request preferred media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type and preferredMediaType BANNER"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.imp.audio

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext.warnings
    }

    def "PBS shouldn't response with warning and make bidder call when multi format false and in request level preferred media type null"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.getDefaultBanner()
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: null))
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext?.warnings
    }

    def "PBS shouldn't response with warning and make bidder call when multi format false and in account level preferred media type null"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.getDefaultBanner()
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB without preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: null))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext?.warnings
    }

    def "PBS should response with specified in request preferred media type when preferred media type specified in both places "() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "false",
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidders = new Bidder(generic: new Generic(preferredMediaType: BANNER))
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: new PreferredMediaType(generic: AUDIO))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }
}
