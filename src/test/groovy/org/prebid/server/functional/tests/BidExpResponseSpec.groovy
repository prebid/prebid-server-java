package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.PrebidCache
import org.prebid.server.functional.model.request.auction.PrebidCacheSettings
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.IgnoreRest

import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO

class BidExpResponseSpec extends BaseSpec {

    private static final def BANNER_TTL_HOST_CACHE = PBSUtils.randomNumber
    private static final def VIDEO_TTL_HOST_CACHE = PBSUtils.randomNumber
    private static final def BANNER_TTL_DEFAULT_CACHE = PBSUtils.randomNumber
    private static final def VIDEO_TTL_DEFAULT_CACHE = PBSUtils.randomNumber
    private static final def AUDIO_TTL_DEFAULT_CACHE = PBSUtils.randomNumber
    private static final def NATIVE_TTL_DEFAULT_CACHE = PBSUtils.randomNumber
    private static final Map<String, String> CACHE_TTL_HOST_CONFIG = ["cache.banner-ttl-seconds": BANNER_TTL_HOST_CACHE as String,
                                                                      "cache.video-ttl-seconds" : VIDEO_TTL_HOST_CACHE as String]
    private static final Map<String, String> DEFAULT_CACHE_TTL_CONFIG = ["cache.default-ttl-seconds.banner": BANNER_TTL_DEFAULT_CACHE as String,
                                                                         "cache.default-ttl-seconds.video" : VIDEO_TTL_DEFAULT_CACHE as String,
                                                                         "cache.default-ttl-seconds.native": NATIVE_TTL_DEFAULT_CACHE as String,
                                                                         "cache.default-ttl-seconds.audio" : AUDIO_TTL_DEFAULT_CACHE as String]
    private static final Map<String, String> EMPTY_CACHE_TTL_CONFIG = ["cache.default-ttl-seconds.banner": "",
                                                                       "cache.default-ttl-seconds.video" : "",
                                                                       "cache.default-ttl-seconds.native": "",
                                                                       "cache.default-ttl-seconds.audio" : ""]
    private static final Map<String, String> EMPTY_CACHE_TTL_HOST_CONFIG = ["cache.banner-ttl-seconds": "",
                                                                            "cache.video-ttl-seconds" : ""]
    private static def pbsOnlyHostCacheTtlService = pbsServiceFactory.getService(CACHE_TTL_HOST_CONFIG + EMPTY_CACHE_TTL_CONFIG)
    private static def pbsEmptyTtlService = pbsServiceFactory.getService(EMPTY_CACHE_TTL_CONFIG + EMPTY_CACHE_TTL_HOST_CONFIG)
    private static def pbsHostAndDefaultCacheTtlService = pbsServiceFactory.getService(CACHE_TTL_HOST_CONFIG + DEFAULT_CACHE_TTL_CONFIG)


    def "PBS auction should resolve bid.exp from response that is set by the bidder’s adapter"() {
        given: "Default basicResponse with exp"
        def bidResponseExp = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = bidResponseExp
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidResponseExp]

        and: "PBS should not call PBC"
        assert !prebidCache.getRequestCount(bidRequest.imp.first.id)

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve bid.exp from response and send it to cache when it set by the bidder’s adapter and cache enabled for request"() {
        given: "BidRequest with enabled cache"
        bidRequest.enableCache()

