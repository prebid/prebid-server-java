package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloorsEnforcement;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PriceFloorEnforcer {

    public static AuctionParticipation enforce(AuctionParticipation auctionParticipation, Account account) {
        return shouldApplyEnforcement(auctionParticipation, account)
                ? applyEnforcement(auctionParticipation)
                : auctionParticipation;
    }

    private static boolean shouldApplyEnforcement(AuctionParticipation auctionParticipation, Account account) {
        // TODO: If the config enabled flag is true
        //  PS. or do nothing if NoopService bean is used

        final BidRequest bidRequest = auctionParticipation.getBidderRequest().getBidRequest();
        final ExtRequestPrebidFloors floors = getFloors(bidRequest);
        if (floors == null) {
            return false;
        }

        final Boolean enforcePbs = ObjectUtil.getIfNotNull(floors.getEnforcement(),
                ExtRequestPrebidFloorsEnforcement::getEnforcePbs);
        if (BooleanUtils.isFalse(enforcePbs)) {
            return false;
        }

        // TODO: If the bid response contains a seatbid.bid.dealid
        //  and (enforce-deal-floors config is set to true and ext.prebid.floors.enforcement.enforcedeals is true)

        return true;
    }

    private static ExtRequestPrebidFloors getFloors(BidRequest bidRequest) {
        final ExtRequest ext = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getExt);
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(ext, ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
    }

    private static AuctionParticipation applyEnforcement(AuctionParticipation auctionParticipation) {
        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);
        if (CollectionUtils.isEmpty(bidderBids)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            final Imp imp = correspondingImp(bid, auctionParticipation);

            final BigDecimal price = bid.getPrice();
            final BigDecimal floor = imp.getBidfloor();

            if (isPriceBelowFloor(price, floor)) {
                errors.add(BidderError.generic(
                        String.format("Bid with id '%s' was rejected: price %s is below the floor %s",
                                bid.getId(), price, floor)));

                // TODO: create a record for analytics adapters to be aware of this rejection and reason

                updatedBidderBids.remove(bidderBid);
            }
        }

        if (bidderBids.size() == updatedBidderBids.size()) {
            return auctionParticipation;
        }

        final BidderResponse resultBidderResponse =
                bidderResponse.with(BidderSeatBid.of(updatedBidderBids, seatBid.getHttpCalls(), errors));
        return auctionParticipation.with(resultBidderResponse);
    }

    private static boolean isPriceBelowFloor(BigDecimal price, BigDecimal bidFloor) {
        return bidFloor != null && price.compareTo(bidFloor) < 0;
    }

    private static Imp correspondingImp(Bid bid, AuctionParticipation auctionParticipation) {
        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final BidRequest bidRequest = ObjectUtil.getIfNotNull(bidderRequest, BidderRequest::getBidRequest);
        return correspondingImp(bid, bidRequest.getImp());
    }

    private static Imp correspondingImp(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        return imps.stream()
                .filter(imp -> Objects.equals(impId, imp.getId()))
                .findFirst()
                // Should never occur. See ResponseBidValidator
                .orElseThrow(() -> new PreBidException(
                        String.format("Bid with impId %s doesn't have matched imp", impId)));
    }
}
