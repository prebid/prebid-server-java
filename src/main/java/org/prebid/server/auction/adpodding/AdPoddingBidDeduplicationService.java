package org.prebid.server.auction.adpodding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAdPoddingConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AdPoddingBidDeduplicationService {

    private final ObjectMapper mapper;

    public AdPoddingBidDeduplicationService(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper).mapper();
    }

    public AuctionParticipation deduplicate(BidRequest bidRequest,
                                            AuctionParticipation auctionParticipation,
                                            Account account,
                                            BidRejectionTracker bidRejectionTracker) {

        final Boolean deduplicationEnabled = Optional.ofNullable(account.getAuction())
                .map(AccountAuctionConfig::getAdPodding)
                .map(AccountAdPoddingConfig::getDeduplicate)
                .orElse(false);

        if (!deduplicationEnabled) {
            return auctionParticipation;
        }

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final List<BidderBid> bids = seatBid.getBids();
        final List<BidderBid> remainedBids = bids.stream()
                  .filter(bid -> bid.getType() != BidType.video && bid.getType() != BidType.audio)
                  .toList();

        final List<BidderBid> winningBids = new ArrayList<>(remainedBids);

        deduplicateBids(
                bidRequest.getImp(),
                winningBids,
                bidRejectionTracker,
                bids,
                imp -> ObjectUtil.getIfNotNull(imp.getVideo(), Video::getPodid) != null,
                bidderBid -> bidderBid.getType() == BidType.video);

        deduplicateBids(
                bidRequest.getImp(),
                winningBids,
                bidRejectionTracker,
                bids,
                imp -> ObjectUtil.getIfNotNull(imp.getAudio(), Audio::getPodid) != null,
                bidderBid -> bidderBid.getType() == BidType.audio);

        return auctionParticipation.with(bidderResponse.with(seatBid.with(winningBids)));
    }

    private void deduplicateBids(List<Imp> imps,
                                 List<BidderBid> winningBids,
                                 BidRejectionTracker bidRejectionTracker,
                                 List<BidderBid> bids,
                                 Predicate<Imp> impFilter,
                                 Predicate<BidderBid> bidFilter) {

        final List<Imp> filteredImps = imps.stream()
                .filter(impFilter)
                .toList();

        final Map<String, Map<Set<String>, List<BidderBid>>> impIdToBids = bids.stream()
                .filter(bidFilter)
                .collect(Collectors.groupingBy(
                        bid -> correspondingImp(bid.getBid().getImpid(), filteredImps).getId(),
                        //todo: what if adomain is absent
                        Collectors.groupingBy(bid -> new HashSet<>(bid.getBid().getAdomain()))));

        for (Map<Set<String>, List<BidderBid>> adomainToBids : impIdToBids.values()) {
            for (List<BidderBid> filteredBids : adomainToBids.values()) {
                BigDecimal highestCpmPerSecond = BigDecimal.ZERO;
                BidderBid winnerBid = null;
                for (BidderBid bidderBid : filteredBids) {
                    final Bid bid = bidderBid.getBid();
                    final Integer duration = ObjectUtils.firstNonNull(bid.getDur(), getBidMetaDuration(bid));
                    final BigDecimal cpmPerSecond = bid.getPrice()
                            .divide(BigDecimal.valueOf(duration), 4, RoundingMode.HALF_EVEN);

                    if (cpmPerSecond.compareTo(highestCpmPerSecond) > 0) {
                        highestCpmPerSecond = cpmPerSecond;
                        if (winnerBid != null) {
                            bidRejectionTracker.rejectBid(winnerBid, BidRejectionReason.RESPONSE_REJECTED_DUPLICATE);
                        }
                        winnerBid = bidderBid;
                    } else {
                        bidRejectionTracker.rejectBid(bidderBid, BidRejectionReason.RESPONSE_REJECTED_DUPLICATE);
                    }
                }

                if (winnerBid != null) {
                    winningBids.add(winnerBid);
                }
            }
        }
    }

    private static Imp correspondingImp(String impId, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> impId.startsWith(imp.getId()))
                .findFirst()
                // should never occur. See ResponseBidValidator
                .orElseThrow(() -> new PreBidException("Bid with impId %s doesn't have matched imp".formatted(impId)));
    }

    private Integer getBidMetaDuration(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .filter(ext -> ext.hasNonNull("prebid"))
                .map(this::convertValue)
                .map(ExtBidPrebid::getMeta)
                .map(ExtBidPrebidMeta::getDur)
                .orElse(null);
    }

    private ExtBidPrebid convertValue(JsonNode jsonNode) {
        try {
            return mapper.convertValue(jsonNode.get("prebid"), ExtBidPrebid.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