        and: "Default basic bid with exp"
        def bidResponseExp = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = bidResponseExp
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidResponseExp]

        and: "PBS should call PBC"
        def cacheRequests = prebidCache.getRecordedRequests(bidRequest.imp.first.id)
        assert cacheRequests.puts.first.first.ttlseconds == bidResponseExp

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve exp from request.imp[].exp when it have value"() {
        given: "Default basic bidRequest with exp"
        def bidRequestExp = PBSUtils.randomNumber
        bidRequest.tap {
            imp.first.exp = bidRequestExp
        }

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = null
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve exp from request.ext.prebid.cache.bids for banner request when it have value"() {
        given: "Default basic bid with ext.prebid.cache.bids"
        def bidRequestExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableCache()
            ext.prebid.cache.bids = new PrebidCacheSettings(ttlSeconds: bidRequestExp)
        }

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = null
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]
    }

    def "PBS auction should resolve exp from request.ext.prebid.cache.vastxml for video request when it have value"() {
        given: "Default basic bid with ext.prebid.cache.vastXml"
        def bidRequestExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultVideoRequest.tap {
            enableCache()
            ext.prebid.cache.vastXml = new PrebidCacheSettings(ttlSeconds: bidRequestExp)
        }

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = null
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]
    }

    def "PBS auction should resolve exp from account config for banner request when it have value"() {
        given: "default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = null
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [accountCacheTtl]
    }

    def "PBS auction should resolve exp from account videoCacheTtl config for video request when it have value"() {
        given: "default bidRequest"
        def bidRequest = BidRequest.defaultVideoRequest

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(videoCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = null
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [accountCacheTtl]
    }

    def "PBS auction should resolve exp from global banner config for banner request"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsHostAndDefaultCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [BANNER_TTL_HOST_CACHE]
    }

    def "PBS auction should prioritize value from bid.exp rather than request.imp[].exp"() {
        given: "Default basic bidRequest with exp"
        bidRequest.tap {
            imp.first.exp = PBSUtils.randomNumber
        }

        and: "Set bidder response with exp"
        def bidResponseExp = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = bidResponseExp
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidResponseExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should prioritize value from request.imp[].exp rather than request.ext.prebid.cache"() {
        given: "Default basic bidRequest with exp"
        def bidRequestExp = PBSUtils.randomNumber
        def bidRequestBidsCacheExp = PBSUtils.randomNumber
        bidRequest.tap {
            enableCache()
            imp.first.exp = bidRequestExp
            ext.prebid.cache.bids = new PrebidCacheSettings(ttlSeconds: bidRequestBidsCacheExp)
        }

        and: "Set bidder response with exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should prioritize value from request.ext.prebid.cache rather than account config"() {
        given: "Default basic bidRequest with exp"
        def bidRequestBidsCacheExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableCache()
            ext.prebid.cache.bids = new PrebidCacheSettings(ttlSeconds: bidRequestBidsCacheExp)
        }

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response with exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestBidsCacheExp]
    }

    def "PBS auction should prioritize value from account config rather than host config"() {
        given: "Default basic bidRequest with exp"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response with exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsHostAndDefaultCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [accountCacheTtl]
    }

    def "PBS auction should prioritize bid.exp from the response over all other fields from the request and account config"() {
        given: "Default bid request with different media type in imp"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0] = Imp.getDefaultImpression(mediaType).tap {
                exp = PBSUtils.randomNumber
            }
            ext.prebid.cache = new PrebidCache(
                    vastXml: new PrebidCacheSettings(ttlSeconds: PBSUtils.randomNumber),
                    bids: new PrebidCacheSettings(ttlSeconds: PBSUtils.randomNumber))
        }

        and: "Default bid response with bid.exp"
        def randomExp = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = randomExp
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def auctionConfig = new AccountAuctionConfig(
                videoCacheTtl: PBSUtils.randomNumber,
                bannerCacheTtl: PBSUtils.randomNumber)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsHostAndDefaultCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == randomExp

        where:
        mediaType << [BANNER, VIDEO, NATIVE, AUDIO]
    }

    def "PBS auction shouldn't resolve bid.exp for #mediaType when the response, request, and account config don't include such data"() {
        given: "Default bid request with different media type in imp"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Default bid response with bid.exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = null
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsEmptyTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain exp data"
        assert !response.seatbid.first.bid.first.exp

        where:
        mediaType << [BANNER, VIDEO, NATIVE, AUDIO]
    }

    def "PBS auction should prioritize imp.exp and resolve bid.exp for #mediaType when request and account config include multiple exp sources"() {
        given: "Default bid request"
        def randomExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0] = Imp.getDefaultImpression(mediaType).tap {
                exp = randomExp
            }
            ext.prebid.cache = new PrebidCache(
                    vastXml: new PrebidCacheSettings(ttlSeconds: PBSUtils.randomNumber),
                    bids: new PrebidCacheSettings(ttlSeconds: PBSUtils.randomNumber))
        }

        and: "Default bid response without bid.exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def auctionConfig = new AccountAuctionConfig(
                videoCacheTtl: PBSUtils.randomNumber,
                bannerCacheTtl: PBSUtils.randomNumber)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsHostAndDefaultCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == randomExp

        where:
        mediaType << [BANNER, VIDEO, NATIVE, AUDIO]
    }

    def "PBS auction shouldn't resolve bid.exp from ext.prebid.cache.vastxml.ttlseconds when request has #mediaType as mediaType"() {
        given: "Default bid request"
        def randomExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
            imp[0] = Imp.getDefaultImpression(mediaType)
            ext.prebid.cache = new PrebidCache(vastXml: new PrebidCacheSettings(ttlSeconds: randomExp))
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def auctionConfig = new AccountAuctionConfig(
                videoCacheTtl: PBSUtils.randomNumber)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsEmptyTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain exp data"
        assert !response?.seatbid?.first?.bid?.first?.exp

        where:
        mediaType << [BANNER, NATIVE, AUDIO]
    }

    def "PBS auction should resolve bid.exp from ext.prebid.cache.vastxml.ttlseconds when request has video as mediaType"() {
        given: "Default bid request"
        def randomExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
            imp[0] = Imp.getDefaultImpression(VIDEO)
            ext.prebid.cache = new PrebidCache(
                    vastXml: new PrebidCacheSettings(ttlSeconds: randomExp),
                    bids: new PrebidCacheSettings(ttlSeconds: PBSUtils.randomNumber))
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def auctionConfig = new AccountAuctionConfig(
                videoCacheTtl: PBSUtils.randomNumber,
                bannerCacheTtl: PBSUtils.randomNumber)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsHostAndDefaultCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == randomExp
    }

    def "PBS auction should resolve bid.exp when ext.prebid.cache.bids.ttlseconds is specified and no higher-priority fields are present"() {
        given: "Default bid request"
        def randomExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
            imp[0] = Imp.getDefaultImpression(mediaType)
            ext.prebid.cache = new PrebidCache(bids: new PrebidCacheSettings(ttlSeconds: randomExp))
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def auctionConfig = new AccountAuctionConfig(
                videoCacheTtl: PBSUtils.randomNumber,
                bannerCacheTtl: PBSUtils.randomNumber)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsHostAndDefaultCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == randomExp

        where:
        mediaType << [BANNER, VIDEO, NATIVE, AUDIO]
    }

    def "PBS auction shouldn't resolve bid.exp when the account config and request imp type do not match"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsEmptyTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain exp data"
        assert !response.seatbid.first.bid.first.exp

        where:
        mediaType | auctionConfig
        VIDEO     | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber)
        VIDEO     | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber, videoCacheTtl: null)
        BANNER    | new AccountAuctionConfig(videoCacheTtl: PBSUtils.randomNumber)
        BANNER    | new AccountAuctionConfig(bannerCacheTtl: null, videoCacheTtl: PBSUtils.randomNumber)
        NATIVE    | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber, videoCacheTtl: PBSUtils.randomNumber)
        NATIVE    | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber)
        NATIVE    | new AccountAuctionConfig(videoCacheTtl: PBSUtils.randomNumber)
        AUDIO     | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber, videoCacheTtl: PBSUtils.randomNumber)
        AUDIO     | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber)
        AUDIO     | new AccountAuctionConfig(videoCacheTtl: PBSUtils.randomNumber)
    }

    def "PBS auction shouldn't resolve bid.exp when account config and request imp type match but account config for cache-ttl is not specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
            imp[0] = Imp.getDefaultImpression(mediaType)
        }
        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: new AccountAuctionConfig(bannerCacheTtl: null, videoCacheTtl: null)))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsEmptyTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain exp data"
        assert !response.seatbid.first.bid.first.exp

        where:
        mediaType << [VIDEO, BANNER, NATIVE, AUDIO]
    }

    def "PBS auction should resolve bid.exp when account.auction.{banner/video}-cache-ttl and banner bid specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            enableCache()
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountAuctionConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsEmptyTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == accountCacheTtl

        where:
        mediaType | accountCacheTtl       | accountAuctionConfig
        BANNER    | PBSUtils.randomNumber | new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        VIDEO     | PBSUtils.randomNumber | new AccountAuctionConfig(videoCacheTtl: accountCacheTtl)
    }

    def "PBS auction should resolve bid.exp when cache.{banner/video}-ttl-seconds config specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
            enableCache()
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsOnlyHostCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == expValue

        where:
        mediaType | expValue
        BANNER    | BANNER_TTL_HOST_CACHE
        VIDEO     | VIDEO_TTL_HOST_CACHE
    }

    def "PBS auction shouldn't resolve bid.exp when cache ttl-seconds is specified for #mediaType mediaType request"() {
        given: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
            ext.prebid.cache = new PrebidCache(bids: new PrebidCacheSettings(ttlSeconds: PBSUtils.randomNumber))
        }

        when: "PBS processes auction request"
        def response = pbsOnlyHostCacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain exp data"
        assert !response.seatbid.first.bid.first.exp

        where:
        mediaType << [NATIVE, AUDIO]
    }

    def "PBS auction should resolve bid.exp when cache.default-ttl-seconds.{banner,video,audio,native} is specified and no higher-priority fields are present"() {
        given: "Prebid server with empty host config and default cache ttl config"
        def config = EMPTY_CACHE_TTL_HOST_CONFIG + DEFAULT_CACHE_TTL_CONFIG
        def prebidServerService = pbsServiceFactory.getService(config)

        and: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.first.bid.first.exp == bidExpValue

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(config)

        where:
        mediaType | bidExpValue
        BANNER    | BANNER_TTL_DEFAULT_CACHE
        VIDEO     | VIDEO_TTL_DEFAULT_CACHE
        AUDIO     | AUDIO_TTL_DEFAULT_CACHE
        NATIVE    | NATIVE_TTL_DEFAULT_CACHE
    }
}
