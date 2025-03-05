package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidadjustments.BidAdjustmentsProcessor;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidsAdjuster {

    private final ResponseBidValidator responseBidValidator;
    private final PriceFloorEnforcer priceFloorEnforcer;
    private final BidAdjustmentsProcessor bidAdjustmentsProcessor;
    private final DsaEnforcer dsaEnforcer;

    public BidsAdjuster(ResponseBidValidator responseBidValidator,
                        PriceFloorEnforcer priceFloorEnforcer,
                        BidAdjustmentsProcessor bidAdjustmentsProcessor,
                        DsaEnforcer dsaEnforcer) {

        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.priceFloorEnforcer = Objects.requireNonNull(priceFloorEnforcer);
        this.bidAdjustmentsProcessor = Objects.requireNonNull(bidAdjustmentsProcessor);
        this.dsaEnforcer = Objects.requireNonNull(dsaEnforcer);
    }

    public List<AuctionParticipation> validateAndAdjustBids(List<AuctionParticipation> auctionParticipations,
                                                            AuctionContext auctionContext,
                                                            BidderAliases aliases,
                                                            ExtRequestPrebidAlternateBidderCodes alternateBidderCodes) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> validBidderResponse(
                        auctionParticipation,
                        auctionContext,
                        aliases,
                        alternateBidderCodes))

                .map(auctionParticipation -> bidAdjustmentsProcessor.enrichWithAdjustedBids(
                        auctionParticipation,
                        auctionContext.getBidRequest(),
                        auctionContext.getBidAdjustments()))

                .map(auctionParticipation -> priceFloorEnforcer.enforce(
                        auctionContext.getBidRequest(),
                        auctionParticipation,
                        auctionContext.getAccount(),
                        auctionContext.getBidRejectionTrackers().get(auctionParticipation.getBidder())))

                .map(auctionParticipation -> dsaEnforcer.enforce(
                        auctionContext.getBidRequest(),
                        auctionParticipation,
                        auctionContext.getBidRejectionTrackers().get(auctionParticipation.getBidder())))
                .toList();
    }

    private AuctionParticipation validBidderResponse(AuctionParticipation auctionParticipation,
                                                     AuctionContext auctionContext,
                                                     BidderAliases aliases,
                                                     ExtRequestPrebidAlternateBidderCodes alternateBidderCodes) {

        if (auctionParticipation.isRequestBlocked()) {
            return auctionParticipation;
        }

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final List<BidderError> warnings = new ArrayList<>(seatBid.getWarnings());

        final List<String> requestCurrencies = bidRequest.getCur();
        if (requestCurrencies.size() > 1) {
            warnings.add(BidderError.badInput(
                    "a single currency (" + requestCurrencies.getFirst() + ") has been chosen for the request. "
                            + "ORTB 2.6 requires that all responses are in the same currency."));
        }

        final List<BidderBid> bids = seatBid.getBids();
        final List<BidderBid> validBids = new ArrayList<>(bids.size());

        for (final BidderBid bid : bids) {
            final ValidationResult validationResult = responseBidValidator.validate(
                    bid,
                    bidderResponse.getBidder(),
                    auctionContext,
                    aliases,
                    alternateBidderCodes);

            if (validationResult.hasWarnings() || validationResult.hasErrors()) {
                errors.add(makeValidationBidderError(bid.getBid(), validationResult));
            }

            if (!validationResult.hasErrors()) {
                validBids.add(bid);
            }
        }

        final BidderResponse resultBidderResponse = bidderResponse.with(
                seatBid.toBuilder()
                        .bids(validBids)
                        .errors(errors)
                        .warnings(warnings)
                        .build());
        return auctionParticipation.with(resultBidderResponse);
    }

    private BidderError makeValidationBidderError(Bid bid, ValidationResult validationResult) {
        final String validationErrors = Stream.concat(
                        validationResult.getErrors().stream().map(message -> "Error: " + message),
                        validationResult.getWarnings().stream().map(message -> "Warning: " + message))
                .collect(Collectors.joining(". "));

        final String bidId = ObjectUtil.getIfNotNullOrDefault(bid, Bid::getId, () -> "unknown");
        return BidderError.invalidBid("BidId `" + bidId + "` validation messages: " + validationErrors);
    }
}
