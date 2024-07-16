package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidderControls
import org.prebid.server.functional.model.request.auction.GenericPreferredBidder
import org.prebid.server.functional.model.request.auction.Native

import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NULL

class FilterMultiFormatSpec extends BaseSpec {

    def "PBS should respond with all requested media types when default adapters multi format is true in config and preferred media type specified at account level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): BANNER])
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert bidderRequest.imp[0].audio
    }

    def "PBS should respond with all requested media types when default adapters multi format is true in config and preferred media type specified at request level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert bidderRequest.imp[0].audio
    }

    def "PBS should respond with one requested preferred media type when default adapters multi format is false in config and  preferred media type specified at account level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): BANNER])
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should respond with one requested preferred media type when default adapters multi format is false in config and preferred media type specified at request level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should respond with all requested media type when multi format is true in config and preferred media type specified at request level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.banner
        assert bidderRequest.imp.audio
    }

    def "PBS should respond with all requested media type when multi format is true in config and preferred media type specified at account level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "true")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): BANNER])
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain all requested media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.banner
        assert bidderRequest.imp.audio
    }

    def "PBS should respond with one requested preferred media type when multi format is false in config and preferred media type specified at account level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): BANNER])
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should respond with one requested preferred media type when multi format is false in config and preferred media type preferred at request level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain only requested preferred media type"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].banner
        assert !bidderRequest.imp[0].audio
    }

    def "PBS should respond with warning and don't make a bidder call when multi format at request and preferred media type specified at account level with non requested media type"() {
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
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): BANNER])
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

    def "PBS should respond with warning and don't make a bidder call when multi format at request and preferred media type specified at request level with non requested media type"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type and preferredMediaType BANNER"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
            imp[0].nativeObj = Native.defaultNative
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
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

    def "PBS shouldn't respond with warning and make a bidder call when request doesn't contain multi format and preferred media type specified at account level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): BANNER])
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

    def "PBS shouldn't respond with warning and make a bidder call when request doesn't contain multi format and preferred media type specified at request level"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with audio and nativeObj type and preferredMediaType BANNER"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.imp.audio

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext.warnings
    }

    def "PBS shouldn't respond with warning and make a bidder call when request doesn't contain multi format and multi format is false and preferred media type specified at request level with null"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.getDefaultBanner()
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: NULL))
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext?.warnings
    }

    def "PBS shouldn't respond with warning and make a bidder call when request doesn't contain multi format and multi format is false and preferred media type specified at account level with null"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.getDefaultBanner()
            imp[0].audio = Audio.defaultAudio
        }

        and: "Account in the DB without preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): NULL])
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should make bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext?.warnings
    }

    def "PBS should respond with preferred media type that specified in request when preferred media type specified in both places"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(
                "adapter-defaults.ortb.multiformat-supported": "false",
                "adapters.generic.ortb.multiformat-supported": "false")

        and: "Default bid request with banner and audio type"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = Banner.defaultBanner
            imp[0].audio = Audio.defaultAudio
            ext.prebid.bidderControls = new BidderControls(generic: new GenericPreferredBidder(preferredMediaType: BANNER))
        }

        and: "Account in the DB with preferred media type"
        def accountConfig = new AccountAuctionConfig(preferredMediaType: [(BidderName.GENERIC): AUDIO])
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
