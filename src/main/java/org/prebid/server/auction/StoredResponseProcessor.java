package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredSeatBid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
public class StoredResponseProcessor {

    private final ApplicationSettings applicationSettings;
    private final TimeoutFactory timeoutFactory;
    private final long defaultTimeout;

    public StoredResponseProcessor(ApplicationSettings applicationSettings, TimeoutFactory timeoutFactory,
                                   long defaultTimeout) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.defaultTimeout = defaultTimeout;
    }

    public Future<StoredResponseResult> getStoredResponseResult(List<Imp> imps, Long tmax) {
        final List<Imp> realRequestImps = new ArrayList<>();
        final Set<String> storedResponseIds = new HashSet<>();
        getStoredResponseIdsAndRequestingImps(imps, realRequestImps, storedResponseIds);

        return applicationSettings.getStoredResponse(storedResponseIds, timeout(tmax))
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored response fetching failed: %s", exception.getMessage()))))
                .map(this::convertToSeatBid)
                .map(storedResponse -> StoredResponseResult.of(realRequestImps, storedResponse));
    }

    private void getStoredResponseIdsAndRequestingImps(List<Imp> imps, List<Imp> realRequestImps,
                                                       Set<String> storedResponseIds) {
        for (final Imp imp : imps) {
            final String impId = imp.getId();
            final ObjectNode extImpNode = imp.getExt();
            if (extImpNode == null) {
                continue;
            }

            final ExtImp extImp = getExtImp(extImpNode, impId);
            final ExtImpPrebid extImpPrebid = extImp != null ? extImp.getPrebid() : null;
            if (extImpPrebid == null) {
                realRequestImps.add(imp);
                continue;
            }

            final ExtStoredAuctionResponse storedAuctionResponse = extImpPrebid.getStoredAuctionResponse();
            final String storedAuctionResponseId = storedAuctionResponse != null ? storedAuctionResponse.getId() : null;
            if (!StringUtils.isNotEmpty(storedAuctionResponseId)) {
                storedResponseIds.add(storedAuctionResponseId);
                continue;
            }

            final List<ExtStoredSeatBid> storedBidResponse = extImpPrebid.getStoredBidResponse();
            final Map<String, String> bidderToStoredId = storedBidResponse != null
                    ? getBidderToStoredResponseId(storedBidResponse, impId)
                    : null;
            if (bidderToStoredId != null && !bidderToStoredId.isEmpty()) {
                realRequestImps.add(removeStoredResponseBidders(imp, extImpNode, bidderToStoredId.keySet()));
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

    private List<SeatBid> convertToSeatBid(StoredResponseDataResult storedResponseDataResult) {
        final List<SeatBid> seatBids = new ArrayList<>();
        for (final Map.Entry<String, String> idToRowSeatBid : storedResponseDataResult.getStoredSeatBid().entrySet()) {
            final String id = idToRowSeatBid.getKey();
            final String rowSeatBid = idToRowSeatBid.getValue();
            try {
                seatBids.add(Json.mapper.readValue(rowSeatBid, SeatBid.class));
            } catch (IOException e) {
                throw new InvalidRequestException(String.format("Can't parse Json for stored response with id %s", id));
            }
        }
        return seatBids;
    }

    /**
     * If the request defines tmax explicitly, then it is returned as is. Otherwise default timeout is returned.
     */
    private Timeout timeout(Long tmax) {
        return timeoutFactory.create(tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }

    public BidResponse makeBidResponse(BidRequest bidRequest, List<SeatBid> seatBids) {
        return BidResponse.builder()
                .id(bidRequest.getId())
                .cur(bidRequest.getCur().get(0))
                .seatbid(mergeSeatBids(seatBids, Collections.emptyList()))
                .ext(Json.mapper.valueToTree(ExtBidResponse.of(null, null, null, bidRequest.getTmax(), null)))
                .build();
    }

    public List<SeatBid> mergeSeatBids(List<SeatBid> storedSeatBid, List<SeatBid> responseSeatBid) {
        responseSeatBid.addAll(storedSeatBid);
        return responseSeatBid.stream().collect(Collectors.groupingBy(SeatBid::getSeat, Collectors.toList()))
                .entrySet().stream()
                .map(bidderToSeatBid -> mergeSameBidderSeatBids(bidderToSeatBid.getKey(), bidderToSeatBid.getValue()))
                .collect(Collectors.toList());
    }

    // TODO resolve aliases?
    private SeatBid mergeSameBidderSeatBids(String seat, List<SeatBid> storedSeatBids) {
        return SeatBid.builder()
                .bid(storedSeatBids.stream().map(SeatBid::getBid).flatMap(List::stream).collect(Collectors.toList()))
                .seat(seat)
                .ext(storedSeatBids.stream().map(SeatBid::getExt).filter(Objects::nonNull).findFirst().orElse(null))
                .build();
    }
}
