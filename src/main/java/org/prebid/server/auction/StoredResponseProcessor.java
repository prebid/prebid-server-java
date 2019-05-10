package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredSeatBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Resolves stored response data retrieving and BidderResponse merging processes.
 */
public class StoredResponseProcessor {

    private static final String PREBID_EXT = "prebid";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final TypeReference<List<SeatBid>> SEATBID_LIST_TYPEREFERENCE = new TypeReference<List<SeatBid>>() {
    };

    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final TimeoutFactory timeoutFactory;
    private final long defaultTimeout;

    public StoredResponseProcessor(ApplicationSettings applicationSettings, BidderCatalog bidderCatalog,
                                   TimeoutFactory timeoutFactory, long defaultTimeout) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.defaultTimeout = defaultTimeout;
    }

    public Future<StoredResponseResult> getStoredResponseResult(List<Imp> imps, Long tmax,
                                                                Map<String, String> aliases) {
        final List<Imp> requiredRequestImps = new ArrayList<>();
        final Set<String> storedResponseIds = new HashSet<>();

        try {
            fillStoredResponseIdsAndRequestingImps(imps, requiredRequestImps, storedResponseIds, aliases);
        } catch (InvalidRequestException ex) {
            return Future.failedFuture(ex);
        }

        if (storedResponseIds.isEmpty()) {
            return Future.succeededFuture(StoredResponseResult.of(imps, Collections.emptyList()));
        }

        return applicationSettings.getStoredResponses(storedResponseIds, timeout(tmax))
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored response fetching failed with reason: %s", exception.getMessage()))))
                .map(this::convertToSeatBid)
                .map(storedResponse -> StoredResponseResult.of(requiredRequestImps, storedResponse));
    }

    public List<BidderResponse> mergeWithBidderResponses(List<BidderResponse> bidderResponses,
                                                         List<SeatBid> storedResponses) {
        if (CollectionUtils.isEmpty(storedResponses)) {
            return bidderResponses;
        }

        final Map<String, BidderResponse> bidderToResponse = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, Function.identity()));
        final Map<String, SeatBid> bidderToSeatBid = storedResponses.stream()
                .collect(Collectors.toMap(SeatBid::getSeat, Function.identity()));
        final Set<String> responseBidders = new HashSet<>(bidderToResponse.keySet());
        responseBidders.addAll(bidderToSeatBid.keySet());

        return responseBidders.stream()
                .map(bidder -> makeBidderResponse(bidderToResponse.get(bidder), bidderToSeatBid.get(bidder)))
                .collect(Collectors.toList());
    }

    private void fillStoredResponseIdsAndRequestingImps(List<Imp> imps, List<Imp> requiredRequestImps,
                                                        Set<String> storedResponseIds, Map<String, String> aliases) {
        for (final Imp imp : imps) {
            final String impId = imp.getId();
            final ObjectNode extImpNode = imp.getExt();
            if (extImpNode == null) {
                continue;
            }

            final ExtImp extImp = getExtImp(extImpNode, impId);
            final ExtImpPrebid extImpPrebid = extImp != null ? extImp.getPrebid() : null;
            if (extImpPrebid == null) {
                requiredRequestImps.add(imp);
                continue;
            }

            final ExtStoredAuctionResponse storedAuctionResponse = extImpPrebid.getStoredAuctionResponse();
            final String storedAuctionResponseId = storedAuctionResponse != null ? storedAuctionResponse.getId() : null;
            if (StringUtils.isNotEmpty(storedAuctionResponseId)) {
                storedResponseIds.add(storedAuctionResponseId);
                continue;
            }

            final List<ExtStoredSeatBid> storedBidResponse = extImpPrebid.getStoredBidResponse();
            final Map<String, String> bidderToStoredId = storedBidResponse != null
                    ? getBidderToStoredResponseId(storedBidResponse, impId)
                    : null;
            if (bidderToStoredId != null && !bidderToStoredId.isEmpty()) {
                final Imp resolvedBiddersImp = removeStoredResponseBidders(imp, extImpNode, bidderToStoredId.keySet());
                if (hasValidBidder(aliases, resolvedBiddersImp)) {
                    requiredRequestImps.add(resolvedBiddersImp);
                }
                storedResponseIds.addAll(bidderToStoredId.values());
            }
        }
    }

    private ExtImp getExtImp(ObjectNode extImpNode, String impId) {
        try {
            return Json.mapper.treeToValue(extImpNode, ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.imp.ext for impId = %s : %s",
                    impId, e.getMessage()));
        }
    }

    private Map<String, String> getBidderToStoredResponseId(List<ExtStoredSeatBid> extStoredSeatBids, String impId) {
        final Map<String, String> bidderToStoredResponseId = new HashMap<>();
        for (final ExtStoredSeatBid extStoredSeatBid : extStoredSeatBids) {
            final String bidder = extStoredSeatBid.getBidder();
            if (StringUtils.isEmpty(bidder)) {
                throw new InvalidRequestException(String.format(
                        "Bidder was not defined for imp.ext.prebid.storedBidResponse for imp with id %s", impId));
            }

            final String id = extStoredSeatBid.getId();
            if (StringUtils.isEmpty(id)) {
                throw new InvalidRequestException(String.format(
                        "Id was not defined for imp.ext.prebid.storedBidResponse for imp with id %s", impId));
            }

            bidderToStoredResponseId.put(bidder, id);
        }
        return bidderToStoredResponseId;
    }

    private Imp removeStoredResponseBidders(Imp imp, ObjectNode extImp, Set<String> bidders) {
        boolean isUpdated = false;
        for (final String bidder : bidders) {
            if (extImp.hasNonNull(bidder)) {
                extImp.remove(bidder);
                isUpdated = true;
            }
        }
        return isUpdated ? imp.toBuilder().ext(extImp).build() : imp;
    }

    private boolean hasValidBidder(Map<String, String> aliases, Imp resolvedBiddersImp) {
        return asStream(resolvedBiddersImp.getExt().fieldNames())
                .anyMatch(bidder -> !Objects.equals(bidder, PREBID_EXT) && isValidBidder(bidder, aliases));
    }

    private <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private boolean isValidBidder(String bidder, Map<String, String> aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
    }

    private List<SeatBid> convertToSeatBid(StoredResponseDataResult storedResponseDataResult) {
        final List<SeatBid> seatBids = new ArrayList<>();
        for (final Map.Entry<String, String> idToRowSeatBid : storedResponseDataResult.getStoredSeatBid().entrySet()) {
            final String id = idToRowSeatBid.getKey();
            final String rowSeatBid = idToRowSeatBid.getValue();
            try {
                seatBids.addAll(Json.mapper.readValue(rowSeatBid, SEATBID_LIST_TYPEREFERENCE));
            } catch (IOException e) {
                throw new InvalidRequestException(String.format("Can't parse Json for stored response with id %s", id));
            }
        }
        validateStoredSeatBid(seatBids);
        return mergeSameBidderSeatBid(seatBids);
    }

    private void validateStoredSeatBid(List<SeatBid> seatBids) {
        for (final SeatBid seatBid : seatBids) {
            if (StringUtils.isEmpty(seatBid.getSeat())) {
                throw new InvalidRequestException("Seat can't be empty in stored response seatBid");
            }
        }
    }

    private List<SeatBid> mergeSameBidderSeatBid(List<SeatBid> seatBids) {
        return seatBids.stream().collect(Collectors.groupingBy(SeatBid::getSeat, Collectors.toList()))
                .entrySet().stream()
                .map(bidderToSeatBid -> makeMergedSeatBid(bidderToSeatBid.getKey(), bidderToSeatBid.getValue()))
                .collect(Collectors.toList());
    }

    private SeatBid makeMergedSeatBid(String seat, List<SeatBid> storedSeatBids) {
        return SeatBid.builder()
                .bid(storedSeatBids.stream().map(SeatBid::getBid).flatMap(List::stream).collect(Collectors.toList()))
                .seat(seat)
                .ext(storedSeatBids.stream().map(SeatBid::getExt).filter(Objects::nonNull).findFirst().orElse(null))
                .build();
    }

    private BidderResponse makeBidderResponse(BidderResponse bidderResponse, SeatBid seatBid) {
        if (bidderResponse != null) {
            final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();
            return BidderResponse.of(bidderResponse.getBidder(),
                    seatBid == null ? bidderSeatBid : makeBidderSeatBid(bidderSeatBid, seatBid),
                    bidderResponse.getResponseTime());
        } else {
            return BidderResponse.of(seatBid.getSeat(), makeBidderSeatBid(null, seatBid), 0);
        }
    }

    private BidderSeatBid makeBidderSeatBid(BidderSeatBid bidderSeatBid, SeatBid seatBid) {
        final boolean nonNullBidderSeatBid = bidderSeatBid != null;
        final String bidCurrency = nonNullBidderSeatBid
                ? bidderSeatBid.getBids().stream()
                        .map(BidderBid::getBidCurrency).filter(Objects::nonNull)
                        .findAny().orElse(DEFAULT_BID_CURRENCY)
                : DEFAULT_BID_CURRENCY;
        final List<BidderBid> bidderBids = seatBid != null
                ? seatBid.getBid().stream()
                        .map(bid -> makeBidderBid(bid, bidCurrency))
                        .collect(Collectors.toList())
                : Collections.emptyList();
        if (nonNullBidderSeatBid) {
            bidderBids.addAll(bidderSeatBid.getBids());
        }
        return BidderSeatBid.of(bidderBids,
                nonNullBidderSeatBid ? bidderSeatBid.getHttpCalls() : Collections.emptyList(),
                nonNullBidderSeatBid ? bidderSeatBid.getErrors() : Collections.emptyList());
    }

    private BidderBid makeBidderBid(Bid bid, String bidCurrency) {
        return BidderBid.of(bid, getBidType(bid.getExt()), bidCurrency);
    }

    private BidType getBidType(ObjectNode bidExt) {
        final ObjectNode bidExtPrebid = bidExt != null ? (ObjectNode) bidExt.get(PREBID_EXT) : null;
        final ExtBidPrebid extBidPrebid = bidExtPrebid != null ? parseExtBidPrebid(bidExtPrebid) : null;
        return extBidPrebid != null ? extBidPrebid.getType() : BidType.banner;
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode bidExtPrebid) {
        try {
            return Json.mapper.treeToValue(bidExtPrebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding stored response bid.ext.prebid");
        }
    }

    private Timeout timeout(Long tmax) {
        return timeoutFactory.create(tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }
}
