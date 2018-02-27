package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.openrtb.ext.ExtPrebid;
import org.prebid.server.model.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.model.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.model.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.model.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.model.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.model.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.model.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.model.openrtb.ext.response.ExtResponseDebug;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Executes an OpenRTB v2.5 Auction.
 */
public class ExchangeService {

    private static final String PREBID_EXT = "prebid";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private static final Clock CLOCK = Clock.systemDefaultZone();
    private final BidderRequesterCatalog bidderRequesterCatalog;
    private final CacheService cacheService;
    private final Metrics metrics;
    private long expectedCacheTime;

    public ExchangeService(BidderRequesterCatalog bidderRequesterCatalog, CacheService cacheService, Metrics metrics,
                           long expectedCacheTime) {
        this.bidderRequesterCatalog = Objects.requireNonNull(bidderRequesterCatalog);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.metrics = Objects.requireNonNull(metrics);
        if (expectedCacheTime < 0) {
            throw new IllegalArgumentException("Expected cache time could not be negative");
        }
        this.expectedCacheTime = expectedCacheTime;
    }

    /**
     * Runs an auction: delegates request to applicable bidders, gathers responses from them and constructs final
     * response containing returned bids and additional information in extensions.
     */
    public Future<BidResponse> holdAuction(BidRequest bidRequest, UidsCookie uidsCookie, GlobalTimeout globalTimeout) {
        final List<BidderRequest> bidderRequests = extractBidderRequests(bidRequest, uidsCookie);

        updateRequestMetric(bidderRequests, uidsCookie);

        // extract ext from bid request
        final ExtBidRequest requestExt;
        try {
            requestExt = requestExt(bidRequest);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final ExtRequestTargeting targeting = targeting(requestExt);

        // build targeting keywords creator
        final TargetingKeywordsCreator keywordsCreator = buildKeywordsCreator(targeting);

        final boolean shouldCacheBids = shouldCacheBids(targeting, requestExt);

        // Randomize the list to make the auction more fair
        Collections.shuffle(bidderRequests);

        final long startTime = CLOCK.millis();

        // send all the requests to the bidders and gathers results
        final CompositeFuture bidderResults = CompositeFuture.join(bidderRequests.stream()
                .map(bidderRequest -> requestBids(bidderRequest, startTime,
                        auctionTimeout(globalTimeout, shouldCacheBids)))
                .collect(Collectors.toList()));

        // produce response from bidder results
        return bidderResults.compose(result -> {

            final List<BidderResponse> bidderResponses = result.list();
            updateMetricsFromResponses(bidderResponses);
            return toBidResponse(
                    bidderResponses, bidRequest, keywordsCreator, shouldCacheBids, globalTimeout);
        });
    }

    /**
     * Updates 'request' and 'no_cookie_requests' metrics for each {@link BidderRequest}
     */
    private void updateRequestMetric(List<BidderRequest> bidderRequests, UidsCookie uidsCookie) {
        bidderRequests.forEach(bidderRequest -> {
            final String bidder = bidderRequest.getBidder();

            metrics.forAdapter(bidder).incCounter(MetricName.requests);

            final boolean noBuyerId = StringUtils.isBlank(uidsCookie.uidFrom(
                    bidderRequesterCatalog.byName(bidder).cookieFamilyName()));

            if (bidderRequest.getBidRequest().getApp() == null && noBuyerId) {
                metrics.forAdapter(bidder).incCounter(MetricName.no_cookie_requests);
            }
        });
    }

    /**
     * Updates 'request_time', 'responseTime', 'timeout_request', 'error_requests', 'no_bid_requests',
     * 'prices' metrics for each {@link BidderResponse}
     */
    private void updateMetricsFromResponses(List<BidderResponse> bidderResponses) {
        bidderResponses.forEach(bidderResponse -> {
            final AdapterMetrics adapterMetrics = metrics.forAdapter(bidderResponse.getBidder());

            adapterMetrics.updateTimer(MetricName.request_time, bidderResponse.getResponseTime());

            final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
            List<Bid> bids = null;
            if (CollectionUtils.isNotEmpty(bidderBids)) {
                bids = bidderBids.stream()
                        .map(BidderBid::getBid).collect(Collectors.toList());
            }

            if (CollectionUtils.isEmpty(bids)) {
                adapterMetrics.incCounter(MetricName.no_bid_requests);
            } else {
                bids.forEach(bid -> {
                    final long cpmPrice = bid.getPrice() != null
                            ? bid.getPrice().multiply(THOUSAND).longValue()
                            : 0L;
                    adapterMetrics.updateHistogram(MetricName.prices, cpmPrice);
                });
            }
            if (CollectionUtils.isNotEmpty(bidderResponse.getSeatBid().getErrors())) {
                bidderResponse.getSeatBid().getErrors().forEach(error -> adapterMetrics.incCounter(error.isTimedOut()
                        ? MetricName.timeout_requests
                            : MetricName.error_requests));
            }
        });
    }

    /**
     * Takes an OpenRTB request and returns the OpenRTB requests sanitized for each bidder.
     * <p>
     * This will copy the {@link BidRequest} into a list of requests, where the {@link BidRequest}.imp[].ext field
     * will only consist of the "prebid" field and the field for the appropriate bidder parameters. We will drop all
     * extended fields beyond this context, so this will not be compatible with any other uses of the extension area
     * i.e. the bidders will not see any other extension fields.
     * <p>
     * Each of the created {@link BidRequest}s will have bidrequest.user.buyerid field populated with the value from
     * {@link UidsCookie} corresponding to bidder's family name unless buyerid is already in the original OpenRTB
     * request (in this case it will not be overridden).
     * <p>
     * NOTE: the return list will only contain entries for bidders that both have the extension field in at least one
     * {@link Imp}, and are known to {@link BidderCatalog}.
     */
    private List<BidderRequest> extractBidderRequests(BidRequest bidRequest, UidsCookie uidsCookie) {
        // sanity check: discard imps without extension
        final List<Imp> imps = bidRequest.getImp().stream()
                .filter(imp -> imp.getExt() != null)
                .collect(Collectors.toList());

        // identify valid bidders out of imps
        final List<String> bidders = imps.stream()
                .flatMap(imp -> asStream(imp.getExt().fieldNames())
                        .filter(bidder -> !Objects.equals(bidder, PREBID_EXT))
                        .filter(bidderRequesterCatalog::isValidName))
                .distinct()
                .collect(Collectors.toList());

        return bidders.stream()
                // for each bidder create a new request that is a copy of original request except buyerid and imp
                // extensions
                .map(bidder -> BidderRequest.of(bidder, bidRequest.toBuilder()
                        .user(userWithBuyerid(bidder, bidRequest, uidsCookie))
                        .imp(imps.stream()
                                .filter(imp -> imp.getExt().hasNonNull(bidder))
                                // for each imp create a new imp with extension crafted to contain only
                                // bidder-specific data
                                .map(imp -> imp.toBuilder()
                                        .ext(Json.mapper.valueToTree(extractBidderExt(bidder, imp.getExt())))
                                        .build())
                                .collect(Collectors.toList()))
                        .build()))
                .collect(Collectors.toList());
    }

    /**
     * Returns either original {@link User} (if there is no user id for the bidder in uids cookie or buyerid is
     * already there) or new {@link User} with buyerid filled with user id from uids cookie.
     */
    private User userWithBuyerid(String bidder, BidRequest bidRequest, UidsCookie uidsCookie) {
        final User result;

        final String buyerid = uidsCookie.uidFrom(bidderRequesterCatalog.byName(bidder).cookieFamilyName());

        final User user = bidRequest.getUser();
        if (StringUtils.isNotBlank(buyerid) && (user == null || StringUtils.isBlank(user.getBuyeruid()))) {
            final User.UserBuilder builder = user == null ? User.builder() : user.toBuilder();
            builder.buyeruid(buyerid);
            result = builder.build();
        } else {
            result = user;
        }

        return result;
    }

    /**
     * Extracts {@link ExtBidRequest} from bid request.
     */
    private static ExtBidRequest requestExt(BidRequest bidRequest) {
        try {
            return bidRequest.getExt() != null
                    ? Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()), e);
        }
    }

    /**
     * Extracts {@link ExtRequestTargeting} from {@link ExtBidRequest} model.
     */
    private static ExtRequestTargeting targeting(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        return prebid != null ? prebid.getTargeting() : null;
    }

    /**
     * Extracts targeting keywords settings from the bid request and creates {@link TargetingKeywordsCreator}
     * instance if they are present.
     * <p>
     * Returns null if bidrequest.ext.prebid.targeting is missing - it means that no targeting keywords
     * should be included in bid response.
     */
    private static TargetingKeywordsCreator buildKeywordsCreator(ExtRequestTargeting targeting) {
        return targeting != null
                ? TargetingKeywordsCreator.withPriceGranularity(targeting.getPricegranularity()) : null;
    }

    /**
     * Determines is prebid cache needed.
     */
    private static boolean shouldCacheBids(ExtRequestTargeting targeting, ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidCache cache = prebid != null ? prebid.getCache() : null;
        return targeting != null && cache != null && !cache.getBids().isNull();
    }

    /**
     * Creates a new imp extension for particular bidder having:
     * <ul>
     * <li>"bidder" field populated with an imp.ext.{bidder} field value, not null</li>
     * <li>"prebid" field populated with an imp.ext.prebid field value, may be null</li>
     * </ul>
     */
    private static ExtPrebid<JsonNode, JsonNode> extractBidderExt(String bidder, ObjectNode impExt) {
        return ExtPrebid.of(impExt.get(PREBID_EXT), impExt.get(bidder));
    }

    /**
     * If we need to cache bids, then it will take some time to call prebid cache.
     * We should reduce the amount of time the bidders have, to compensate.
     */
    private GlobalTimeout auctionTimeout(GlobalTimeout timeout, boolean shouldCacheBids) {
        // A static timeout here is not ideal. This is a hack because we have some aggressive timelines for OpenRTB
        // support.
        // In reality, the cache response time will probably fluctuate with the traffic over time. Someday, this
        // should be replaced by code which tracks the response time of recent cache calls and adjusts the time
        // dynamically.
        return shouldCacheBids ? timeout.minus(expectedCacheTime) : timeout;
    }

    /**
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest, long startTime, GlobalTimeout timeout) {
        final String bidder = bidderRequest.getBidder();
        return bidderRequesterCatalog.byName(bidder).requestBids(bidderRequest.getBidRequest(), timeout)
                .map(result -> BidderResponse.of(bidder, result, responseTime(startTime)));
    }

    /**
     * Takes all the bids supplied by the bidder and crafts an OpenRTB {@link BidResponse} to send back to the
     * requester.
     */
    private Future<BidResponse> toBidResponse(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                              TargetingKeywordsCreator keywordsCreator, boolean shouldCacheBids,
                                              GlobalTimeout timeout) {
        final List<Bid> winningBids = determineWinningBids(keywordsCreator, bidderResponses);

        return toWinningBidsWithCacheIds(shouldCacheBids, winningBids, keywordsCreator, timeout)
                .map(winningBidsWithCacheIds ->
                        toBidResponseWithCacheInfo(bidderResponses, bidRequest, keywordsCreator,
                                winningBidsWithCacheIds));
    }

    /**
     * Determines wining bids for each impId (ad unit code). Winning bid is the one with the highest price.
     */
    private static List<Bid> determineWinningBids(TargetingKeywordsCreator keywordsCreator,
                                                  List<BidderResponse> bidderResponses) {
        // determine winning bids only if targeting keywords are requested
        return keywordsCreator == null ? Collections.emptyList() : bidderResponses.stream()
                .flatMap(bidderResponse -> bidderResponse.getSeatBid().getBids().stream()
                        .map(BidderBid::getBid))
                // group bids by impId (ad unit code)
                .collect(Collectors.groupingBy(Bid::getImpid))
                .values().stream()
                // sort each bid list by price and pick a winning bid for each impId
                .peek(bids -> bids.sort(Comparator.comparing(Bid::getPrice).reversed()))
                .map(bids -> bids.get(0)) // each list is guaranteed to be non-empty
                .collect(Collectors.toList());
    }

    /**
     * Corresponds cacheId (or null if not present) to each {@link Bid}.
     */
    private Future<Map<Bid, String>> toWinningBidsWithCacheIds(boolean shouldCacheBids, List<Bid> winningBids,
                                                               TargetingKeywordsCreator keywordsCreator,
                                                               GlobalTimeout timeout) {
        final Future<Map<Bid, String>> result;

        if (!shouldCacheBids) {
            result = Future.succeededFuture(toMapBidsWithEmptyCacheIds(winningBids));
        } else {
            // do not submit bids with zero CPM to prebid cache
            final List<Bid> winningBidsWithNonZeroCpm = winningBids.stream()
                    .filter(bid -> keywordsCreator.isNonZeroCpm(bid.getPrice()))
                    .collect(Collectors.toList());

            result = cacheService.cacheBidsOpenrtb(winningBidsWithNonZeroCpm, timeout)
                    .recover(throwable -> Future.succeededFuture(Collections.emptyList())) // just skip cache errors
                    .map(cacheIds -> mergeCacheResults(winningBidsWithNonZeroCpm, cacheIds, winningBids));
        }

        return result;
    }

    /**
     * Creates a map with {@link Bid} as a key and null as a value.
     */
    private static Map<Bid, String> toMapBidsWithEmptyCacheIds(List<Bid> bids) {
        final Map<Bid, String> result = new HashMap<>(bids.size());
        bids.forEach(bid -> result.put(bid, null));
        return result;
    }

    /**
     * Creates a map with {@link Bid} as a key and cacheId as a value.
     * <p>
     * Adds entries with null as a value for bids which were not submitted to {@link CacheService}.
     */
    private static Map<Bid, String> mergeCacheResults(List<Bid> winningBidsWithNonZeroCpm, List<String> cacheIds,
                                                      List<Bid> winningBids) {
        if (CollectionUtils.isEmpty(cacheIds) || cacheIds.size() != winningBidsWithNonZeroCpm.size()) {
            return toMapBidsWithEmptyCacheIds(winningBids);
        }

        final Map<Bid, String> result = new HashMap<>(winningBids.size());
        for (int i = 0; i < cacheIds.size(); i++) {
            result.put(winningBidsWithNonZeroCpm.get(i), cacheIds.get(i));
        }
        // add bids without cache ids
        if (winningBids.size() > cacheIds.size()) {
            for (Bid bid : winningBids) {
                if (!result.containsKey(bid)) {
                    result.put(bid, null);
                }
            }
        }
        return result;
    }

    /**
     * Creates an OpenRTB {@link BidResponse} from the bids supplied by the bidder,
     * including processing of winning bids with cache IDs.
     */
    private static BidResponse toBidResponseWithCacheInfo(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                                          TargetingKeywordsCreator keywordsCreator,
                                                          Map<Bid, String> winningBidsWithCacheIds) {
        final List<SeatBid> seatBids = bidderResponses.stream()
                .filter(bidderResponse -> !bidderResponse.getSeatBid().getBids().isEmpty())
                .map(bidderResponse -> toSeatBid(bidderResponse, keywordsCreator, winningBidsWithCacheIds))
                .collect(Collectors.toList());

        final ExtBidResponse bidResponseExt = toExtBidResponse(bidderResponses, bidRequest, keywordsCreator);

        return BidResponse.builder()
                .id(bidRequest.getId())
                // signal "Invalid Request" if no valid bidders.
                .nbr(bidderResponses.isEmpty() ? 2 : null)
                .seatbid(seatBids)
                .ext(Json.mapper.valueToTree(bidResponseExt))
                .build();
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private static SeatBid toSeatBid(BidderResponse bidderResponse, TargetingKeywordsCreator keywordsCreator,
                                     Map<Bid, String> winningBidsWithCacheIds) {
        final String bidder = bidderResponse.getBidder();
        final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();

        final SeatBid.SeatBidBuilder seatBidBuilder = SeatBid.builder()
                .seat(bidder)
                // prebid cannot support roadblocking
                .group(0)
                .bid(bidderSeatBid.getBids().stream()
                        .map(bidderBid ->
                                toBid(bidderBid, bidder, keywordsCreator, winningBidsWithCacheIds))
                        .collect(Collectors.toList()));

        final ObjectNode seatBidExt = bidderSeatBid.getExt();
        if (seatBidExt != null) {
            seatBidBuilder.ext(Json.mapper.valueToTree(ExtPrebid.of(null, seatBidExt)));
        }

        return seatBidBuilder.build();
    }

    /**
     * Returns an OpenRTB {@link Bid} with "prebid" and "bidder" extension fields populated.
     */
    private static Bid toBid(BidderBid bidderBid, String bidder, TargetingKeywordsCreator keywordsCreator,
                             Map<Bid, String> winningBidsWithCacheIds) {
        final Bid bid = bidderBid.getBid();
        final Map<String, String> targetingKeywords = keywordsCreator != null
                ? keywordsCreator.makeFor(bid, bidder, winningBidsWithCacheIds.containsKey(bid),
                winningBidsWithCacheIds.get(bid))
                : null;

        // TODO: by now cache is not filled (same behavior is observed in open-source version), either fill it or
        // delete from extension
        final ExtBidPrebid prebidExt = ExtBidPrebid.of(bidderBid.getType(), targetingKeywords, null);

        final ExtPrebid<ExtBidPrebid, ObjectNode> bidExt = ExtPrebid.of(prebidExt, bid.getExt());

        return bid.toBuilder().ext(Json.mapper.valueToTree(bidExt)).build();
    }

    /**
     * Creates {@link ExtBidResponse} populated with response time, errors and debug info (if requested) from all
     * bidders
     */
    private static ExtBidResponse toExtBidResponse(List<BidderResponse> results, BidRequest bidRequest,
                                                   TargetingKeywordsCreator keywordsCreator) {
        final Map<String, Integer> responseTimeMillis = results.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, BidderResponse::getResponseTime));

        final Map<String, List<String>> errors = results.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, r -> messages(r.getSeatBid().getErrors())));
        // if price granularity in request is not valid - add corresponding error message for each bidder
        if (keywordsCreator != null && !keywordsCreator.isPriceGranularityValid()) {
            final String priceGranularityError = String.format(
                    "Price bucket granularity error: '%s' is not a recognized granularity",
                    keywordsCreator.priceGranularity());
            errors.replaceAll((k, v) -> append(v, priceGranularityError));
        }

        final Map<String, List<ExtHttpCall>> httpCalls = bidRequest.getTest() == 1
                ? results.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, r -> r.getSeatBid().getHttpCalls()))
                : null;

        return ExtBidResponse.of(httpCalls != null ? ExtResponseDebug.of(httpCalls) : null,
                errors, responseTimeMillis, null);
    }

    private static <T> List<T> append(List<T> originalList, T value) {
        final List<T> list = new ArrayList<>(originalList);
        list.add(value);
        return list;
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private int responseTime(long startTime) {
        return Math.toIntExact(CLOCK.millis() - startTime);
    }

    private static List<String> messages(List<BidderError> errors) {
        return CollectionUtils.emptyIfNull(errors).stream().map(BidderError::getMessage).collect(Collectors.toList());
    }
}
