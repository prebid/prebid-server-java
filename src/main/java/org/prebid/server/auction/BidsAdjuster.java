package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.adjustment.BidAdjustmentFactorResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.PbsUtil;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidsAdjuster {

    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";

    private final ResponseBidValidator responseBidValidator;
    private final CurrencyConversionService currencyService;
    private final BidAdjustmentFactorResolver bidAdjustmentFactorResolver;
    private final PriceFloorEnforcer priceFloorEnforcer;
    private final DsaEnforcer dsaEnforcer;
    private final JacksonMapper mapper;

    public BidsAdjuster(ResponseBidValidator responseBidValidator,
                        CurrencyConversionService currencyService,
                        BidAdjustmentFactorResolver bidAdjustmentFactorResolver,
                        PriceFloorEnforcer priceFloorEnforcer,
                        DsaEnforcer dsaEnforcer,
                        JacksonMapper mapper) {

        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidAdjustmentFactorResolver = Objects.requireNonNull(bidAdjustmentFactorResolver);
        this.priceFloorEnforcer = Objects.requireNonNull(priceFloorEnforcer);
        this.dsaEnforcer = Objects.requireNonNull(dsaEnforcer);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public List<AuctionParticipation> validateAndAdjustBids(List<AuctionParticipation> auctionParticipations,
                                                            AuctionContext auctionContext,
                                                            BidderAliases aliases) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> validBidderResponse(auctionParticipation, auctionContext, aliases))
                .map(auctionParticipation -> applyBidPriceChanges(auctionParticipation, auctionContext.getBidRequest()))
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
                                                     BidderAliases aliases) {

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
                    aliases);

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

    private AuctionParticipation applyBidPriceChanges(AuctionParticipation auctionParticipation,
                                                      BidRequest bidRequest) {
        if (auctionParticipation.isRequestBlocked()) {
            return auctionParticipation;
        }

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();

        final List<BidderBid> bidderBids = seatBid.getBids();
        if (bidderBids.isEmpty()) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids.size());
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final String adServerCurrency = bidRequest.getCur().getFirst();

        for (final BidderBid bidderBid : bidderBids) {
            try {
                final BidderBid updatedBidderBid =
                        updateBidderBidWithBidPriceChanges(bidderBid, bidderResponse, bidRequest, adServerCurrency);
                updatedBidderBids.add(updatedBidderBid);
            } catch (PreBidException e) {
                errors.add(BidderError.generic(e.getMessage()));
            }
        }

        final BidderResponse resultBidderResponse = bidderResponse.with(seatBid.toBuilder()
                .bids(updatedBidderBids)
                .errors(errors)
                .build());
        return auctionParticipation.with(resultBidderResponse);
    }

    private BidderBid updateBidderBidWithBidPriceChanges(BidderBid bidderBid,
                                                         BidderResponse bidderResponse,
                                                         BidRequest bidRequest,
                                                         String adServerCurrency) {
        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();
        final BigDecimal price = bid.getPrice();

        final BigDecimal priceInAdServerCurrency = currencyService.convertCurrency(
                price, bidRequest, StringUtils.stripToNull(bidCurrency), adServerCurrency);

        final BigDecimal priceAdjustmentFactor =
                bidAdjustmentForBidder(bidderResponse.getBidder(), bidRequest, bidderBid);
        final BigDecimal adjustedPrice = adjustPrice(priceAdjustmentFactor, priceInAdServerCurrency);

        final ObjectNode bidExt = bid.getExt();
        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();

        updateExtWithOrigPriceValues(updatedBidExt, price, bidCurrency);

        final Bid.BidBuilder bidBuilder = bid.toBuilder();
        if (adjustedPrice.compareTo(price) != 0) {
            bidBuilder.price(adjustedPrice);
        }

        if (!updatedBidExt.isEmpty()) {
            bidBuilder.ext(updatedBidExt);
        }

        return bidderBid.toBuilder().bid(bidBuilder.build()).build();
    }

    private BigDecimal bidAdjustmentForBidder(String bidder, BidRequest bidRequest, BidderBid bidderBid) {
        final ExtRequestBidAdjustmentFactors adjustmentFactors = extBidAdjustmentFactors(bidRequest);
        if (adjustmentFactors == null) {
            return null;
        }
        final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                bidderBid.getBid().getImpid(), bidRequest.getImp(), bidderBid.getType());

        return bidAdjustmentFactorResolver.resolve(mediaType, adjustmentFactors, bidder);
    }

    private static ExtRequestBidAdjustmentFactors extBidAdjustmentFactors(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = PbsUtil.extRequestPrebid(bidRequest);
        return prebid != null ? prebid.getBidadjustmentfactors() : null;
    }

    private static BigDecimal adjustPrice(BigDecimal priceAdjustmentFactor, BigDecimal price) {
        return priceAdjustmentFactor != null && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                ? price.multiply(priceAdjustmentFactor)
                : price;
    }

    private static void updateExtWithOrigPriceValues(ObjectNode updatedBidExt, BigDecimal price, String bidCurrency) {
        addPropertyToNode(updatedBidExt, ORIGINAL_BID_CPM, new DecimalNode(price));
        if (StringUtils.isNotBlank(bidCurrency)) {
            addPropertyToNode(updatedBidExt, ORIGINAL_BID_CURRENCY, new TextNode(bidCurrency));
        }
    }

    private static void addPropertyToNode(ObjectNode node, String propertyName, JsonNode propertyValue) {
        node.set(propertyName, propertyValue);
    }
}
