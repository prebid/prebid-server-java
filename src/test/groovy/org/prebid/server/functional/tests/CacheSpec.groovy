package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountEventsConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class CacheSpec extends BaseSpec {

    private final static String PBS_API_HEADER = 'x-pbc-api-key'
    private static final Integer MAX_DATACENTER_REGION_LENGTH = 4

    private final static String XML_CREATIVE_SIZE_METRIC = "account.%s.prebid_cache.creative_size.xml"
    private final static String JSON_CREATIVE_SIZE_METRIC = "account.%s.prebid_cache.creative_size.json"
    private final static String XML_CREATIVE_TTL_METRIC = "account.%s.prebid_cache.creative_ttl.xml"
    private final static String JSON_CREATIVE_TTL_METRIC = "account.%s.prebid_cache.creative_ttl.json"
    private final static String CACHE_REQUEST_OK_METRIC = "account.%s.prebid_cache.requests.ok"
    private final static String XML_CREATIVE_SIZE_GENERAL_METRIC = "prebid_cache.creative_size.xml"
    private final static String JSON_CREATIVE_SIZE_GENERAL_METRIC = "prebid_cache.creative_size.json"
    private final static String OK_GENERAL_METRIC = "prebid_cache.requests.ok"

    def "PBS should update prebid_cache.creative_size.xml metric when xml creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, OK_GENERAL_METRIC)

        and: "Default VtrackRequest"
        def accountId = PBSUtils.randomNumber.toString()
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        when: "PBS processes vtrack request"
        defaultPbsService.sendVtrackRequest(request, accountId)

        then: "prebid_cache.creative_size.xml metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        def creativeSize = creative.bytes.length
        assert metrics[OK_GENERAL_METRIC] == initialValue + 1
        assert metrics[XML_CREATIVE_SIZE_GENERAL_METRIC] == creativeSize

        and: "account.<account-id>.prebid_cache.creative_size.xml should be updated"
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(accountId)] == 1
        assert metrics[XML_CREATIVE_SIZE_METRIC.formatted(accountId)] == creativeSize
    }

    def "PBS should update prebid_cache.creative_size.json metric when json creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, OK_GENERAL_METRIC)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()

        and: "Default basic bid with banner creative"
        def asset = new Asset(id: PBSUtils.randomNumber)
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [asset])
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "prebid_cache.creative_size.json should be update"
        def adm = bidResponse.seatbid[0].bid[0].getAdm()
        def creativeSize = adm.bytes.length

        and: "prebid_cache.creative_size.json metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[OK_GENERAL_METRIC] == initialValue + 1
        assert metrics[JSON_CREATIVE_SIZE_GENERAL_METRIC] == creativeSize

        and: "account.<account-id>.prebid_cache.creative_size.json should be update"
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1
        assert metrics[JSON_CREATIVE_SIZE_METRIC.formatted(bidRequest.accountId)] == creativeSize
    }

    def "PBS should cache bids when targeting is specified"() {
        given: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS call shouldn't include api-key"
        assert !prebidCache.getRequestHeaders(bidRequest.imp[0].id)[PBS_API_HEADER]
    }

    def "PBS should cache bids without api-key header when targeting is specified and api-key-secured disabled"() {
        given: "Pbs config with disabled api-key-secured and pbc.api.key"
        def apiKey = PBSUtils.randomString
        def pbsService = pbsServiceFactory.getService(['pbc.api.key': apiKey, 'cache.api-key-secured': 'false'])

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS call shouldn't include api-key"
        assert !prebidCache.getRequestHeaders(bidRequest.imp[0].id)[PBS_API_HEADER]
    }

    def "PBS should cache bids with api-key header when targeting is specified and api-key-secured enabled"() {
        given: "Pbs config with api-key-secured and pbc.api.key"
        def apiKey = PBSUtils.randomString
        def pbsService = pbsServiceFactory.getService(['pbc.api.key': apiKey, 'cache.api-key-secured': 'true'])

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS call should include api-key"
        assert prebidCache.getRequestHeaders(bidRequest.imp[0].id)[PBS_API_HEADER] == [apiKey]
    }

    def "PBS should cache banner bids with cache key that include account and datacenter short name when append-trace-info-to-cache-id enabled"() {
        given: "Pbs config with append-trace-info-to-cache-id"
        def serverDataCenter = PBSUtils.randomString
        def bannerHostTtl = PBSUtils.getRandomNumber(300, 1500)
        def pbsConfig = ['cache.default-ttl-seconds.banner'   : bannerHostTtl.toString(),
                         'datacenter-region'                  : serverDataCenter,
                         'cache.append-trace-info-to-cache-id': 'true'
        ]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS cache key should start with account and datacenter short name"
        def cacheKey = prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key
        assert cacheKey.startsWith("${bidRequest.accountId}-${serverDataCenter.take(MAX_DATACENTER_REGION_LENGTH)}")

        and: "PBS cache key should have length equal to default UUID"
        assert cacheKey.length() == UUID.randomUUID().toString().length()

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should cache video bids with cache key that include account and datacenter short name when append-trace-info-to-cache-id enabled"() {
        given: "Pbs config with append-trace-info-to-cache-id"
        def serverDataCenter = PBSUtils.randomString
        def videoHostTtl = PBSUtils.getRandomNumber(300, 1500)
        def pbsConfig = ['cache.default-ttl-seconds.video'    : videoHostTtl.toString(),
                         'datacenter-region'                  : serverDataCenter,
                         'cache.append-trace-info-to-cache-id': 'true'
        ]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultVideoRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS cache key should start with account and datacenter short name"
        def cacheKey = prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key
        assert cacheKey.startsWith("${bidRequest.accountId}-${serverDataCenter.take(MAX_DATACENTER_REGION_LENGTH)}")

        and: "PBS cache key should have length equal to default UUID"
        assert cacheKey.length() == UUID.randomUUID().toString().length()

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == videoHostTtl
        assert metrics[XML_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == videoHostTtl
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should cache bids with cache key that include account when append-trace-info-to-cache-id enabled and datacenter is null"() {
        given: "Pbs config with append-trace-info-to-cache-id"
        def bannerHostTtl = PBSUtils.getRandomNumber(300, 1500)
        def pbsConfig = ['cache.default-ttl-seconds.banner'   : bannerHostTtl.toString(),
                         'datacenter-region'                  : null,
                         'cache.append-trace-info-to-cache-id': 'true'
        ]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS cache key should start with account and datacenter short name"
        def cacheKey = prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key
        assert cacheKey.startsWith("${bidRequest.accountId}-")

        and: "PBS cache key should have length equal to default UUID"
        assert cacheKey.length() == UUID.randomUUID().toString().length()

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should cache bids without cache key when account ID is too large"() {
        given: "Pbs config with append-trace-info-to-cache-id"
        def serverDataCenter = PBSUtils.randomString
        def bannerHostTtl = PBSUtils.getRandomNumber(300, 1500)
        def pbsConfig = ['cache.default-ttl-seconds.banner'   : bannerHostTtl.toString(),
                         'datacenter-region'                  : serverDataCenter,
                         'cache.append-trace-info-to-cache-id': 'true'
        ]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default BidRequest with cache, targeting and large account ID"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()
        bidRequest.setAccountId(UUID.randomUUID().toString())

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS shouldn't contain cache key"
        assert !prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should cache bids without cache key when append-trace-info-to-cache-id disabled"() {
        given: "Pbs config with append-trace-info-to-cache-id"
        def bannerHostTtl = PBSUtils.getRandomNumber(300, 1500)
        def serverDataCenter = PBSUtils.randomString
        def pbsConfig = ['cache.default-ttl-seconds.banner'   : bannerHostTtl.toString(),
                         'datacenter-region'                  : serverDataCenter,
                         'cache.append-trace-info-to-cache-id': 'false'
        ]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS shouldn't contain cache key"
        assert !prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should not cache bids when targeting isn't specified"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = null

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 0
    }

    def "PBS shouldn't response with seatbid.bid.adm in response when ext.prebid.cache.bids.returnCreative=false"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableCache()
            ext.prebid.cache.bids.returnCreative = false
        }

        and: "Default basic bid with banner creative"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [Asset.defaultAsset])
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain adm obj"
        assert !response.seatbid[0].bid[0].adm
    }

    def "PBS should response with seatbid.bid.adm in response when ext.prebid.cache.bids.returnCreative=true"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableCache()
            ext.prebid.cache.bids.returnCreative = true
        }

        and: "Default basic bid with banner creative"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [Asset.defaultAsset])
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain adm obj"
        assert response.seatbid[0].bid[0].adm == bidResponse.seatbid[0].bid[0].adm
    }

    def "PBS shouldn't response with seatbid.bid.adm in response when ext.prebid.cache.vastXml.returnCreative=false and video request"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
            enableCache()
            ext.prebid.cache.vastXml.returnCreative = false
        }

        and: "Default basic bid with banner creative"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [Asset.defaultAsset])
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain adm obj"
        assert !response.seatbid[0].bid[0].adm
    }

    def "PBS should response with seatbid.bid.adm in response when ext.prebid.cache.vastXml.returnCreative=#returnCreative and imp.#mediaType"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableCache()
            imp[0] = Imp.getDefaultImpression(mediaType)
            ext.prebid.cache.vastXml.returnCreative = returnCreative
        }

        and: "Default basic bid with banner creative"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [Asset.defaultAsset])
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain adm obj"
        assert response.seatbid[0].bid[0].adm == bidResponse.seatbid[0].bid[0].adm

        where:
        returnCreative | mediaType
        false          | BANNER
        true           | VIDEO
    }

    def "PBS should update prebid_cache.creative_size.xml metric and adding tracking xml when xml creative contain #wrapper and impression are valid xml value"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, OK_GENERAL_METRIC)

        and: "Create and save enabled events config in account"
        def accountId = PBSUtils.randomNumber.toString()
        def account = new Account().tap {
            uuid = accountId
            config = new AccountConfig().tap {
                auction = new AccountAuctionConfig(events: new AccountEventsConfig(enabled: true))
            }
        }
        accountDao.save(account)

        and: "Vtrack request with custom tags"
        def payload = PBSUtils.randomString
        def creative = "<VAST version=\"3.0\"><Ad><${wrapper}><AdSystem>prebid.org wrapper</AdSystem>" +
                "<VASTAdTagURI>&lt;![CDATA[//${payload}]]&gt;</VASTAdTagURI>" +
                "<${impression}> &lt;![CDATA[ ]]&gt; </${impression}><Creatives></Creatives></${wrapper}></Ad></VAST>"
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        when: "PBS processes vtrack request"
        defaultPbsService.sendVtrackRequest(request, accountId)

        then: "Vast xml is modified"
        def prebidCacheRequest = prebidCache.getXmlRecordedRequestsBody(payload)
        assert prebidCacheRequest.size() == 1
        assert prebidCacheRequest[0].contains("/event?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")

        and: "prebid_cache.creative_size.xml metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[OK_GENERAL_METRIC] == initialValue + 1

        and: "account.<account-id>.prebid_cache.creative_size.xml should be updated"
        assert metrics[CACHE_REQUEST_OK_METRIC.formatted(accountId) as String] == 1

        where:
        wrapper                                     | impression
        " wrapper "                                 | " impression "
        PBSUtils.getRandomCase(" wrapper ")         | PBSUtils.getRandomCase(" impression ")
        "  wraPPer ${PBSUtils.getRandomString()}  " | "  imPreSSion ${PBSUtils.getRandomString()}"
        "    inLine    "                            | " ImpreSSion $PBSUtils.randomNumber"
        PBSUtils.getRandomCase(" inline ")          | " ${PBSUtils.getRandomCase(" impression ")} $PBSUtils.randomNumber "
        "  inline ${PBSUtils.getRandomString()}  "  | "   ImpreSSion    "
    }
}
