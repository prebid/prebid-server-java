package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class SkippedAuctionService {

    private final StoredResponseProcessor storedResponseProcessor;

    public SkippedAuctionService(StoredResponseProcessor storedResponseProcessor) {
        this.storedResponseProcessor = Objects.requireNonNull(storedResponseProcessor);
    }

    public Future<AuctionContext> skipAuction(AuctionContext auctionContext) {
        if (auctionContext.isRequestRejected()) {
            return Future.failedFuture("Rejected request cannot be skipped");
        }

        final ExtStoredAuctionResponse storedResponse = Optional.ofNullable(auctionContext.getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getStoredAuctionResponse)
                .orElse(null);

        if (storedResponse == null) {
            return Future.failedFuture(new InvalidRequestException(
                    "the auction can not be skipped, ext.prebid.storedauctionresponse is absent"));
        }

        final List<SeatBid> seatBids = storedResponse.getSeatBids();
        if (seatBids != null) {
            return validateStoredSeatBid(seatBids)
                    .recover(throwable -> {
                        auctionContext.getDebugWarnings().add(throwable.getMessage());
                        return Future.succeededFuture(Collections.emptyList());
                    })
                    .map(storedSeatBids -> enrichAuctionContextWithBidResponse(auctionContext, storedSeatBids))
                    .map(AuctionContext::skipAuction);
        }

        if (storedResponse.getId() != null) {
            final Timeout timeout = auctionContext.getTimeoutContext().getTimeout();
            return storedResponseProcessor.getStoredResponseResult(storedResponse.getId(), timeout)
                    .map(StoredResponseResult::getAuctionStoredResponse)
                    .recover(throwable -> {
                        auctionContext.getDebugWarnings().add(throwable.getMessage());
                        return Future.succeededFuture(Collections.emptyList());
                    })
                    .map(storedSeatBids -> enrichAuctionContextWithBidResponse(auctionContext, storedSeatBids))
                    .map(AuctionContext::skipAuction);
        }

        return Future.failedFuture(new InvalidRequestException(
                "the auction can not be skipped, ext.prebid.storedauctionresponse can not be resolved properly"));

    }

    private Future<List<SeatBid>> validateStoredSeatBid(List<SeatBid> seatBids) {
        for (final SeatBid seatBid : seatBids) {
            if (seatBid == null) {
                return Future.failedFuture(
                        new InvalidRequestException("SeatBid can't be null in stored response"));
            }
            if (StringUtils.isEmpty(seatBid.getSeat())) {
                return Future.failedFuture(
                        new InvalidRequestException("Seat can't be empty in stored response seatBid"));
            }

            if (CollectionUtils.isEmpty(seatBid.getBid())) {
                return Future.failedFuture(
                        new InvalidRequestException("There must be at least one bid in stored response seatBid"));
            }
        }

        return Future.succeededFuture(seatBids);
    }

    private static AuctionContext enrichAuctionContextWithBidResponse(AuctionContext auctionContext,
                                                                      List<SeatBid> seatBids) {

        auctionContext.getDebugWarnings().add("no auction. response defined by storedauctionresponse");
        return auctionContext.with(bidResponse(auctionContext, seatBids));
    }

    private static BidResponse bidResponse(AuctionContext auctionContext, List<SeatBid> seatBids) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final ExtBidResponse extBidResponse = ExtBidResponse.builder()
                .warnings(extractContextWarnings(auctionContext))
                .tmaxrequest(bidRequest.getTmax())
                .build();

        final List<String> cur = bidRequest.getCur();

        return BidResponse.builder()
                .id(bidRequest.getId())
                .cur(CollectionUtils.isNotEmpty(cur) ? cur.getFirst() : null)
                .seatbid(ListUtils.emptyIfNull(seatBids))
                .ext(extBidResponse)
                .build();
    }

    private static Map<String, List<ExtBidderError>> extractContextWarnings(AuctionContext auctionContext) {
        final List<ExtBidderError> contextWarnings = auctionContext.getDebugWarnings().stream()
                .map(message -> ExtBidderError.of(BidderError.Type.generic.getCode(), message))
                .toList();

        return contextWarnings.isEmpty()
                ? Collections.emptyMap()
                : Collections.singletonMap("prebid", contextWarnings);
    }
}
