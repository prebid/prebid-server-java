package org.prebid.server.cache;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Imp;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cache.model.CacheBid;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpCall;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.cache.proto.request.BannerValue;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.settings.ApplicationSettings;
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

    private final ApplicationSettings applicationSettings;
    private final CacheTtl mediaTypeCacheTtl;
    private final HttpClient httpClient;
    private final URL endpointUrl;
    private final String cachedAssetUrlTemplate;
    private final Clock clock;

    public CacheService(ApplicationSettings applicationSettings, CacheTtl mediaTypeCacheTtl, HttpClient httpClient,
                        URL endpointUrl, String cachedAssetUrlTemplate, Clock clock) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.mediaTypeCacheTtl = Objects.requireNonNull(mediaTypeCacheTtl);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.cachedAssetUrlTemplate = Objects.requireNonNull(cachedAssetUrlTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    public String getEndpointHost() {
        final String host = endpointUrl.getHost();
        final int port = endpointUrl.getPort();
        return port != -1 ? String.format("%s:%d", host, port) : host;
    }

    public String getEndpointPath() {
        return endpointUrl.getPath();
    }

    /**
     * Makes cache for {@link Bid}s (legacy).
     * <p>
     * The returned result will always have the same number of elements as the values argument.
     */
    public Future<List<BidCacheResult>> cacheBids(List<Bid> bids, Timeout timeout) {
        return doCache(bids, timeout, CacheService::createPutObject, this::createBidCacheResult);
    }

    /**
     * Makes cache for {@link Bid}s with video media type only (legacy).
     * <p>
     * The returned result will always have the same number of elements as the values argument.
     */
    public Future<List<BidCacheResult>> cacheBidsVideoOnly(List<Bid> bids, Timeout timeout) {
        return doCache(bids, timeout, CacheService::createPutObjectVideoOnly, this::createBidCacheResult);
    }

    /**
     * Generic method to work with cache service (legacy).
     */
    private <T, R> Future<List<R>> doCache(List<T> bids, Timeout timeout,
                                           Function<T, PutObject> requestItemCreator,
                                           Function<CacheObject, R> responseItemCreator) {
        return makeRequest(toRequest(bids, requestItemCreator), bids.size(), timeout)
                .map(bidCacheResponse -> toResponse(bidCacheResponse, responseItemCreator));
    }

    /**
     * Asks external prebid cache service to store the given value.
     */
    private Future<BidCacheResponse> makeRequest(BidCacheRequest bidCacheRequest, int bidCount, Timeout timeout) {
        if (bidCount == 0) {
            return Future.succeededFuture(BidCacheResponse.of(Collections.emptyList()));
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(new TimeoutException("Timeout has been exceeded"));
        }

        return httpClient.post(endpointUrl.toString(), HttpUtil.headers(), Json.encode(bidCacheRequest),
                remainingTimeout)
                .map(response -> toBidCacheResponse(response.getStatusCode(), response.getBody(), bidCount))
                .recover(CacheService::failResponse);
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<BidCacheResponse> failResponse(Throwable exception) {
        logger.warn("Error occurred while interacting with cache service", exception);
        return Future.failedFuture(exception);
    }

    /**
     * Makes cache for OpenRTB {@link com.iab.openrtb.response.Bid}s.
     */
    public Future<CacheServiceResult> cacheBidsOpenrtb(
            List<com.iab.openrtb.response.Bid> bids, List<Imp> imps, CacheContext cacheContext, String publisherId,
            Timeout timeout) {
        final Future<CacheServiceResult> result;

        if (CollectionUtils.isEmpty(bids)) {
            result = Future.succeededFuture(CacheServiceResult.empty());
        } else {
            final Map<String, Integer> impIdToTtl = new HashMap<>(imps.size());
            boolean impWithNoExpExists = false; // indicates at least one impression without expire presents
            final List<String> videoImpIds = new ArrayList<>();
            for (Imp imp : imps) {
                impIdToTtl.put(imp.getId(), imp.getExp());
                impWithNoExpExists |= imp.getExp() == null;
                if (cacheContext.isShouldCacheVideoBids() && imp.getId() != null && imp.getVideo() != null) {
                    videoImpIds.add(imp.getId());
                }
            }

            result = CompositeFuture.all(
                    getCacheBids(cacheContext.isShouldCacheBids(), bids, impIdToTtl, impWithNoExpExists,
                            cacheContext.getCacheBidsTtl(), publisherId, timeout),
                    getVideoCacheBids(cacheContext.isShouldCacheVideoBids(), bids, impIdToTtl, videoImpIds,
                            impWithNoExpExists, cacheContext.getCacheVideoBidsTtl(), publisherId, timeout))
                    .compose(composite -> doCacheOpenrtb(composite.<List<CacheBid>>list().get(0),
                            composite.<List<CacheBid>>list().get(1), timeout));
        }

        return result;
    }

    /**
     * Creates list of {@link CacheBid}s from the list of {@link com.iab.openrtb.response.Bid}s.
     */
    private Future<List<CacheBid>> getCacheBids(
            boolean shouldCacheBids, List<com.iab.openrtb.response.Bid> bids, Map<String, Integer> impIdToTtl,
            boolean impWithNoExpExists, Integer cacheBidsTtl, String publisherId, Timeout timeout) {

        return shouldCacheBids
                ? accountCacheTtlFrom(impWithNoExpExists, publisherId, timeout)
                .map(accountCacheTtl -> bids.stream()
                        .map(bid -> toCacheBid(bid, impIdToTtl, cacheBidsTtl, accountCacheTtl, false))
                        .collect(Collectors.toList()))
                : Future.succeededFuture(Collections.emptyList());
    }

    /**
     * Creates list of video {@link CacheBid}s from the list of {@link com.iab.openrtb.response.Bid}s.
     */
    private Future<List<CacheBid>> getVideoCacheBids(
            boolean shouldCacheVideoBids, List<com.iab.openrtb.response.Bid> bids, Map<String, Integer> impIdToTtl,
            List<String> videoImpIds, boolean impWithNoExpExists, Integer cacheVideoBidsTtl, String publisherId,
            Timeout timeout) {

        return shouldCacheVideoBids
                ? accountCacheTtlFrom(impWithNoExpExists, publisherId, timeout)
                .map(accountCacheTtl -> bids.stream()
                        .filter(bid -> videoImpIds.contains(bid.getImpid())) // bid is video
                        .map(bid -> toCacheBid(bid, impIdToTtl, cacheVideoBidsTtl, accountCacheTtl, true))
                        .collect(Collectors.toList()))
                : Future.succeededFuture(Collections.emptyList());
    }

    /**
     * Fetches {@link CacheTtl} for the given account.
     * <p>
     * This data is not critical, so returns empty {@link CacheTtl} if any error occurred.
     */
    private Future<CacheTtl> accountCacheTtlFrom(boolean impWithNoExpExists, String publisherId, Timeout timeout) {
        return impWithNoExpExists && StringUtils.isNotEmpty(publisherId)
                ? makeCacheTtl(publisherId, timeout)
                : Future.succeededFuture(CacheTtl.empty());
    }

    /**
     * Makes {@link CacheTtl} from {@link Account} fetched by {@link ApplicationSettings}.
     */
    private Future<CacheTtl> makeCacheTtl(String publisherId, Timeout timeout) {
        return applicationSettings.getAccountById(publisherId, timeout)
                .map(CacheService::cacheTtlFrom)
                .otherwise(CacheService::accountFallback);
    }

    /**
     * Verifies if configs for {@link CacheTtl} are present in {@link Account}.
     */
    private static CacheTtl cacheTtlFrom(Account account) {
        return account.getBannerCacheTtl() != null || account.getVideoCacheTtl() != null
                ? CacheTtl.of(account.getBannerCacheTtl(), account.getVideoCacheTtl())
                : CacheTtl.empty();
    }

    /**
     * Returns empty {@link CacheTtl} if account is not found or any exception occurred.
     */
    private static CacheTtl accountFallback(Throwable exception) {
        if (!(exception instanceof PreBidException)) {
            logger.warn("Error occurred while fetching account", exception);
        }
        return CacheTtl.empty();
    }

    /**
     * Creates {@link CacheBid} from given {@link com.iab.openrtb.response.Bid} and determined cache ttl.
     */
    private CacheBid toCacheBid(com.iab.openrtb.response.Bid bid, Map<String, Integer> impIdToTtl, Integer requestTtl,
                                CacheTtl accountCacheTtl, boolean isVideoBid) {
        final Integer bidTtl = bid.getExp();
        final Integer impTtl = impIdToTtl.get(bid.getImpid());
        final Integer accountMediaTypeTtl = isVideoBid
                ? accountCacheTtl.getVideoCacheTtl() : accountCacheTtl.getBannerCacheTtl();
        final Integer mediaTypeTtl = isVideoBid
                ? mediaTypeCacheTtl.getVideoCacheTtl() : mediaTypeCacheTtl.getBannerCacheTtl();
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
    private Future<CacheServiceResult> doCacheOpenrtb(List<CacheBid> bids, List<CacheBid> videoBids, Timeout timeout) {
        final List<PutObject> putObjects = Stream.concat(
                bids.stream().map(CacheService::createJsonPutObjectOpenrtb),
                videoBids.stream().map(CacheService::createXmlPutObjectOpenrtb))
                .collect(Collectors.toList());

        if (putObjects.isEmpty()) {
            return Future.succeededFuture(CacheServiceResult.empty());
        }

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.succeededFuture(CacheServiceResult.of(null, new TimeoutException("Timeout has been exceeded"),
                    Collections.emptyMap()));
        }

        final String url = endpointUrl.toString();
        final String body = Json.encode(BidCacheRequest.of(putObjects));
        final CacheHttpRequest httpRequest = CacheHttpRequest.of(url, body);

        final long startTime = clock.millis();
        return httpClient.post(url, HttpUtil.headers(), body, remainingTimeout)
                .map(response -> processResponseOpenrtb(response, httpRequest, putObjects.size(), bids, videoBids,
                        startTime))
                .otherwise(exception -> failResponseOpenrtb(exception, httpRequest, startTime));
    }

    /**
     * Creates {@link CacheServiceResult} from the given {@link HttpClientResponse}.
     */
    private CacheServiceResult processResponseOpenrtb(HttpClientResponse response, CacheHttpRequest httpRequest,
                                                      int bidCount, List<CacheBid> bids, List<CacheBid> videoBids,
                                                      long startTime) {
        final CacheHttpResponse httpResponse = CacheHttpResponse.of(response.getStatusCode(), response.getBody());
        final CacheHttpCall httpCall = CacheHttpCall.of(httpRequest, httpResponse, responseTime(startTime));

        final BidCacheResponse bidCacheResponse;
        try {
            bidCacheResponse = toBidCacheResponse(response.getStatusCode(), response.getBody(), bidCount);
        } catch (PreBidException e) {
            return CacheServiceResult.of(httpCall, e, Collections.emptyMap());
        }

        final List<String> uuids = toResponse(bidCacheResponse, CacheObject::getUuid);
        return CacheServiceResult.of(httpCall, null, toResultMap(bids, videoBids, uuids));
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private CacheServiceResult failResponseOpenrtb(Throwable exception, CacheHttpRequest request, long startTime) {
        logger.warn("Error occurred while interacting with cache service", exception);

        final CacheHttpCall httpCall = CacheHttpCall.of(request, null, responseTime(startTime));
        return CacheServiceResult.of(httpCall, exception, Collections.emptyMap());
    }

    /**
     * Calculates execution time since the given start time.
     */
    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }

    /**
     * Makes put object from {@link Bid}. Used for legacy auction request.
     */
    private static PutObject createPutObject(Bid bid) {
        return MediaType.video.equals(bid.getMediaType()) ? videoPutObject(bid) : bannerPutObject(bid);
    }

    /**
     * Makes put object from {@link Bid} with video media type only. Used for legacy auction request.
     */
    private static PutObject createPutObjectVideoOnly(Bid bid) {
        return MediaType.video.equals(bid.getMediaType()) ? videoPutObject(bid) : null;
    }

    /**
     * Makes JSON type {@link PutObject} from {@link com.iab.openrtb.response.Bid}. Used for OpenRTB auction request.
     */
    private static PutObject createJsonPutObjectOpenrtb(CacheBid cacheBid) {
        return PutObject.of("json", Json.mapper.valueToTree(cacheBid.getBid()), cacheBid.getTtl());
    }

    /**
     * Makes XML type {@link PutObject} from {@link com.iab.openrtb.response.Bid}. Used for OpenRTB auction request.
     */
    private static PutObject createXmlPutObjectOpenrtb(CacheBid cacheBid) {
        if (cacheBid.getBid().getAdm() == null) {
            return PutObject.of("xml", new TextNode("<VAST version=\"3.0\"><Ad><Wrapper>"
                    + "<AdSystem>prebid.org wrapper</AdSystem>"
                    + "<VASTAdTagURI><![CDATA[" + cacheBid.getBid().getNurl() + "]]></VASTAdTagURI>"
                    + "<Impression></Impression><Creatives></Creatives>"
                    + "</Wrapper></Ad></VAST>"), cacheBid.getTtl());
        } else {
            return PutObject.of("xml", new TextNode(cacheBid.getBid().getAdm()), cacheBid.getTtl());
        }
    }

    /**
     * Creates bid cache request for the given bids.
     */
    private static <T> BidCacheRequest toRequest(List<T> bids, Function<T, PutObject> requestItemCreator) {
        return BidCacheRequest.of(bids.stream()
                .filter(Objects::nonNull)
                .map(requestItemCreator)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    /**
     * Transforms {@link CacheObject} into {@link BidCacheResult}. Used for legacy auction request.
     */
    private BidCacheResult createBidCacheResult(CacheObject cacheObject) {
        final String uuid = cacheObject.getUuid();
        return BidCacheResult.of(uuid, getCachedAssetURL(uuid));
    }

    /**
     * Handles http response, analyzes response status and creates {@link BidCacheResponse} from response body
     * or throws {@link PreBidException} in case of errors.
     */
    private static BidCacheResponse toBidCacheResponse(int statusCode, String responseBody, int bidCount) {
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final BidCacheResponse bidCacheResponse;
        try {
            bidCacheResponse = Json.decodeValue(responseBody, BidCacheResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", responseBody), e);
        }

        final List<CacheObject> responses = bidCacheResponse.getResponses();
        if (responses == null || responses.size() != bidCount) {
            throw new PreBidException("The number of response cache objects doesn't match with bids");
        }

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
     * Creates a map with bids as a key and {@link CacheIdInfo} as a value from obtained UUIDs.
     */
    private static Map<com.iab.openrtb.response.Bid, CacheIdInfo> toResultMap(
            List<CacheBid> cacheBids, List<CacheBid> cacheVideoBids, List<String> uuids) {
        final Map<com.iab.openrtb.response.Bid, CacheIdInfo> result = new HashMap<>(uuids.size());

        final List<com.iab.openrtb.response.Bid> bids = cacheBids.stream()
                .map(CacheBid::getBid).collect(Collectors.toList());
        final List<com.iab.openrtb.response.Bid> videoBids = cacheVideoBids.stream()
                .map(CacheBid::getBid).collect(Collectors.toList());

        // here we assume "videoBids" is a sublist of "bids"
        // so, no need for a separate loop on "videoBids" if "bids" is not empty
        if (!bids.isEmpty()) {
            for (int i = 0; i < bids.size(); i++) {
                final com.iab.openrtb.response.Bid bid = bids.get(i);

                // determine uuid for video bid
                final int indexOfVideoBid = videoBids.indexOf(bid);
                final String videoBidUuid = indexOfVideoBid != -1 ? uuids.get(bids.size() + indexOfVideoBid) : null;

                result.put(bid, CacheIdInfo.of(uuids.get(i), videoBidUuid));
            }
        } else {
            for (int i = 0; i < videoBids.size(); i++) {
                result.put(videoBids.get(i), CacheIdInfo.of(null, uuids.get(i)));
            }
        }

        return result;
    }

    /**
     * Composes cached asset URL for the given UUID cache value.
     */
    public String getCachedAssetURL(String uuid) {
        return cachedAssetUrlTemplate.replaceFirst("%PBS_CACHE_UUID%", uuid);
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

    /**
     * Creates video {@link PutObject} from the given {@link Bid}. Used for legacy auction request.
     */
    private static PutObject videoPutObject(Bid bid) {
        return PutObject.of("xml", new TextNode(bid.getAdm()), null);
    }

    /**
     * Creates banner {@link PutObject} from the given {@link Bid}. Used for legacy auction request.
     */
    private static PutObject bannerPutObject(Bid bid) {
        return PutObject.of("json",
                Json.mapper.valueToTree(BannerValue.of(bid.getAdm(), bid.getNurl(), bid.getWidth(), bid.getHeight())),
                null);
    }
}
