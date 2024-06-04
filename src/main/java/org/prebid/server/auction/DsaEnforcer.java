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
import org.prebid.server.exception.PreBidException;
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
import java.util.Objects;
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
        this.mapper = Objects.requireNonNull(mapper);
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

            try {
                validateFieldLength(dsaResponse);
                if (isDsaValidationRequired(bidRequest)) {
                    validateDsa(bidRequest, dsaResponse);
                }
            } catch (PreBidException e) {
                warnings.add(BidderError.invalidBid("Bid \"%s\": %s".formatted(bid.getId(), e.getMessage())));
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

    private static void validateDsa(BidRequest bidRequest, ExtBidDsa dsaResponse) {
        if (dsaResponse == null) {
            throw new PreBidException("DSA object missing when required");
        }

        final Integer adRender = dsaResponse.getAdRender();
        final Integer pubRender = bidRequest.getRegs().getExt().getDsa().getPubRender();

        if (pubRender == null) {
            return;
        }

        if (pubRender == DsaPublisherRender.WILL_RENDER.getValue()
                && adRender != null && adRender == DsaAdvertiserRender.WILL_RENDER.getValue()) {
            throw new PreBidException("DSA publisher and buyer both signal will render");
        }

        if (pubRender == DsaPublisherRender.NOT_RENDER.getValue()
                && (adRender == null || adRender == DsaAdvertiserRender.NOT_RENDER.getValue())) {
            throw new PreBidException("DSA publisher and buyer both signal will not render");
        }
    }

    private static void validateFieldLength(ExtBidDsa dsaResponse) {
        if (dsaResponse == null) {
            return;
        }

        if (!hasValidLength(dsaResponse.getBehalf())) {
            throw new PreBidException("DSA behalf exceeds limit of 100 chars");
        }
        if (!hasValidLength(dsaResponse.getPaid())) {
            throw new PreBidException("DSA paid exceeds limit of 100 chars");
        }
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
