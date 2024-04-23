package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.DsaPublisherRender;
import org.prebid.server.proto.openrtb.ext.request.DsaRequired;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.response.DsaAdvertiserRender;
import org.prebid.server.proto.openrtb.ext.response.ExtBidDsa;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DsaEnforcer {

    private static final String DSA_EXT = "dsa";
    private static final Set<Integer> DSA_REQUIRED = Set.of(
            DsaRequired.REQUIRED.getValue(),
            DsaRequired.REQUIRED_ONLINE_PLATFORM.getValue());
    private static final int MAX_DSA_FIELD_LENGTH = 100;

    private final JacksonMapper mapper;

    public DsaEnforcer(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    public AuctionParticipation enforce(BidRequest bidRequest,
                                        AuctionParticipation auctionParticipation,
                                        BidRejectionTracker rejectionTracker) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);

        if (CollectionUtils.isEmpty(bidderBids)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> warnings = new ArrayList<>(seatBid.getWarnings());

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();

            final ExtBidDsa dsaResponse = Optional.ofNullable(bid.getExt())
                    .map(ext -> ext.get(DSA_EXT))
                    .map(this::getDsaResponse)
                    .orElse(null);

            final boolean isValid = isDsaValidationRequired(bidRequest)
                    ? isDsaValid(bidRequest, dsaResponse)
                    : dsaResponse == null || isDsaFieldsLengthValid(dsaResponse);

            if (!isValid) {
                warnings.add(BidderError.invalidBid("Bid \"%s\" has invalid DSA".formatted(bid.getId())));
                rejectionTracker.reject(bid.getImpid(), BidRejectionReason.REJECTED_BY_DSA_PRIVACY);
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

    private static boolean isDsaValid(BidRequest bidRequest, ExtBidDsa dsaResponse) {
        if (dsaResponse == null || !isDsaFieldsLengthValid(dsaResponse)) {
            return false;
        }

        final Integer adRender = dsaResponse.getAdRender();
        final Integer pubRender = Optional.ofNullable(bidRequest.getRegs())
                .map(Regs::getExt)
                .map(ExtRegs::getDsa)
                .map(ExtRegsDsa::getPubRender)
                .orElse(null);

        if (pubRender == null) {
            return true;
        }

        if (pubRender.equals(DsaPublisherRender.WILL_RENDER.getValue())
                && adRender != null && adRender.equals(DsaAdvertiserRender.WILL_RENDER.getValue())) {
            return false;
        }

        if (pubRender.equals(DsaPublisherRender.NOT_RENDER.getValue())
                && (adRender == null || adRender.equals(DsaAdvertiserRender.NOT_RENDER.getValue()))) {
            return false;
        }

        return true;
    }

    private static boolean isDsaFieldsLengthValid(ExtBidDsa dsaResponse) {
        return hasValidLength(dsaResponse.getBehalf()) && hasValidLength(dsaResponse.getPaid());
    }

    private static boolean hasValidLength(String value) {
        return value == null || value.length() <= MAX_DSA_FIELD_LENGTH;
    }

    private ExtBidDsa getDsaResponse(JsonNode dsaExt) {
        try {
            return mapper.mapper().convertValue(dsaExt, ExtBidDsa.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
