package org.prebid.server.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.cache.model.CacheBid;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.vast.VastModifier;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client stores values in Prebid Cache.
 * <p>
 * For more info, see https://github.com/prebid/prebid-cache project.
 */
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private static final MultiMap CACHE_HEADERS = HttpUtil.headers();
    private static final Map<String, List<String>> DEBUG_HEADERS = HttpUtil.toDebugHeaders(CACHE_HEADERS);
    private static final String BID_WURL_ATTRIBUTE = "wurl";
    private static final String XML_CREATIVE_TYPE = "xml";
    private static final String JSON_CREATIVE_TYPE = "json";

    private final CacheTtl mediaTypeCacheTtl;
    private final HttpClient httpClient;
    private final URL endpointUrl;
    private final String cachedAssetUrlTemplate;
    private final long expectedCacheTimeMs;
    private final VastModifier vastModifier;
    private final EventsService eventsService;
    private final Metrics metrics;
    private final Clock clock;
    private final UUIDIdGenerator idGenerator;
    private final JacksonMapper mapper;

    public CacheService(CacheTtl mediaTypeCacheTtl,
                        HttpClient httpClient,
                        URL endpointUrl,
                        String cachedAssetUrlTemplate,
                        long expectedCacheTimeMs,
                        VastModifier vastModifier,
                        EventsService eventsService,
                        Metrics metrics,
                        Clock clock,
                        UUIDIdGenerator idGenerator,
                        JacksonMapper mapper) {

        this.mediaTypeCacheTtl = Objects.requireNonNull(mediaTypeCacheTtl);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.cachedAssetUrlTemplate = Objects.requireNonNull(cachedAssetUrlTemplate);
        this.expectedCacheTimeMs = expectedCacheTimeMs;
        this.vastModifier = Objects.requireNonNull(vastModifier);
        this.eventsService = Objects.requireNonNull(eventsService);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public String getEndpointHost() {
        final String host = endpointUrl.getHost();
        final int port = endpointUrl.getPort();
        return port != -1 ? String.format("%s:%d", host, port) : host;
    }

    public String getEndpointPath() {
        return endpointUrl.getPath();
    }

    public String getCachedAssetURLTemplate() {
        return cachedAssetUrlTemplate;
    }

    /**
     * Makes cache for debugLog only and returns generated cache object key without wait for result.
     */
    public String cacheVideoDebugLog(CachedDebugLog cachedDebugLog, Integer videoCacheTtl) {
        final String cacheKey = cachedDebugLog.getCacheKey() == null
                ? idGenerator.generateId()
                : cachedDebugLog.getCacheKey();
        final List<CachedCreative> cachedCreatives = Collections.singletonList(
                makeDebugCacheCreative(cachedDebugLog, cacheKey, videoCacheTtl));
        final BidCacheRequest bidCacheRequest = toBidCacheRequest(cachedCreatives);
        httpClient.post(endpointUrl.toString(), HttpUtil.headers(), mapper.encodeToString(bidCacheRequest),
                expectedCacheTimeMs);
        return cacheKey;
    }

    private CachedCreative makeDebugCacheCreative(CachedDebugLog videoCacheDebugLog, String hbCacheId,
                                                  Integer videoCacheTtl) {
        final JsonNode value = mapper.mapper().valueToTree(videoCacheDebugLog.buildCacheBody());
        videoCacheDebugLog.setCacheKey(hbCacheId);
        return CachedCreative.of(PutObject.builder()
                .type(CachedDebugLog.CACHE_TYPE)
                .value(new TextNode(videoCacheDebugLog.buildCacheBody()))
                .expiry(videoCacheTtl != null ? videoCacheTtl : videoCacheDebugLog.getTtl())
                .key(String.format("log_%s", hbCacheId))
                .build(), creativeSizeFromTextNode(value));
    }

    /**
     * Asks external prebid cache service to store the given value.
     */
    private Future<BidCacheResponse> makeRequest(BidCacheRequest bidCacheRequest,
                                                 int bidCount,
                                                 Timeout timeout,
                                                 String accountId) {

        if (bidCount == 0) {
            return Future.succeededFuture(BidCacheResponse.of(Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.failedFuture(new TimeoutException("Timeout has been exceeded"));
        }

        final long startTime = clock.millis();
        return httpClient.post(endpointUrl.toString(), CACHE_HEADERS, mapper.encodeToString(bidCacheRequest),
                        remainingTimeout)
                .map(response -> toBidCacheResponse(
                        response.getStatusCode(), response.getBody(), bidCount, accountId, startTime))
                .recover(exception -> failResponse(exception, accountId, startTime));
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private Future<BidCacheResponse> failResponse(Throwable exception, String accountId, long startTime) {
        metrics.updateCacheRequestFailedTime(accountId, clock.millis() - startTime);

        logger.warn("Error occurred while interacting with cache service: {0}", exception.getMessage());
        logger.debug("Error occurred while interacting with cache service", exception);

        return Future.failedFuture(exception);
    }

    /**
     * Makes cache for Vtrack puts.
     * <p>
     * Modify VAST value in putObjects and stores in the cache.
     * <p>
     * The returned result will always have the number of elements equals putObjects list size.
     */
    public Future<BidCacheResponse> cachePutObjects(List<PutObject> putObjects,
                                                    Boolean isEventsEnabled,
                                                    Set<String> biddersAllowingVastUpdate,
                                                    String accountId,
                                                    String integration,
                                                    Timeout timeout) {

        final List<CachedCreative> cachedCreatives =
                updatePutObjects(putObjects, isEventsEnabled, biddersAllowingVastUpdate, accountId, integration);

        updateCreativeMetrics(accountId, cachedCreatives);

        return makeRequest(toBidCacheRequest(cachedCreatives), cachedCreatives.size(), timeout, accountId);
    }

    /**
     * Modify VAST value in putObjects.
     */
    private List<CachedCreative> updatePutObjects(List<PutObject> putObjects,
                                                  Boolean isEventsEnabled,
                                                  Set<String> allowedBidders,
                                                  String accountId,
                                                  String integration) {

        return putObjects.stream()
                .map(putObject -> putObject.toBuilder()
                        // remove "/vtrack" specific fields
                        .bidid(null)
                        .bidder(null)
                        .timestamp(null)
                        .value(vastModifier.modifyVastXml(isEventsEnabled,
                                allowedBidders,
                                putObject,
                                accountId,
                                integration))
                        .build())
                .map(payload -> CachedCreative.of(payload, creativeSizeFromTextNode(payload.getValue())))
                .collect(Collectors.toList());
    }

    public Future<CacheServiceResult> cacheBidsOpenrtb(List<BidInfo> bidsToCache,
                                                       AuctionContext auctionContext,
                                                       CacheContext cacheContext,
                                                       EventsContext eventsContext) {

        if (CollectionUtils.isEmpty(bidsToCache)) {
            return Future.succeededFuture(CacheServiceResult.empty());
        }

        final List<Imp> imps = auctionContext.getBidRequest().getImp();
        final boolean isAnyEmptyExpImp = imps.stream()
                .map(Imp::getExp)
                .anyMatch(Objects::isNull);

        final Account account = auctionContext.getAccount();
        final CacheTtl accountCacheTtl = accountCacheTtl(isAnyEmptyExpImp, account);

        final List<CacheBid> cacheBids = cacheContext.isShouldCacheBids()
                ? getCacheBids(bidsToCache, cacheContext.getCacheBidsTtl(), accountCacheTtl)
                : Collections.emptyList();

        final List<CacheBid> videoCacheBids = cacheContext.isShouldCacheVideoBids()
                ? getVideoCacheBids(bidsToCache, cacheContext.getCacheVideoBidsTtl(), accountCacheTtl)
                : Collections.emptyList();

        return doCacheOpenrtb(cacheBids, videoCacheBids, auctionContext, eventsContext);
    }

    /**
     * Fetches {@link CacheTtl} from {@link Account}.
     * <p>
     * Returns empty {@link CacheTtl} when there are no impressions without expiration or
     * if {@link Account} has neither of banner or video cache ttl.
     */
    private CacheTtl accountCacheTtl(boolean impWithNoExpExists, Account account) {
        final AccountAuctionConfig accountAuctionConfig = account.getAuction();
        final Integer bannerCacheTtl = accountAuctionConfig != null ? accountAuctionConfig.getBannerCacheTtl() : null;
        final Integer videoCacheTtl = accountAuctionConfig != null ? accountAuctionConfig.getVideoCacheTtl() : null;

        return impWithNoExpExists && (bannerCacheTtl != null || videoCacheTtl != null)
                ? CacheTtl.of(bannerCacheTtl, videoCacheTtl)
                : CacheTtl.empty();
    }

    private List<CacheBid> getCacheBids(List<BidInfo> bidInfos,
                                        Integer cacheBidsTtl,
                                        CacheTtl accountCacheTtl) {

        return bidInfos.stream()
                .map(bidInfo -> toCacheBid(bidInfo, cacheBidsTtl, accountCacheTtl, false))
                .collect(Collectors.toList());
    }

    private List<CacheBid> getVideoCacheBids(List<BidInfo> bidInfos,
                                             Integer cacheBidsTtl,
                                             CacheTtl accountCacheTtl) {

        return bidInfos.stream()
                .filter(bidInfo -> Objects.equals(bidInfo.getBidType(), BidType.video))
                .map(bidInfo -> toCacheBid(bidInfo, cacheBidsTtl, accountCacheTtl, true))
                .collect(Collectors.toList());
    }

    /**
     * Creates {@link CacheBid} from given {@link BidInfo} and determined cache ttl.
     */
    private CacheBid toCacheBid(BidInfo bidInfo,
                                Integer requestTtl,
                                CacheTtl accountCacheTtl,
                                boolean isVideoBid) {

        final com.iab.openrtb.response.Bid bid = bidInfo.getBid();
        final Integer bidTtl = bid.getExp();
        final Imp correspondingImp = bidInfo.getCorrespondingImp();
        final Integer impTtl = correspondingImp != null ? correspondingImp.getExp() : null;
        final Integer accountMediaTypeTtl = isVideoBid
                ? accountCacheTtl.getVideoCacheTtl()
                : accountCacheTtl.getBannerCacheTtl();
        final Integer mediaTypeTtl = isVideoBid
                ? mediaTypeCacheTtl.getVideoCacheTtl()
                : mediaTypeCacheTtl.getBannerCacheTtl();
        final Integer ttl = ObjectUtils.firstNonNull(bidTtl, impTtl, requestTtl, accountMediaTypeTtl, mediaTypeTtl);

        return CacheBid.of(bidInfo, ttl);
    }

    /**
     * Makes cache for OpenRTB bids.
     * <p>
     * Stores JSON values for the given {@link com.iab.openrtb.response.Bid}s in the cache.
     * Stores XML cache objects for the given video {@link com.iab.openrtb.response.Bid}s in the cache.
     * <p>
     * The returned result will always have the number of elements equals to sum of sizes of bids and video bids.
     */
    private Future<CacheServiceResult> doCacheOpenrtb(List<CacheBid> bids,
                                                      List<CacheBid> videoBids,
                                                      AuctionContext auctionContext,
                                                      EventsContext eventsContext) {

        final Account account = auctionContext.getAccount();
        final String accountId = account.getId();
        final String hbCacheId = videoBids.stream().anyMatch(cacheBid -> cacheBid.getBidInfo().getCategory() != null)
                ? idGenerator.generateId()
                : null;
        final String requestId = auctionContext.getBidRequest().getId();
        final List<CachedCreative> cachedCreatives = Stream.concat(
                        bids.stream().map(cacheBid ->
                                createJsonPutObjectOpenrtb(cacheBid, accountId, eventsContext)),
                        videoBids.stream().map(videoBid -> createXmlPutObjectOpenrtb(videoBid, requestId, hbCacheId)))
                .collect(Collectors.toList());

        if (cachedCreatives.isEmpty()) {
            return Future.succeededFuture(CacheServiceResult.empty());
        }

        final CachedDebugLog cachedDebugLog = auctionContext.getCachedDebugLog();

        final Integer videoCacheTtl = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getVideoCacheTtl);
        if (CollectionUtils.isNotEmpty(cachedCreatives) && cachedDebugLog != null && cachedDebugLog.isEnabled()) {
            cachedCreatives.add(makeDebugCacheCreative(cachedDebugLog, hbCacheId, videoCacheTtl));
        }

        final long remainingTimeout = auctionContext.getTimeout().remaining();
        if (remainingTimeout <= 0) {
            return Future.succeededFuture(CacheServiceResult.of(null, new TimeoutException("Timeout has been exceeded"),
                    Collections.emptyMap()));
        }

        final BidCacheRequest bidCacheRequest = toBidCacheRequest(cachedCreatives);

        updateCreativeMetrics(accountId, cachedCreatives);

        final String url = endpointUrl.toString();
        final String body = mapper.encodeToString(bidCacheRequest);
        final CacheHttpRequest httpRequest = CacheHttpRequest.of(url, body);

        final long startTime = clock.millis();
        return httpClient.post(url, CACHE_HEADERS, body, remainingTimeout)
                .map(response -> processResponseOpenrtb(response,
                        httpRequest,
                        cachedCreatives.size(),
                        bids,
                        videoBids,
                        hbCacheId,
                        accountId,
                        startTime))
                .otherwise(exception -> failResponseOpenrtb(exception, accountId, httpRequest, startTime));
    }

    /**
     * Creates {@link CacheServiceResult} from the given {@link HttpClientResponse}.
     */
    private CacheServiceResult processResponseOpenrtb(HttpClientResponse response,
                                                      CacheHttpRequest httpRequest,
                                                      int bidCount,
                                                      List<CacheBid> bids,
                                                      List<CacheBid> videoBids,
                                                      String hbCacheId,
                                                      String accountId,
                                                      long startTime) {

        final CacheHttpResponse httpResponse = CacheHttpResponse.of(response.getStatusCode(), response.getBody());
        final int responseStatusCode = response.getStatusCode();
        final DebugHttpCall httpCall = makeDebugHttpCall(endpointUrl.toString(), httpRequest, httpResponse, startTime);
        final BidCacheResponse bidCacheResponse;
        try {
            bidCacheResponse = toBidCacheResponse(
                    responseStatusCode, response.getBody(), bidCount, accountId, startTime);
        } catch (PreBidException e) {
            return CacheServiceResult.of(httpCall, e, Collections.emptyMap());
        }

        final List<String> uuids = toResponse(bidCacheResponse, CacheObject::getUuid);
        return CacheServiceResult.of(httpCall, null, toResultMap(bids, videoBids, uuids, hbCacheId));
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private CacheServiceResult failResponseOpenrtb(Throwable exception,
                                                   String accountId,
                                                   CacheHttpRequest request,
                                                   long startTime) {

        logger.warn("Error occurred while interacting with cache service: {0}", exception.getMessage());
        logger.debug("Error occurred while interacting with cache service", exception);

        metrics.updateCacheRequestFailedTime(accountId, clock.millis() - startTime);

        final DebugHttpCall httpCall = makeDebugHttpCall(endpointUrl.toString(), request, null, startTime);
        return CacheServiceResult.of(httpCall, exception, Collections.emptyMap());
    }

    /**
     * Creates {@link DebugHttpCall} from {@link CacheHttpRequest} and {@link CacheHttpResponse}, endpoint
     * and starttime.
     */
    private DebugHttpCall makeDebugHttpCall(String endpoint,
                                            CacheHttpRequest httpRequest,
                                            CacheHttpResponse httpResponse,
                                            long startTime) {

        return DebugHttpCall.builder()
                .endpoint(endpoint)
                .requestUri(httpRequest != null ? httpRequest.getUri() : null)
                .requestBody(httpRequest != null ? httpRequest.getBody() : null)
                .responseStatus(httpResponse != null ? httpResponse.getStatusCode() : null)
                .responseBody(httpResponse != null ? httpResponse.getBody() : null)
                .responseTimeMillis(responseTime(startTime))
                .requestHeaders(DEBUG_HEADERS)
                .build();
    }

    /**
     * Calculates execution time since the given start time.
     */
    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }

    /**
     * Makes JSON type {@link PutObject} from {@link com.iab.openrtb.response.Bid}.
     * Used for OpenRTB auction request. Also, adds win url to result object if events are enabled.
     */
    private CachedCreative createJsonPutObjectOpenrtb(CacheBid cacheBid,
                                                      String accountId,
                                                      EventsContext eventsContext) {

        final BidInfo bidInfo = cacheBid.getBidInfo();
        final com.iab.openrtb.response.Bid bid = bidInfo.getBid();
        final ObjectNode bidObjectNode = mapper.mapper().valueToTree(bid);

        final String eventUrl =
                generateWinUrl(bidInfo.getBidId(),
                        bidInfo.getBidder(),
                        accountId,
                        eventsContext,
                        bidInfo.getLineItemId());
        if (eventUrl != null) {
            bidObjectNode.put(BID_WURL_ATTRIBUTE, eventUrl);
        }

        final PutObject payload = PutObject.builder()
                .aid(eventsContext.getAuctionId())
                .type("json")
                .value(bidObjectNode)
                .ttlseconds(cacheBid.getTtl())
                .build();

        return CachedCreative.of(payload, creativeSizeFromAdm(bid.getAdm()));
    }

    /**
     * Makes XML type {@link PutObject} from {@link com.iab.openrtb.response.Bid}. Used for OpenRTB auction request.
     */
    private CachedCreative createXmlPutObjectOpenrtb(CacheBid cacheBid, String requestId, String hbCacheId) {
        final BidInfo bidInfo = cacheBid.getBidInfo();
        final com.iab.openrtb.response.Bid bid = bidInfo.getBid();
        final String vastXml = bid.getAdm();

        final String customCacheKey = resolveCustomCacheKey(hbCacheId, bidInfo.getCategory());

        final PutObject payload = PutObject.builder()
                .aid(requestId)
                .type("xml")
                .value(vastXml != null ? new TextNode(vastXml) : null)
                .ttlseconds(cacheBid.getTtl())
                .key(customCacheKey)
                .build();

        return CachedCreative.of(payload, creativeSizeFromTextNode(payload.getValue()));
    }

    private static String resolveCustomCacheKey(String hbCacheId, String category) {
        return StringUtils.isNoneEmpty(category, hbCacheId)
                ? String.format("%s_%s", category, hbCacheId)
                : null;
    }

    private String generateWinUrl(String bidId,
                                  String bidder,
                                  String accountId,
                                  EventsContext eventsContext,
                                  String lineItemId) {

        if (!eventsContext.isEnabledForAccount()) {
            return null;
        }

        if (eventsContext.isEnabledForRequest() || StringUtils.isNotBlank(lineItemId)) {
            return eventsService.winUrl(
                    bidId,
                    bidder,
                    accountId,
                    lineItemId,
                    eventsContext.isEnabledForRequest(),
                    eventsContext);
        }

        return null;
    }

    /**
     * Handles http response, analyzes response status and creates {@link BidCacheResponse} from response body
     * or throws {@link PreBidException} in case of errors.
     */
    private BidCacheResponse toBidCacheResponse(int statusCode,
                                                String responseBody,
                                                int bidCount,
                                                String accountId,
                                                long startTime) {

        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final BidCacheResponse bidCacheResponse;
        try {
            bidCacheResponse = mapper.decodeValue(responseBody, BidCacheResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", responseBody), e);
        }

        final List<CacheObject> responses = bidCacheResponse.getResponses();
        if (responses == null || responses.size() != bidCount) {
            throw new PreBidException("The number of response cache objects doesn't match with bids");
        }

        metrics.updateCacheRequestSuccessTime(accountId, clock.millis() - startTime);
        return bidCacheResponse;
    }

    /**
     * Creates prebid cache service response according to the creator.
     */
    private <T> List<T> toResponse(BidCacheResponse bidCacheResponse, Function<CacheObject, T> responseItemCreator) {
        return bidCacheResponse.getResponses().stream()
                .filter(Objects::nonNull)
                .map(responseItemCreator)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Creates a map with bids as a key and {@link CacheInfo} as a value from obtained UUIDs.
     */
    private static Map<com.iab.openrtb.response.Bid, CacheInfo> toResultMap(List<CacheBid> cacheBids,
                                                                            List<CacheBid> cacheVideoBids,
                                                                            List<String> uuids,
                                                                            String hbCacheId) {

        final Map<com.iab.openrtb.response.Bid, CacheInfo> result = new HashMap<>(uuids.size());

        // here we assume "videoBids" is a sublist of "bids"
        // so, no need for a separate loop on "videoBids" if "bids" is not empty
        if (!cacheBids.isEmpty()) {
            final List<com.iab.openrtb.response.Bid> videoBids = cacheVideoBids.stream()
                    .map(CacheBid::getBidInfo)
                    .map(BidInfo::getBid)
                    .collect(Collectors.toList());

            final int bidsSize = cacheBids.size();
            for (int i = 0; i < bidsSize; i++) {
                final CacheBid cacheBid = cacheBids.get(i);
                final BidInfo bidInfo = cacheBid.getBidInfo();
                final com.iab.openrtb.response.Bid bid = bidInfo.getBid();
                final Integer ttl = cacheBid.getTtl();

                // determine uuid for video bid
                final int indexOfVideoBid = videoBids.indexOf(bid);
                final String videoBidUuid = indexOfVideoBid != -1 ? uuids.get(bidsSize + indexOfVideoBid) : null;
                final Integer videoTtl = indexOfVideoBid != -1 ? cacheVideoBids.get(indexOfVideoBid).getTtl() : null;

                result.put(bid, CacheInfo.of(uuids.get(i), resolveVideoBidUuid(videoBidUuid, hbCacheId), ttl,
                        videoTtl));
            }
        } else {
            for (int i = 0; i < cacheVideoBids.size(); i++) {
                final CacheBid cacheBid = cacheVideoBids.get(i);
                final BidInfo bidInfo = cacheBid.getBidInfo();
                result.put(bidInfo.getBid(), CacheInfo.of(null, resolveVideoBidUuid(uuids.get(i), hbCacheId), null,
                        cacheBid.getTtl()));
            }
        }

        return result;
    }

    private static String resolveVideoBidUuid(String uuid, String hbCacheId) {
        return hbCacheId != null && uuid.endsWith(hbCacheId) ? hbCacheId : uuid;
    }

    /**
     * Composes prebid cache service url against the given schema and host.
     */
    public static URL getCacheEndpointUrl(String cacheSchema, String cacheHost, String path) {
        try {
            final URL baseUrl = getCacheBaseUrl(cacheSchema, cacheHost);
            return new URL(baseUrl, path);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cache endpoint for prebid cache service", e);
        }
    }

    /**
     * Composes cached asset url template against the given query, schema and host.
     */
    public static String getCachedAssetUrlTemplate(String cacheSchema,
                                                   String cacheHost,
                                                   String path,
                                                   String cacheQuery) {

        try {
            final URL baseUrl = getCacheBaseUrl(cacheSchema, cacheHost);
            return new URL(baseUrl, path + "?" + cacheQuery).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cached asset url template for prebid cache service", e);
        }
    }

    /**
     * Returns prebid cache service url or throws {@link MalformedURLException} if error occurs.
     */
    private static URL getCacheBaseUrl(String cacheSchema, String cacheHost) throws MalformedURLException {
        return new URL(cacheSchema + "://" + cacheHost);
    }

    private void updateCreativeMetrics(String accountId, List<CachedCreative> cachedCreatives) {
        for (final CachedCreative cachedCreative : cachedCreatives) {
            metrics.updateCacheCreativeSize(accountId,
                    cachedCreative.getSize(),
                    resolveCreativeTypeName(cachedCreative.getPayload()));
        }
    }

    private static MetricName resolveCreativeTypeName(PutObject putObject) {
        final String typeValue = ObjectUtil.getIfNotNull(putObject, PutObject::getType);

        if (Objects.equals(typeValue, XML_CREATIVE_TYPE)) {
            return MetricName.xml;
        }

        if (Objects.equals(typeValue, JSON_CREATIVE_TYPE)) {
            return MetricName.json;
        }

        return MetricName.unknown;
    }

    private static int creativeSizeFromAdm(String adm) {
        return lengthOrZero(adm);
    }

    private static int lengthOrZero(String adm) {
        return adm != null ? adm.length() : 0;
    }

    private static int creativeSizeFromTextNode(JsonNode node) {
        return node != null ? node.asText().length() : 0;
    }

    private BidCacheRequest toBidCacheRequest(List<CachedCreative> cachedCreatives) {
        return BidCacheRequest.of(cachedCreatives.stream()
                .map(CachedCreative::getPayload)
                .collect(Collectors.toList()));
    }

    @Value(staticConstructor = "of")
    private static class CachedCreative {

        PutObject payload;

        int size;
    }
}
