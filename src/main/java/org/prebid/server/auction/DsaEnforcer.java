package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DsaEnforcer {

    private static final String DSA_EXT = "dsa";
    private static final Set<Integer> DSA_REQUIRED = Set.of(2, 3);

    public AuctionParticipation enforce(BidRequest bidRequest,
                                        AuctionParticipation auctionParticipation,
                                        BidRejectionTracker rejectionTracker) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);

        if (CollectionUtils.isEmpty(bidderBids) || !isDsaValidationRequired(bidRequest)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> warnings = new ArrayList<>(seatBid.getWarnings());

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();

            if (!isValid(bid)) {
                warnings.add(BidderError.invalidBid("Bid \"%s\" missing DSA".formatted(bid.getId())));
                rejectionTracker.reject(bid.getImpid(), BidRejectionReason.GENERAL);
                updatedBidderBids.remove(bidderBid);
            }
        }

        if (bidderBids.size() == updatedBidderBids.size()) {
            return auctionParticipation;
        }

        final BidderSeatBid bidderSeatBid = seatBid.toBuilder()
                .bids(updatedBidderBids)
                .warnings(warnings)
                .build();
        return auctionParticipation.with(bidderResponse.with(bidderSeatBid));
    }

    private static boolean isDsaValidationRequired(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getRegs())
                .map(Regs::getExt)
                .map(ExtRegs::getDsa)
                .map(ExtRegsDsa::getDsaRequired)
                .map(DSA_REQUIRED::contains)
                .orElse(false);
    }

    private boolean isValid(Bid bid) {
        final ObjectNode bidExt = bid.getExt();
        return bidExt != null && bidExt.hasNonNull(DSA_EXT) && !bidExt.get(DSA_EXT).isEmpty();
    }

}
