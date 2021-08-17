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
import org.prebid.server.auction.model.Tuple2;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves stored response data retrieving and BidderResponse merging processes.
 */
public class StoredResponseProcessor {

    private static final String PREBID_EXT = "prebid";
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private static final TypeReference<List<SeatBid>> SEATBID_LIST_TYPE = new TypeReference<List<SeatBid>>() {
    };

    private final ApplicationSettings applicationSettings;
    private final JacksonMapper mapper;

    public StoredResponseProcessor(ApplicationSettings applicationSettings,
                                   JacksonMapper mapper) {

        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.mapper = Objects.requireNonNull(mapper);
    }

    Future<StoredResponseResult> getStoredResponseResult(List<Imp> imps, Timeout timeout) {
        final Map<String, ExtImpPrebid> impExtPrebids = getImpsExtPrebid(imps);
        final Map<String, String> auctionStoredResponseToImpId = getAuctionStoredResponses(impExtPrebids);
        final List<Imp> requiredRequestImps = excludeStoredAuctionResponseImps(imps, auctionStoredResponseToImpId);

        final Map<String, Map<String, String>> impToBidderToStoredBidResponseId = getStoredBidResponses(impExtPrebids,
                requiredRequestImps);

        final Set<String> storedIds = new HashSet<>(auctionStoredResponseToImpId.keySet());

        storedIds.addAll(
                impToBidderToStoredBidResponseId.values().stream()
                        .flatMap(bidderToId -> bidderToId.values().stream())
                        .collect(Collectors.toSet()));

        if (storedIds.isEmpty()) {
            return Future.succeededFuture(StoredResponseResult.of(imps, Collections.emptyList(),
                    Collections.emptyMap()));
        }

        return applicationSettings.getStoredResponses(storedIds, timeout)
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored response fetching failed with reason: %s", exception.getMessage()))))
                .map(storedResponseDataResult -> StoredResponseResult.of(
                        requiredRequestImps,
                        convertToSeatBid(storedResponseDataResult, auctionStoredResponseToImpId),
                        mapStoredBidResponseIdsToValues(storedResponseDataResult.getIdToStoredResponses(),
                                impToBidderToStoredBidResponseId)));
    }

    private List<Imp> excludeStoredAuctionResponseImps(List<Imp> imps,
                                                       Map<String, String> auctionStoredResponseToImpId) {
        return imps.stream()
                .filter(imp -> !auctionStoredResponseToImpId.containsValue(imp.getId()))
                .collect(Collectors.toList());
    }

