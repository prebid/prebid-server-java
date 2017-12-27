package org.rtb.vexing.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.rtb.vexing.auction.model.BidderRequest;
import org.rtb.vexing.auction.model.BidderResponse;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidPrebid;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidResponse;
import org.rtb.vexing.model.openrtb.ext.response.ExtHttpCall;
import org.rtb.vexing.model.openrtb.ext.response.ExtResponseDebug;

import java.time.Clock;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ExchangeService {

    private static final String PREBID_EXT = "prebid";

    private final BidderCatalog bidderCatalog;

    private Clock clock = Clock.systemDefaultZone();

    public ExchangeService(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    /**
     * Executes an OpenRTB v2.5 Auction.
     */
    public Future<BidResponse> holdAuction(BidRequest bidRequest) {
        Objects.requireNonNull(bidRequest);

        final List<BidderRequest> bidderRequests = extractBidderRequests(bidRequest);

        // Randomize the list to make the auction more fair
        Collections.shuffle(bidderRequests);

        final long startTime = clock.millis();

        // send all the requests to the bidders and gathers results
        final CompositeFuture bidderResults = CompositeFuture.join(bidderRequests.stream()
                .map(bidderRequest -> requestBids(bidderRequest, startTime))
                .collect(Collectors.toList()));

        // produce response from bidder results
        return bidderResults.map(result -> toBidResponse(result.result().list(), bidRequest));
    }

    /**
     * Takes an OpenRTB request and returns the OpenRTB requests sanitized for each bidder.
     * <p>
     * This will copy the {@link BidRequest} into a list of requests, where the {@link BidRequest}.imp[].ext field
     * will only consist of the "prebid" field and the field for the appropriate bidder parameters. We will drop all
     * extended fields beyond this context, so this will not be compatible with any other uses of the extension area.
     * That is, this method will work, but the bidders will not see any other extension fields.
     * <p>
     * NOTE: the return list will only contain entries for bidders that both have the extension field in at least one
     * {@link Imp}, and are known to {@link BidderCatalog}.
     */
    private List<BidderRequest> extractBidderRequests(BidRequest bidRequest) {
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
                // for each bidder create a new request that is a copy of original request except imp extensions
                .map(bidder -> BidderRequest.of(bidder, bidRequest.toBuilder()
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
        return bidderCatalog.byName(bidderRequest.bidder).requestBids(bidderRequest.bidRequest)
                .map(result -> BidderResponse.of(bidderRequest.bidder, result, responseTime(startTime)));
    }

    /**
     * Takes all the bids supplied by the bidder and crafts an OpenRTB {@link BidResponse} to send back to the
     * requester.
     */
    private static BidResponse toBidResponse(List<BidderResponse> bidderResponses, BidRequest bidRequest) {
        final List<SeatBid> seatBids = bidderResponses.stream()
                .filter(bidderResponse -> CollectionUtils.isNotEmpty(bidderResponse.seatBid.bids))
                .map(ExchangeService::toSeatBid)
                .collect(Collectors.toList());

        final ExtBidResponse bidResponseExt = toExtBidResponse(bidderResponses, bidRequest);

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
    private static SeatBid toSeatBid(BidderResponse bidderResponse) {
        final SeatBid.SeatBidBuilder seatBidBuilder = SeatBid.builder()
                .seat(bidderResponse.bidder)
                // prebid cannot support roadblocking
                .group(0)
                .bid(bidderResponse.seatBid.bids.stream()
                        .map(ExchangeService::toBid)
                        .collect(Collectors.toList()));

        if (bidderResponse.seatBid.ext != null) {
            seatBidBuilder.ext(Json.mapper.valueToTree(
                    ExtPrebid.<Void, ObjectNode>of(null, bidderResponse.seatBid.ext)));
        }

        return seatBidBuilder.build();
    }

    /**
     * Returns an OpenRTB {@link Bid} with "prebid" and "bidder" extension fields populated
     */
    private static Bid toBid(BidderBid bidderBid) {
        final ExtBidPrebid prebidExt = ExtBidPrebid.builder().type(bidderBid.type).build();

        final ExtPrebid<ExtBidPrebid, ObjectNode> bidExt = ExtPrebid.of(prebidExt, bidderBid.bid.getExt());

        return bidderBid.bid.toBuilder().ext(Json.mapper.valueToTree(bidExt)).build();
    }

    /**
     * Creates {@link ExtBidResponse} populated with response time, errors and debug info (if requested) from all
     * bidders
     */
    private static ExtBidResponse toExtBidResponse(List<BidderResponse> results, BidRequest bidRequest) {
        final Map<String, Integer> responseTimeMillis = results.stream()
                .collect(Collectors.toMap(r -> r.bidder, r -> r.responseTime));

        final Map<String, List<String>> errors = results.stream()
                .collect(Collectors.toMap(r -> r.bidder, r -> ListUtils.emptyIfNull(r.seatBid.errors)));

        final Map<String, List<ExtHttpCall>> httpCalls = bidRequest.getTest() == 1
                ? results.stream()
                .collect(Collectors.toMap(r -> r.bidder, r -> ListUtils.emptyIfNull(r.seatBid.httpCalls)))
                : null;

        return ExtBidResponse.builder()
                .responsetimemillis(responseTimeMillis)
                .errors(errors)
                .debug(httpCalls != null ? ExtResponseDebug.of(httpCalls) : null)
                .build();
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }
}
