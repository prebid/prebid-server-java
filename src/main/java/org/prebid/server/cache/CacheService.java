package org.prebid.server.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.GeneratedBidIds;
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
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.util.ArrayList;
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

    private static final String BID_WURL_ATTRIBUTE = "wurl";

    private final CacheTtl mediaTypeCacheTtl;
    private final HttpClient httpClient;
    private final URL endpointUrl;
    private final String cachedAssetUrlTemplate;
    private final long expectedCacheTimeMs;
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
        httpClient.post(endpointUrl.toString(), HttpUtil.headers(), mapper.encode(bidCacheRequest),
                expectedCacheTimeMs);
        return cacheKey;
    }

    /**
     * Asks external prebid cache service to store the given value.
     */
    private Future<BidCacheResponse> makeRequest(
            BidCacheRequest bidCacheRequest, int bidCount, Timeout timeout, String accountId) {

        if (bidCount == 0) {
            return Future.succeededFuture(BidCacheResponse.of(Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.failedFuture(new TimeoutException("Timeout has been exceeded"));
        }

        final long startTime = clock.millis();
        return httpClient.post(endpointUrl.toString(), HttpUtil.headers(), mapper.encode(bidCacheRequest),
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
                                                    Set<String> biddersAllowingVastUpdate,
                                                    String accountId,
                                                    String integration, Timeout timeout) {

        final List<CachedCreative> cachedCreatives =
                updatePutObjects(putObjects, biddersAllowingVastUpdate, accountId, integration);

        updateCreativeMetrics(accountId, cachedCreatives);

        return makeRequest(toBidCacheRequest(cachedCreatives), cachedCreatives.size(), timeout, accountId);
    }

    /**
     * Modify VAST value in putObjects.
     */
    private List<CachedCreative> updatePutObjects(List<PutObject> putObjects,
                                                  Set<String> biddersAllowingVastUpdate,
                                                  String accountId,
                                                  String integration) {

        final List<CachedCreative> result = new ArrayList<>();

        for (final PutObject putObject : putObjects) {
            final PutObject.PutObjectBuilder builder = putObject.toBuilder()
                    // remove "/vtrack" specific fields
                    .bidid(null)
                    .bidder(null)
                    .timestamp(null);

            final JsonNode value = putObject.getValue();
            if (biddersAllowingVastUpdate.contains(putObject.getBidder()) && value != null) {
                final String eventUrl = eventsService.vastUrlTracking(
                        putObject.getBidid(),
                        putObject.getBidder(),
                        accountId,
                        putObject.getTimestamp(),
                        integration);
                final String updatedVastXml = appendTrackingUrlToVastXml(value.asText(), eventUrl);
                builder.value(new TextNode(updatedVastXml)).build();
            }

            final PutObject payload = builder.build();

            result.add(CachedCreative.of(payload, creativeSizeFromTextNode(payload.getValue())));
        }

        return result;
    }

    /**
     * Makes cache for OpenRTB {@link com.iab.openrtb.response.Bid}s.
     */
    public Future<CacheServiceResult> cacheBidsOpenrtb(List<com.iab.openrtb.response.Bid> bids,
                                                       AuctionContext auctionContext,
                                                       CacheContext cacheContext,
                                                       EventsContext eventsContext) {

        if (CollectionUtils.isEmpty(bids)) {
            return Future.succeededFuture(CacheServiceResult.empty());
        }

        final List<Imp> imps = auctionContext.getBidRequest().getImp();

        final Map<String, Integer> impIdToTtl = new HashMap<>(imps.size());
        boolean impWithNoExpExists = false; // indicates at least one impression without expire presents
        final List<String> videoImpIds = new ArrayList<>();
        final boolean shouldCacheVideoBids = cacheContext.isShouldCacheVideoBids();
        for (final Imp imp : imps) {
            final String impId = imp.getId();
            impIdToTtl.put(impId, imp.getExp());
            impWithNoExpExists |= imp.getExp() == null;
            if (shouldCacheVideoBids && impId != null && imp.getVideo() != null) {
                videoImpIds.add(impId);
            }
        }

        final Account account = auctionContext.getAccount();

        final List<CacheBid> cacheBids = getCacheBids(cacheContext.isShouldCacheBids(), bids, impIdToTtl,
                impWithNoExpExists, cacheContext.getCacheBidsTtl(), account);

        final List<CacheBid> videoCacheBids = getVideoCacheBids(shouldCacheVideoBids, bids,
                impIdToTtl, videoImpIds, impWithNoExpExists, cacheContext.getCacheVideoBidsTtl(), account);

        return doCacheOpenrtb(
                cacheBids,
                videoCacheBids,
                auctionContext,
                cacheContext,
                eventsContext);
    }

    /**
     * Creates list of {@link CacheBid}s from the list of {@link com.iab.openrtb.response.Bid}s.
     */
    private List<CacheBid> getCacheBids(boolean shouldCacheBids,
                                        List<com.iab.openrtb.response.Bid> bids,
                                        Map<String, Integer> impIdToTtl,
                                        boolean impWithNoExpExists,
                                        Integer cacheBidsTtl,
                                        Account account) {

        return shouldCacheBids
                ? bids.stream()
                .map(bid -> toCacheBid(bid, impIdToTtl, cacheBidsTtl,
                        accountCacheTtlFrom(impWithNoExpExists, account), false))
                .collect(Collectors.toList())
                : Collections.emptyList();
    }

    /**
     * Creates list of video {@link CacheBid}s from the list of {@link com.iab.openrtb.response.Bid}s.
     */
    private List<CacheBid> getVideoCacheBids(
            boolean shouldCacheVideoBids, List<com.iab.openrtb.response.Bid> bids, Map<String, Integer> impIdToTtl,
            List<String> videoImpIds, boolean impWithNoExpExists, Integer cacheVideoBidsTtl, Account account) {

        return shouldCacheVideoBids
                ? bids.stream()
                .filter(bid -> videoImpIds.contains(bid.getImpid())) // bid is video
                .map(bid -> toCacheBid(bid, impIdToTtl, cacheVideoBidsTtl,
                        accountCacheTtlFrom(impWithNoExpExists, account), true))
                .collect(Collectors.toList())
                : Collections.emptyList();
    }

    /**
     * Fetches {@link CacheTtl} from {@link Account}.
     * <p>
     * Returns empty {@link CacheTtl} when there are no impressions without expiration or
     * if{@link Account} has neither of banner or video cache ttl.
     */
    private CacheTtl accountCacheTtlFrom(boolean impWithNoExpExists, Account account) {
        return impWithNoExpExists && (account.getBannerCacheTtl() != null || account.getVideoCacheTtl() != null)
                ? CacheTtl.of(account.getBannerCacheTtl(), account.getVideoCacheTtl())
                : CacheTtl.empty();
    }

    /**
     * Creates {@link CacheBid} from given {@link com.iab.openrtb.response.Bid} and determined cache ttl.
     */
    private CacheBid toCacheBid(com.iab.openrtb.response.Bid bid,
                                Map<String, Integer> impIdToTtl,
                                Integer requestTtl,
                                CacheTtl accountCacheTtl,
                                boolean isVideoBid) {
        final Integer bidTtl = bid.getExp();
        final Integer impTtl = impIdToTtl.get(bid.getImpid());
        final Integer accountMediaTypeTtl = isVideoBid
                ? accountCacheTtl.getVideoCacheTtl()
                : accountCacheTtl.getBannerCacheTtl();
        final Integer mediaTypeTtl = isVideoBid
                ? mediaTypeCacheTtl.getVideoCacheTtl()
                : mediaTypeCacheTtl.getBannerCacheTtl();
        final Integer ttl = ObjectUtils.firstNonNull(bidTtl, impTtl, requestTtl, accountMediaTypeTtl, mediaTypeTtl);

        return CacheBid.of(bid, ttl);
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
                                                      CacheContext cacheContext,
                                                      EventsContext eventsContext) {

        final Account account = auctionContext.getAccount();
        final GeneratedBidIds biddersToCacheBidIds = cacheContext.getBidderToBidsToGeneratedIds();
        final GeneratedBidIds bidderToVideoBidIdsToModify = cacheContext.getBidderToVideoGeneratedBidIdsToModify();

        final Map<String, Map<String, String>> biddersToBidsCategories = cacheContext.getBiddersToBidsCategories();
        final String hbCacheId = MapUtils.isEmpty(biddersToBidsCategories) ? null : idGenerator.generateId();
        final List<CachedCreative> cachedCreatives = Stream.concat(
                bids.stream().map(cacheBid -> createJsonPutObjectOpenrtb(
                        cacheBid, biddersToCacheBidIds, account, eventsContext)),
                videoBids.stream().map(cacheBid -> createXmlPutObjectOpenrtb(cacheBid, bidderToVideoBidIdsToModify,
                        hbCacheId, biddersToBidsCategories, account, eventsContext)))
                .collect(Collectors.toList());

        if (cachedCreatives.isEmpty()) {
            return Future.succeededFuture(CacheServiceResult.empty());
        }

        final CachedDebugLog cachedDebugLog = auctionContext.getCachedDebugLog();
        final Integer videoCacheTtl = account.getVideoCacheTtl();
        if (CollectionUtils.isNotEmpty(cachedCreatives) && cachedDebugLog != null && cachedDebugLog.isEnabled()) {
            cachedCreatives.add(makeDebugCacheCreative(cachedDebugLog, hbCacheId, videoCacheTtl));
        }

        final long remainingTimeout = auctionContext.getTimeout().remaining();
        if (remainingTimeout <= 0) {
            return Future.succeededFuture(CacheServiceResult.of(null, new TimeoutException("Timeout has been exceeded"),
                    Collections.emptyMap()));
        }

        final BidCacheRequest bidCacheRequest = toBidCacheRequest(cachedCreatives);

        final String accountId = account.getId();
        updateCreativeMetrics(accountId, cachedCreatives);

        final String url = endpointUrl.toString();
        final String body = mapper.encode(bidCacheRequest);
        final CacheHttpRequest httpRequest = CacheHttpRequest.of(url, body);

        final long startTime = clock.millis();
        return httpClient.post(url, HttpUtil.headers(), body, remainingTimeout)
                .map(response -> processResponseOpenrtb(response, httpRequest, cachedCreatives.size(), bids, videoBids,
                        hbCacheId, accountId, startTime))
                .otherwise(exception -> failResponseOpenrtb(exception, accountId, httpRequest, startTime));
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
    private DebugHttpCall makeDebugHttpCall(String endpoint, CacheHttpRequest httpRequest,
                                            CacheHttpResponse httpResponse, long startTime) {
        return DebugHttpCall.builder()
                .endpoint(endpoint)
                .requestUri(httpRequest != null ? httpRequest.getUri() : null)
                .requestBody(httpRequest != null ? httpRequest.getBody() : null)
                .responseStatus(httpResponse != null ? httpResponse.getStatusCode() : null)
                .responseBody(httpResponse != null ? httpResponse.getBody() : null)
                .responseTimeMillis(responseTime(startTime))
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
                                                      GeneratedBidIds biddersToCacheBidIds,
                                                      Account account,
                                                      EventsContext eventsContext) {

        final com.iab.openrtb.response.Bid bid = cacheBid.getBid();
        final ObjectNode bidObjectNode = mapper.mapper().valueToTree(bid);

        final String eventUrl = generateWinUrl(biddersToCacheBidIds, bid, account, eventsContext);
        if (eventUrl != null) {
            bidObjectNode.put(BID_WURL_ATTRIBUTE, eventUrl);
        }

        final PutObject payload = PutObject.builder()
                .type("json")
                .value(bidObjectNode)
                .expiry(cacheBid.getTtl())
                .build();

        return CachedCreative.of(payload, creativeSizeFromAdm(bid));
    }

    /**
     * Makes XML type {@link PutObject} from {@link com.iab.openrtb.response.Bid}. Used for OpenRTB auction request.
     */
    private CachedCreative createXmlPutObjectOpenrtb(CacheBid cacheBid,
                                                     GeneratedBidIds bidderToVideoBidIdsToModify,
                                                     String hbCacheId,
                                                     Map<String, Map<String, String>> biddersToBidsCategories,
                                                     Account account,
                                                     EventsContext eventsContext) {

        final com.iab.openrtb.response.Bid bid = cacheBid.getBid();
        final String vastXml = resolveVastXmlFrom(bid);
        final String bidId = bid.getId();
        final String bidder = bidderToVideoBidIdsToModify.getBidderForBid(bid.getId(), bid.getImpid()).orElse(null);
        final String customCacheKey = resolveCustomCacheKey(hbCacheId, bidder, bidId, biddersToBidsCategories);
        final String eventUrl = generateVastUrlTracking(bidderToVideoBidIdsToModify, bid, account, eventsContext);
        final String effectiveVastXml = eventUrl != null ? appendTrackingUrlToVastXml(vastXml, eventUrl) : vastXml;

        final PutObject payload = PutObject.builder()
                .type("xml")
                .value(new TextNode(effectiveVastXml))
                .expiry(cacheBid.getTtl())
                .key(customCacheKey)
                .build();

        return CachedCreative.of(payload, creativeSizeFromTextNode(payload.getValue()));
    }

    private static String resolveCustomCacheKey(String hbCacheId, String bidder, String bidId,
                                                Map<String, Map<String, String>> biddersToBidsCategories) {
        if (hbCacheId == null || bidder == null) {
            return null;
        }

        final Map<String, String> bidToCategories = biddersToBidsCategories.get(bidder);
        final String categoryDuration = bidToCategories != null ? bidToCategories.get(bidId) : null;

        return StringUtils.isNotEmpty(categoryDuration) ? String.format("%s_%s", categoryDuration, hbCacheId) : null;
    }

    private static String resolveVastXmlFrom(com.iab.openrtb.response.Bid bid) {
        if (bid.getAdm() == null) {
            return "<VAST version=\"3.0\"><Ad><Wrapper>"
                    + "<AdSystem>prebid.org wrapper</AdSystem>"
                    + "<VASTAdTagURI><![CDATA[" + bid.getNurl() + "]]></VASTAdTagURI>"
                    + "<Impression></Impression><Creatives></Creatives>"
                    + "</Wrapper></Ad></VAST>";
        }

        return bid.getAdm();
    }

    private String generateWinUrl(GeneratedBidIds biddersToCacheBidIds,
                                  com.iab.openrtb.response.Bid bid,
                                  Account account,
                                  EventsContext eventsContext) {

        if (eventsContext.isEnabledForAccount() && eventsContext.isEnabledForRequest()) {
            final String bidId = bid.getId();
            final String impId = bid.getImpid();
            return biddersToCacheBidIds.getBidderForBid(bidId, impId)
                    .map(bidder -> eventsService.winUrl(
                            biddersToCacheBidIds.getGeneratedId(bidder, bidId, impId),
                            bidder,
                            account.getId(),
                            eventsContext.getAuctionTimestamp(),
                            eventsContext.getIntegration()))
                    .orElse(null);
        }

        return null;
    }

    private String generateVastUrlTracking(GeneratedBidIds bidderToVideoBidIdsToModify,
                                           com.iab.openrtb.response.Bid bid,
                                           Account account,
                                           EventsContext eventsContext) {

        if (eventsContext.isEnabledForAccount()) {
            final String bidId = bid.getId();
            final String impId = bid.getImpid();
            return bidderToVideoBidIdsToModify.getBidderForBid(bidId, impId)
                    .map(bidder -> eventsService.vastUrlTracking(
                            bidderToVideoBidIdsToModify.getGeneratedId(bidder, bidId, impId),
                            bidder,
                            account.getId(),
                            eventsContext.getAuctionTimestamp(),
                            eventsContext.getIntegration()))
                    .orElse(null);
        }

        return null;
    }

    private String appendTrackingUrlToVastXml(String vastXml, String vastUrlTracking) {
        final String closeTag = "</Impression>";
        final int closeTagIndex = vastXml.indexOf(closeTag);

        // no impression tag - pass it as it is
        if (closeTagIndex == -1) {
            return vastXml;
        }

        final String impressionUrl = "<![CDATA[" + vastUrlTracking + "]]>";
        final String openTag = "<Impression>";

        // empty impression tag - just insert the link
        if (closeTagIndex - vastXml.indexOf(openTag) == openTag.length()) {
            return vastXml.replaceFirst(openTag, openTag + impressionUrl);
        }

        return vastXml.replaceFirst(closeTag, closeTag + openTag + impressionUrl + closeTag);
    }

    /**
     * Handles http response, analyzes response status and creates {@link BidCacheResponse} from response body
     * or throws {@link PreBidException} in case of errors.
     */
    private BidCacheResponse toBidCacheResponse(
            int statusCode, String responseBody, int bidCount, String accountId, long startTime) {

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
                                                                            List<String> uuids, String hbCacheId) {
        final Map<com.iab.openrtb.response.Bid, CacheInfo> result = new HashMap<>(uuids.size());

        // here we assume "videoBids" is a sublist of "bids"
        // so, no need for a separate loop on "videoBids" if "bids" is not empty
        if (!cacheBids.isEmpty()) {
            final List<com.iab.openrtb.response.Bid> videoBids = cacheVideoBids.stream()
                    .map(CacheBid::getBid)
                    .collect(Collectors.toList());

            final int bidsSize = cacheBids.size();
            for (int i = 0; i < bidsSize; i++) {
                final CacheBid cacheBid = cacheBids.get(i);
                final com.iab.openrtb.response.Bid bid = cacheBid.getBid();
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
                result.put(cacheBid.getBid(), CacheInfo.of(null, resolveVideoBidUuid(uuids.get(i), hbCacheId), null,
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
    public static String getCachedAssetUrlTemplate(String cacheSchema, String cacheHost, String path,
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
            metrics.updateCacheCreativeSize(accountId, cachedCreative.getSize());
        }
    }

    private static int creativeSizeFromAdm(com.iab.openrtb.response.Bid bid) {
        return lengthOrZero(bid.getAdm());
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