    List<BidderResponse> mergeWithBidderResponses(List<BidderResponse> bidderResponses,
                                                  List<SeatBid> storedAuctionResponses,
                                                  List<Imp> imps) {
        if (CollectionUtils.isEmpty(storedAuctionResponses)) {
            return bidderResponses;
        }

        final Map<String, BidderResponse> bidderToResponse = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, Function.identity()));
        final Map<String, SeatBid> bidderToSeatBid = storedAuctionResponses.stream()
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

    private Map<String, ExtImpPrebid> getImpsExtPrebid(List<Imp> imps) {
        return imps.stream()
                .collect(Collectors.toMap(Imp::getId, imp -> getExtImp(imp.getExt(), imp.getId()).getPrebid()));
    }

    private Map<String, String> getAuctionStoredResponses(Map<String, ExtImpPrebid> extImpPrebids) {
        return extImpPrebids.entrySet().stream()
                .map(impIdToExtPrebid -> Tuple2.of(impIdToExtPrebid.getKey(),
                        extractAuctionStoredResponseId(impIdToExtPrebid.getValue())))
                .filter(impIdToStoredResponseId -> impIdToStoredResponseId.getRight() != null)
                .collect(Collectors.toMap(Tuple2::getRight, Tuple2::getLeft));
    }

    private String extractAuctionStoredResponseId(ExtImpPrebid extImpPrebid) {
        final ExtStoredAuctionResponse storedAuctionResponse = extImpPrebid.getStoredAuctionResponse();
        return storedAuctionResponse != null ? storedAuctionResponse.getId() : null;
    }

    private Map<String, Map<String, String>> getStoredBidResponses(Map<String, ExtImpPrebid> extImpPrebids,
                                                                   List<Imp> imps) {
        // PBS supports stored bid response only for requests with single impression, but it can be changed in future
        if (imps.size() != 1) {
            return Collections.emptyMap();
        }

        final Set<String> impsIds = imps.stream().map(Imp::getId).collect(Collectors.toSet());

        return extImpPrebids.entrySet().stream()
                .filter(impIdToExtPrebid -> impsIds.contains(impIdToExtPrebid.getKey()))
                .filter(impIdToExtPrebid -> CollectionUtils
                        .isNotEmpty(impIdToExtPrebid.getValue().getStoredBidResponse()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        impIdToStoredResponses ->
                                resolveStoredBidResponse(impIdToStoredResponses.getValue().getStoredBidResponse())));
    }

    private ExtImp getExtImp(ObjectNode extImpNode, String impId) {
        try {
            return mapper.mapper().treeToValue(extImpNode, ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format(
                    "Error decoding bidRequest.imp.ext for impId = %s : %s", impId, e.getMessage()));
        }
    }

    private Map<String, String> resolveStoredBidResponse(List<ExtStoredBidResponse> storedBidResponse) {
        return storedBidResponse.stream()
                .collect(Collectors.toMap(ExtStoredBidResponse::getBidder, ExtStoredBidResponse::getId));
    }

    private List<SeatBid> convertToSeatBid(StoredResponseDataResult storedResponseDataResult,
                                           Map<String, String> auctionStoredResponses) {
        final List<SeatBid> resolvedSeatBids = new ArrayList<>();
        final Map<String, String> idToStoredResponses = storedResponseDataResult.getIdToStoredResponses();
        for (final Map.Entry<String, String> storedIdToImpId : auctionStoredResponses.entrySet()) {
            final String id = storedIdToImpId.getKey();
            final String impId = storedIdToImpId.getValue();
            final String rowSeatBid = idToStoredResponses.get(id);
            if (rowSeatBid == null) {
                throw new InvalidRequestException(String.format("Failed to fetch stored auction response for"
                        + " impId = %s and storedAuctionResponse id = %s.", impId, id));
            }
            final List<SeatBid> seatBids = parseSeatBid(id, rowSeatBid);
            validateStoredSeatBid(seatBids);
            resolvedSeatBids.addAll(seatBids.stream()
                    .map(seatBid -> updateSeatBidBids(seatBid, impId))
                    .collect(Collectors.toList()));
        }
        return mergeSameBidderSeatBid(resolvedSeatBids);
    }

    private List<SeatBid> parseSeatBid(String id, String rowSeatBid) {
        try {
            return mapper.mapper().readValue(rowSeatBid, SEATBID_LIST_TYPE);
        } catch (IOException e) {
            throw new InvalidRequestException(String.format("Can't parse Json for stored response with id %s", id));
        }
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

            if (CollectionUtils.isEmpty(seatBid.getBid())) {
                throw new InvalidRequestException("There must be at least one bid in stored response seatBid");
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

    private Map<String, Map<String, String>> mapStoredBidResponseIdsToValues(
            Map<String, String> idToStoredResponses,
            Map<String, Map<String, String>> impToBidderToStoredBidResponseId) {

        return impToBidderToStoredBidResponseId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().entrySet().stream()
                                .filter(bidderToId -> idToStoredResponses.containsKey(bidderToId.getValue()))
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        bidderToId -> idToStoredResponses.get(bidderToId.getValue())))));
    }

    private BidderResponse makeBidderResponse(BidderResponse bidderResponse, SeatBid seatBid,
                                              Map<String, BidType> impIdToBidType) {
        if (bidderResponse != null) {
            final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();
            return BidderResponse.of(bidderResponse.getBidder(),
                    seatBid == null ? bidderSeatBid : makeBidderSeatBid(bidderSeatBid, seatBid, impIdToBidType),
                    bidderResponse.getResponseTime());
        } else {
            return BidderResponse.of(seatBid != null ? seatBid.getSeat() : null,
                    makeBidderSeatBid(null, seatBid, impIdToBidType), 0);
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
