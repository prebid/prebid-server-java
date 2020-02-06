package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
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
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredBidResponse;
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
    private static final String CONTEXT_EXT = "context";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final TypeReference<List<SeatBid>> SEATBID_LIST_TYPEREFERENCE = new TypeReference<List<SeatBid>>() {
    };

    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final JacksonMapper mapper;

    public StoredResponseProcessor(ApplicationSettings applicationSettings,
                                   BidderCatalog bidderCatalog,
                                   JacksonMapper mapper) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.mapper = Objects.requireNonNull(mapper);
    }

    Future<StoredResponseResult> getStoredResponseResult(List<Imp> imps, Map<String, String> aliases, Timeout timeout) {
        final List<Imp> requiredRequestImps = new ArrayList<>();
        final Map<String, String> storedResponseIdToImpId = new HashMap<>();

        try {
            fillStoredResponseIdsAndRequestingImps(imps, requiredRequestImps, storedResponseIdToImpId, aliases);
        } catch (InvalidRequestException ex) {
            return Future.failedFuture(ex);
        }

        if (storedResponseIdToImpId.isEmpty()) {
            return Future.succeededFuture(StoredResponseResult.of(imps, Collections.emptyList()));
        }

        return applicationSettings.getStoredResponses(storedResponseIdToImpId.keySet(), timeout)
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored response fetching failed with reason: %s", exception.getMessage()))))
                .map(storedResponseDataResult -> convertToSeatBid(storedResponseDataResult, storedResponseIdToImpId))
                .map(storedResponse -> StoredResponseResult.of(requiredRequestImps, storedResponse));
    }

    List<BidderResponse> mergeWithBidderResponses(List<BidderResponse> bidderResponses, List<SeatBid> storedResponses,
                                                  List<Imp> imps) {
        if (CollectionUtils.isEmpty(storedResponses)) {
            return bidderResponses;
        }

        final Map<String, BidderResponse> bidderToResponse = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, Function.identity()));
        final Map<String, SeatBid> bidderToSeatBid = storedResponses.stream()
                .collect(Collectors.toMap(SeatBid::getSeat, Function.identity()));
        final Map<String, BidType> impIdToBidType = imps.stream()
                .collect(Collectors.toMap(Imp::getId, this::resolveBidType));
        final Set<String> responseBidders = new HashSet<>(bidderToResponse.keySet());
        responseBidders.addAll(bidderToSeatBid.keySet());

        return responseBidders.stream()
                .map(bidder -> makeBidderResponse(bidderToResponse.get(bidder), bidderToSeatBid.get(bidder),
                        impIdToBidType))
                .collect(Collectors.toList());
    }

    private void fillStoredResponseIdsAndRequestingImps(List<Imp> imps, List<Imp> requiredRequestImps,
                                                        Map<String, String> storedResponseIdToImpId,
                                                        Map<String, String> aliases) {
        for (final Imp imp : imps) {
            final String impId = imp.getId();
            final ObjectNode extImpNode = imp.getExt();
            final ExtImp extImp = getExtImp(extImpNode, impId);
            final ExtImpPrebid extImpPrebid = extImp != null ? extImp.getPrebid() : null;
            if (extImpPrebid == null) {
                requiredRequestImps.add(imp);
                continue;
            }

            final ExtStoredAuctionResponse storedAuctionResponse = extImpPrebid.getStoredAuctionResponse();
            final String storedAuctionResponseId = storedAuctionResponse != null ? storedAuctionResponse.getId() : null;
            if (StringUtils.isNotEmpty(storedAuctionResponseId)) {
                storedResponseIdToImpId.put(storedAuctionResponseId, impId);
                continue;
            }

            resolveStoredBidResponse(requiredRequestImps, storedResponseIdToImpId, aliases, imp, impId, extImpNode,
                    extImpPrebid);
        }
    }

    private ExtImp getExtImp(ObjectNode extImpNode, String impId) {
        try {
            return mapper.mapper().treeToValue(extImpNode, ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.imp.ext for impId = %s : %s",
                    impId, e.getMessage()));
        }
    }

    private void resolveStoredBidResponse(List<Imp> requiredRequestImps, Map<String, String> storedResponseIdToImpId,
                                          Map<String, String> aliases, Imp imp, String impId, ObjectNode extImpNode,
                                          ExtImpPrebid extImpPrebid) {
        final List<ExtStoredBidResponse> storedBidResponse = extImpPrebid.getStoredBidResponse();
        final Map<String, String> bidderToStoredId = storedBidResponse != null
                ? getBidderToStoredResponseId(storedBidResponse, impId)
                : Collections.emptyMap();
        if (!bidderToStoredId.isEmpty()) {
            final Imp resolvedBiddersImp = removeStoredResponseBidders(imp, extImpNode, bidderToStoredId.keySet());
            if (hasValidBidder(aliases, resolvedBiddersImp)) {
                requiredRequestImps.add(resolvedBiddersImp);
            }
            storedResponseIdToImpId.putAll(bidderToStoredId.values().stream()
                    .collect(Collectors.toMap(Function.identity(), id -> impId)));
        } else {
            requiredRequestImps.add(imp);
        }
    }

    private Map<String, String> getBidderToStoredResponseId(List<ExtStoredBidResponse> extStoredBidResponses,
                                                            String impId) {
        final Map<String, String> bidderToStoredResponseId = new HashMap<>();
        for (final ExtStoredBidResponse extStoredBidResponse : extStoredBidResponses) {
            final String bidder = extStoredBidResponse.getBidder();
            if (StringUtils.isEmpty(bidder)) {
                throw new InvalidRequestException(String.format(
                        "Bidder was not defined for imp.ext.prebid.storedBidResponse for imp with id %s", impId));
            }

            final String id = extStoredBidResponse.getId();
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
                .anyMatch(bidder -> !Objects.equals(bidder, PREBID_EXT) && !Objects.equals(bidder, CONTEXT_EXT)
                        && isValidBidder(bidder, aliases));
    }

    private <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private boolean isValidBidder(String bidder, Map<String, String> aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
    }

    private List<SeatBid> convertToSeatBid(StoredResponseDataResult storedResponseDataResult,
                                           Map<String, String> storedResponseIdToImpId) {
        final List<SeatBid> resolvedSeatBids = new ArrayList<>();
        for (final Map.Entry<String, String> idToRowSeatBid : storedResponseDataResult.getStoredSeatBid().entrySet()) {
            final String id = idToRowSeatBid.getKey();
            final String impId = storedResponseIdToImpId.get(id);
            final String rowSeatBid = idToRowSeatBid.getValue();
            try {
                final List<SeatBid> seatBids = mapper.mapper().readValue(rowSeatBid, SEATBID_LIST_TYPEREFERENCE);
                resolvedSeatBids.addAll(seatBids.stream()
                        .map(seatBid -> updateSeatBidBids(seatBid, impId))
                        .collect(Collectors.toList()));
            } catch (IOException e) {
                throw new InvalidRequestException(String.format("Can't parse Json for stored response with id %s", id));
            }
        }
        validateStoredSeatBid(resolvedSeatBids);
        return mergeSameBidderSeatBid(resolvedSeatBids);
    }

    private SeatBid updateSeatBidBids(SeatBid seatBid, String impId) {
        return seatBid.toBuilder().bid(updateBidsWithImpId(seatBid.getBid(), impId)).build();
    }

    private List<Bid> updateBidsWithImpId(List<Bid> bids, String impId) {
        return bids.stream().map(bid -> updateBidWithImpId(bid, impId)).collect(Collectors.toList());
    }

    private static Bid updateBidWithImpId(Bid bid, String impId) {
        return bid.toBuilder().impid(impId).build();
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

    private BidderResponse makeBidderResponse(BidderResponse bidderResponse, SeatBid seatBid,
                                              Map<String, BidType> impIdToBidType) {
        if (bidderResponse != null) {
            final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();
            return BidderResponse.of(bidderResponse.getBidder(),
                    seatBid == null ? bidderSeatBid : makeBidderSeatBid(bidderSeatBid, seatBid, impIdToBidType),
                    bidderResponse.getResponseTime());
        } else {
            return BidderResponse.of(seatBid.getSeat(), makeBidderSeatBid(null, seatBid, impIdToBidType), 0);
        }
    }

    private BidderSeatBid makeBidderSeatBid(BidderSeatBid bidderSeatBid, SeatBid seatBid,
                                            Map<String, BidType> impIdToBidType) {
        final boolean nonNullBidderSeatBid = bidderSeatBid != null;
        final String bidCurrency = nonNullBidderSeatBid
                ? bidderSeatBid.getBids().stream()
                .map(BidderBid::getBidCurrency).filter(Objects::nonNull)
                .findAny().orElse(DEFAULT_BID_CURRENCY)
                : DEFAULT_BID_CURRENCY;
        final List<BidderBid> bidderBids = seatBid != null
                ? seatBid.getBid().stream()
                .map(bid -> makeBidderBid(bid, bidCurrency, impIdToBidType))
                .collect(Collectors.toList())
                : new ArrayList<>();
        if (nonNullBidderSeatBid) {
            bidderBids.addAll(bidderSeatBid.getBids());
        }
        return BidderSeatBid.of(bidderBids,
                nonNullBidderSeatBid ? bidderSeatBid.getHttpCalls() : Collections.emptyList(),
                nonNullBidderSeatBid ? bidderSeatBid.getErrors() : Collections.emptyList());
    }

    private BidderBid makeBidderBid(Bid bid, String bidCurrency, Map<String, BidType> impIdToBidType) {
        return BidderBid.of(bid, getBidType(bid.getExt(), impIdToBidType.get(bid.getImpid())), bidCurrency);
    }

    private BidType getBidType(ObjectNode bidExt, BidType bidType) {
        final ObjectNode bidExtPrebid = bidExt != null ? (ObjectNode) bidExt.get(PREBID_EXT) : null;
        final ExtBidPrebid extBidPrebid = bidExtPrebid != null ? parseExtBidPrebid(bidExtPrebid) : null;
        return extBidPrebid != null ? extBidPrebid.getType() : bidType;
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode bidExtPrebid) {
        try {
            return mapper.mapper().treeToValue(bidExtPrebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding stored response bid.ext.prebid");
        }
    }

    private BidType resolveBidType(Imp imp) {
        BidType bidType = BidType.banner;
        if (imp.getBanner() != null) {
            return bidType;
        } else if (imp.getVideo() != null) {
            bidType = BidType.video;
        } else if (imp.getXNative() != null) {
            bidType = BidType.xNative;
        } else if (imp.getAudio() != null) {
            bidType = BidType.audio;
        }
        return bidType;
    }
}
