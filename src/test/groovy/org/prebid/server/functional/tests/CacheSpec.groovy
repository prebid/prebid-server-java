package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountCacheConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.response.auction.ErrorType.CACHE
import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class CacheSpec extends BaseSpec {

    private static final String PBS_API_HEADER = 'x-pbc-api-key'
    private static final Integer MAX_DATACENTER_REGION_LENGTH = 4
    private static final Integer DEFAULT_UUID_LENGTH = 36
    private static final Integer TARGETING_PARAM_NAME_MAX_LENGTH = 20

    private static final String ACCOUNT_JSON_CREATIVE_SIZE_METRIC = "account.%s.prebid_cache.creative_size.json"
    private static final String ACCOUNT_XML_CREATIVE_SIZE_METRIC = "account.%s.prebid_cache.creative_size.xml"
    private static final String ACCOUNT_XML_CREATIVE_TTL_METRIC = "account.%s.prebid_cache.creative_ttl.xml"
    private static final String ACCOUNT_JSON_CREATIVE_TTL_METRIC = "account.%s.prebid_cache.creative_ttl.json"

    private static final String ACCOUNT_REQUEST_OK_METRIC = "account.%s.prebid_cache.requests.ok"
    private static final String REQUEST_OK_METRIC = "prebid_cache.requests.ok"

    private static final String JSON_CREATIVE_SIZE_GLOBAL_METRIC = "prebid_cache.creative_size.json"
    private static final String XML_CREATIVE_SIZE_GLOBAL_METRIC = "prebid_cache.creative_size.xml"
    private static final String XML_CREATIVE_TTL_METRIC = "prebid_cache.creative_ttl.xml"
    private static final String JSON_CREATIVE_TTL_METRIC = "prebid_cache.creative_ttl.json"

    private static final String CACHE_PATH = "/${PBSUtils.randomString}".toString()
    private static final String CACHE_HOST = "${PBSUtils.randomString}:${PBSUtils.getRandomNumber(0, 65535)}".toString()
    private static final String INTERNAL_CACHE_PATH = '/cache'
    private static final String HTTP_SCHEME = 'http'
    private static final String HTTPS_SCHEME = 'https'

    def "PBS should update prebid_cache.creative_size.json metric when json creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, REQUEST_OK_METRIC)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
        }

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
        assert metrics[REQUEST_OK_METRIC] == initialValue + 1
        assert metrics[JSON_CREATIVE_SIZE_GLOBAL_METRIC] == creativeSize

        and: "account.<account-id>.prebid_cache.creative_size.json should be update"
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1
        assert metrics[ACCOUNT_JSON_CREATIVE_SIZE_METRIC.formatted(bidRequest.accountId)] == creativeSize
    }

    def "PBS should update prebid_cache.creative_size.xml metric when video bid and xml creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, REQUEST_OK_METRIC)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultVideoRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

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
        assert metrics[REQUEST_OK_METRIC] == initialValue + 1
        assert metrics[XML_CREATIVE_SIZE_GLOBAL_METRIC] == creativeSize

        and: "account.<account-id>.prebid_cache.creative_size.json should be update"
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1
        assert metrics[ACCOUNT_XML_CREATIVE_SIZE_METRIC.formatted(bidRequest.accountId)] == creativeSize
    }

    def "PBS should cache bids when targeting is specified"() {
        given: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

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
        def pbsConfig = ['pbc.api.key': apiKey, 'cache.api-key-secured': 'false']
        def pbsService = pbsServiceFactory.getService(['pbc.api.key': apiKey, 'cache.api-key-secured': 'false'])

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS call shouldn't include api-key"
        assert !prebidCache.getRequestHeaders(bidRequest.imp[0].id)[PBS_API_HEADER]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should cache bids with api-key header when targeting is specified and api-key-secured enabled"() {
        given: "Pbs config with api-key-secured and pbc.api.key"
        def apiKey = PBSUtils.randomString
        def pbsConfig = ['pbc.api.key': apiKey, 'cache.api-key-secured': 'true']
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS call should include api-key"
        assert prebidCache.getRequestHeaders(bidRequest.imp[0].id)[PBS_API_HEADER] == [apiKey]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
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
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS cache key should start with account and datacenter short name"
        def cacheKey = prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key
        assert cacheKey.startsWith("${bidRequest.accountId}-${serverDataCenter.take(MAX_DATACENTER_REGION_LENGTH)}")

        and: "PBS cache key should have length equal to default UUID"
        assert cacheKey.length() == DEFAULT_UUID_LENGTH

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

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
        def bidRequest = BidRequest.defaultVideoRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

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
        assert cacheKey.length() == DEFAULT_UUID_LENGTH

        and: "PBS should include metrics for account"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == videoHostTtl
        assert metrics[ACCOUNT_XML_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == videoHostTtl
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        and: "PBS should include metrics prebid_cache_creative.{xml,json}.creative.ttl for general"
        assert metrics[JSON_CREATIVE_TTL_METRIC] == videoHostTtl
        assert metrics[XML_CREATIVE_TTL_METRIC] == videoHostTtl

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
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS cache key should start with account and datacenter short name"
        def cacheKey = prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key
        assert cacheKey.startsWith("${bidRequest.accountId}-")

        and: "PBS cache key should have length equal to default UUID"
        assert cacheKey.length() == DEFAULT_UUID_LENGTH

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

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
        def accountOverflowLength = DEFAULT_UUID_LENGTH - MAX_DATACENTER_REGION_LENGTH - 2
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
            it.setAccountId(PBSUtils.getRandomString(accountOverflowLength))
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS shouldn't contain cache key"
        assert !prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

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
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS shouldn't contain cache key"
        assert !prebidCache.getRecordedRequests(bidRequest.imp.id.first).puts.flatten().first.key

        and: "PBS should include metrics for request"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_JSON_CREATIVE_TTL_METRIC.formatted(bidRequest.accountId)] == bannerHostTtl
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should not cache bids when targeting isn't specified"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.targeting = null
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 0
    }

    def "PBS shouldn't response with seatbid.bid.adm in response when ext.prebid.cache.bids.returnCreative=false"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.ext.prebid.cache.bids.returnCreative = false
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
            it.enableCache()
            it.ext.prebid.cache.bids.returnCreative = true
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
            it.enableCache()
            it.imp[0] = Imp.getDefaultImpression(VIDEO)
            it.ext.prebid.cache.vastXml.returnCreative = false
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
            it.enableCache()
            it.imp[0] = Imp.getDefaultImpression(mediaType)
            it.ext.prebid.cache.vastXml.returnCreative = returnCreative
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

    def "PBS shouldn't cache bids when targeting is specified and config cache is invalid"() {
        given: "Pbs config with cache"
        def INVALID_PREBID_CACHE_CONFIG = ["cache.path"  : CACHE_PATH,
                                           "cache.scheme": HTTP_SCHEME,
                                           "cache.host"  : CACHE_HOST]
        def pbsService = pbsServiceFactory.getService(INVALID_PREBID_CACHE_CONFIG)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert bidResponse.ext?.errors[CACHE]*.code == [999]
        assert bidResponse.ext?.errors[CACHE]*.message[0] == ("Failed to resolve '${CACHE_HOST.tokenize(":")[0]}' [A(1)]")

        and: "Bid response targeting should contain value"
        assert bidResponse.seatbid[0].bid[0].ext.prebid.targeting.findAll { it.key.startsWith("hb_cache") }.isEmpty()

        and: "PBS shouldn't call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 0

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(INVALID_PREBID_CACHE_CONFIG)
    }

    def "PBS should cache bids and emit error when targeting is specified and config cache is valid and internal is invalid"() {
        given: "Pbs config with cache"
        def INVALID_PREBID_CACHE_CONFIG = ["cache.internal.path"  : CACHE_PATH,
                                           "cache.internal.scheme": HTTP_SCHEME,
                                           "cache.internal.host"  : CACHE_HOST]
        def pbsService = pbsServiceFactory.getService(INVALID_PREBID_CACHE_CONFIG)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 0

        and: "Seat bid shouldn't be discarded"
        assert !bidResponse.seatbid.isEmpty()

        and: "Bid response targeting should contain value"
        assert bidResponse.seatbid[0].bid[0].ext.prebid.targeting.findAll { it.key.startsWith("hb_cache") }.isEmpty()

        and: "Debug should contain http call with empty response body"
        def cacheCall = bidResponse.ext.debug.httpcalls['cache'][0]
        assert cacheCall.responseBody == null
        assert cacheCall.uri == "${HTTP_SCHEME}://${networkServiceContainer.hostAndPort + INTERNAL_CACHE_PATH}"

        then: "Response should contain error"
        assert bidResponse.ext?.errors[CACHE]*.code == [999]
        assert bidResponse.ext?.errors[CACHE]*.message[0] == ("Failed to resolve '${CACHE_HOST.tokenize(":")[0]}' [A(1)]")

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(INVALID_PREBID_CACHE_CONFIG)
    }

    def "PBS should cache bids when targeting is specified and config cache is invalid and internal cache config valid"() {
        given: "Pbs config with cache"
        def INVALID_PREBID_CACHE_CONFIG = ["cache.path"  : CACHE_PATH,
                                           "cache.scheme": HTTPS_SCHEME,
                                           "cache.host"  : CACHE_HOST,]
        def VALID_INTERNAL_CACHE_CONFIG = ["cache.internal.scheme": HTTP_SCHEME,
                                           "cache.internal.host"  : "$networkServiceContainer.hostAndPort".toString(),
                                           "cache.internal.path"  : INTERNAL_CACHE_PATH,]
        def pbsService = pbsServiceFactory.getService(INVALID_PREBID_CACHE_CONFIG + VALID_INTERNAL_CACHE_CONFIG)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "Bid response targeting should contain value"
        verifyAll(bidResponse?.seatbid[0]?.bid[0]?.ext?.prebid?.targeting as Map) {
            it.get("hb_cache_id")
            it.get("hb_cache_id_generic")
            it.get("hb_cache_path") == CACHE_PATH
            it.get("hb_cache_host") == CACHE_HOST
            it.get("hb_cache_path_generic".substring(0, TARGETING_PARAM_NAME_MAX_LENGTH)) == CACHE_PATH
            it.get("hb_cache_host_generic".substring(0, TARGETING_PARAM_NAME_MAX_LENGTH)) == CACHE_HOST
        }

        and: "Debug should contain http call"
        assert bidResponse.ext.debug.httpcalls['cache'][0].uri ==
                "${HTTPS_SCHEME}://${CACHE_HOST + CACHE_PATH}"

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(INVALID_PREBID_CACHE_CONFIG + VALID_INTERNAL_CACHE_CONFIG)
    }

    def "PBS should cache bids when targeting is specified and config cache and internal cache config valid"() {
        given: "Pbs config with cache"
        def VALID_INTERNAL_CACHE_CONFIG = ["cache.internal.scheme": HTTP_SCHEME,
                                           "cache.internal.host"  : "$networkServiceContainer.hostAndPort".toString(),
                                           "cache.internal.path"  : INTERNAL_CACHE_PATH]
        def pbsService = pbsServiceFactory.getService(VALID_INTERNAL_CACHE_CONFIG)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.enableCache()
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "Bid response targeting should contain value"
        verifyAll(bidResponse.seatbid[0].bid[0].ext.prebid.targeting) {
            it.get("hb_cache_id")
            it.get("hb_cache_id_generic")
            it.get("hb_cache_path") == INTERNAL_CACHE_PATH
            it.get("hb_cache_host") == networkServiceContainer.hostAndPort.toString()
            it.get("hb_cache_path_generic".substring(0, TARGETING_PARAM_NAME_MAX_LENGTH)) == INTERNAL_CACHE_PATH
            it.get("hb_cache_host_generic".substring(0, TARGETING_PARAM_NAME_MAX_LENGTH)) == networkServiceContainer.hostAndPort.toString()
        }

        and: "Debug should contain http call"
        assert bidResponse.ext.debug.httpcalls['cache'][0].uri ==
                "${HTTP_SCHEME}://${networkServiceContainer.hostAndPort + INTERNAL_CACHE_PATH}"

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(VALID_INTERNAL_CACHE_CONFIG)
    }

    def "PBS should cache bids and add targeting values when account cache config #accountAuctionConfig"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, REQUEST_OK_METRIC)

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.getDefaultVideoRequest().tap {
            it.enableCache()
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Default bid response"
        def presetBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, presetBidResponse)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1

        and: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap.containsKey('hb_cache_id')
        assert targetingKeyMap.containsKey("hb_cache_id_${GENERIC}".toString())
        assert targetingKeyMap.containsKey('hb_uuid')
        assert targetingKeyMap.containsKey("hb_uuid_${GENERIC}".toString())

        and: "Metrics should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[REQUEST_OK_METRIC] == initialValue + 1
        assert metrics[ACCOUNT_REQUEST_OK_METRIC.formatted(bidRequest.accountId)] == 1

        where:
        accountAuctionConfig << [
                new AccountAuctionConfig(),
                new AccountAuctionConfig(cache: new AccountCacheConfig()),
                new AccountAuctionConfig(cache: new AccountCacheConfig(enabled: null)),
                new AccountAuctionConfig(cache: new AccountCacheConfig(enabled: true))
        ]
    }

    def "PBS shouldn't cache bids and add targeting values when account cache config disabled"() {
        given: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.getDefaultVideoRequest().tap {
            it.enableCache()
        }

        and: "Account with cache config"
        def accountAuctionConfig = new AccountAuctionConfig(cache: new AccountCacheConfig(enabled: false))
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Default bid response"
        def presetBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, presetBidResponse)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't call PBC"
        assert !prebidCache.getRequestCount(bidRequest.imp[0].id)

        and: "PBS response targeting shouldn't contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert !targetingKeyMap.containsKey('hb_cache_id')
        assert !targetingKeyMap.containsKey("hb_cache_id_${GENERIC}".toString())
        assert !targetingKeyMap.containsKey('hb_uuid')
        assert !targetingKeyMap.containsKey("hb_uuid_${GENERIC}".toString())
    }
}
