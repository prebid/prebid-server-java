package org.rtb.vexing.auction;

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
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.auction.model.BidderRequest;
import org.rtb.vexing.auction.model.BidderResponse;
import org.rtb.vexing.bidder.HttpConnector;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtBidRequest;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestTargeting;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidPrebid;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidResponse;
import org.rtb.vexing.model.openrtb.ext.response.ExtHttpCall;
import org.rtb.vexing.model.openrtb.ext.response.ExtResponseDebug;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Executes an OpenRTB v2.5 Auction.
 */
public class ExchangeService {

    private static final String PREBID_EXT = "prebid";

    private final HttpConnector httpConnector;

    private final BidderCatalog bidderCatalog;

    private Clock clock = Clock.systemDefaultZone();

    public ExchangeService(HttpConnector httpConnector, BidderCatalog bidderCatalog) {
        this.httpConnector = Objects.requireNonNull(httpConnector);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    /**
     * Runs an auction: delegates request to applicable bidders, gathers responses from them and constructs final
     * response containing returned bids and additional information in extensions.
     */
    public Future<BidResponse> holdAuction(BidRequest bidRequest, UidsCookie uidsCookie) {
        final List<BidderRequest> bidderRequests = extractBidderRequests(bidRequest, uidsCookie);

        // Randomize the list to make the auction more fair
        Collections.shuffle(bidderRequests);

        // build targeting keywords creator
        final TargetingKeywordsCreator keywordsCreator;
        try {
            keywordsCreator = buildKeywordsCreator(bidRequest);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final long startTime = clock.millis();

        // send all the requests to the bidders and gathers results
        final CompositeFuture bidderResults = CompositeFuture.join(bidderRequests.stream()
                .map(bidderRequest -> requestBids(bidderRequest, startTime))
                .collect(Collectors.toList()));

        // produce response from bidder results
        return bidderResults.map(result -> toBidResponse(result.result().list(), bidRequest, keywordsCreator));
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
                        .filter(bidderCatalog::isValidName))
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

        final String buyerid = uidsCookie.uidFrom(bidderCatalog.byName(bidder).cookieFamilyName());

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
     * Extracts targeting keywords settings from the bid request and creates {@link TargetingKeywordsCreator}
     * instance if they
     * are present. Returns null if bidrequest.ext.prebid.targeting is missing - it means that no targeting keywords
     * should be included in bid response.
     */
    private static TargetingKeywordsCreator buildKeywordsCreator(BidRequest bidRequest) {
        final TargetingKeywordsCreator result;

        try {
            final ExtBidRequest requestExt = bidRequest.getExt() != null
                    ? Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class) : null;
            final ExtRequestTargeting targeting = requestExt != null && requestExt.prebid != null
                    ? requestExt.prebid.targeting : null;
            result = targeting != null
                    ? TargetingKeywordsCreator.withSettings(targeting.pricegranularity, targeting.lengthmax) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()), e);
        }

        return result;
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
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest, long startTime) {
        return httpConnector.requestBids(bidderCatalog.byName(bidderRequest.bidder), bidderRequest.bidRequest)
                .map(result -> BidderResponse.of(bidderRequest.bidder, result, responseTime(startTime)));
    }

    /**
     * Takes all the bids supplied by the bidder and crafts an OpenRTB {@link BidResponse} to send back to the
     * requester.
     */
    private static BidResponse toBidResponse(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                             TargetingKeywordsCreator keywordsCreator) {
        final Set<Bid> winningBids = determineWinningBids(keywordsCreator, bidderResponses);

        final List<SeatBid> seatBids = bidderResponses.stream()
                .filter(bidderResponse -> !bidderResponse.seatBid.bids.isEmpty())
                .map(bidderRespone -> toSeatBid(bidderRespone, keywordsCreator, winningBids))
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
     * Determines wining bids for each impId (ad unit code). Winning bid is the one with the highest price.
     */
    private static Set<Bid> determineWinningBids(TargetingKeywordsCreator keywordsCreator,
                                                 List<BidderResponse> bidderResponses) {
        // determine winning bids only if targeting keywords are requested
        return keywordsCreator == null ? Collections.emptySet() : bidderResponses.stream()
                .flatMap(bidderResponse -> bidderResponse.seatBid.bids.stream().map(bidderBid -> bidderBid.bid))
                // group bids by impId (ad unit code)
                .collect(Collectors.groupingBy(Bid::getImpid))
                .values().stream()
                // sort each bid list by price and pick a winning bid for each impId
                .peek(bids -> bids.sort(Comparator.comparing(Bid::getPrice).reversed()))
                .map(bids -> bids.get(0)) // each list is guaranteed to be non-empty
                .collect(Collectors.toSet());
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private static SeatBid toSeatBid(BidderResponse bidderResponse, TargetingKeywordsCreator keywordsCreator,
                                     Set<Bid> winningBids) {
        final SeatBid.SeatBidBuilder seatBidBuilder = SeatBid.builder()
                .seat(bidderResponse.bidder)
                // prebid cannot support roadblocking
                .group(0)
                .bid(bidderResponse.seatBid.bids.stream()
                        .map(bidderBid -> toBid(bidderBid, bidderResponse.bidder, keywordsCreator, winningBids))
                        .collect(Collectors.toList()));

        if (bidderResponse.seatBid.ext != null) {
            seatBidBuilder.ext(Json.mapper.valueToTree(ExtPrebid.of(null, bidderResponse.seatBid.ext)));
        }

        return seatBidBuilder.build();
    }

    /**
     * Returns an OpenRTB {@link Bid} with "prebid" and "bidder" extension fields populated
     */
    private static Bid toBid(BidderBid bidderBid, String bidder, TargetingKeywordsCreator keywordsCreator,
                             Set<Bid> winningBids) {
        final Bid bid = bidderBid.bid;
        final Map<String, String> targetingKeywords = keywordsCreator != null
                ? keywordsCreator.makeFor(bid, bidder, winningBids.contains(bid))
                : null;

        final ExtBidPrebid prebidExt = ExtBidPrebid.builder()
                .type(bidderBid.type)
                .targeting(targetingKeywords)
                .build();

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
                .collect(Collectors.toMap(r -> r.bidder, r -> r.responseTime));

        final Map<String, List<String>> errors = results.stream()
                .collect(Collectors.toMap(r -> r.bidder, r -> r.seatBid.errors));
        // if price granularity in request is not valid - add corresponding error message for each bidder
        if (keywordsCreator != null && !keywordsCreator.isPriceGranularityValid()) {
            final String priceGranularityError = String.format(
                    "Price bucket granularity error: '%s' is not a recognized granularity",
                    keywordsCreator.priceGranularity());
            errors.replaceAll((k, v) -> append(v, priceGranularityError));
        }

        final Map<String, List<ExtHttpCall>> httpCalls = bidRequest.getTest() == 1
                ? results.stream()
                .collect(Collectors.toMap(r -> r.bidder, r -> r.seatBid.httpCalls))
                : null;

        return ExtBidResponse.builder()
                .responsetimemillis(responseTimeMillis)
                .errors(errors)
                .debug(httpCalls != null ? ExtResponseDebug.of(httpCalls) : null)
                .build();
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
        return Math.toIntExact(clock.millis() - startTime);
    }
}
