package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ImpMediaTypeResolver;
import org.prebid.server.auction.adjustment.BidAdjustmentFactorResolver;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.PbsUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BidAdjustmentsProcessor {

    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";

    private final CurrencyConversionService currencyService;
    private final BidAdjustmentFactorResolver bidAdjustmentFactorResolver;
    private final BidAdjustmentsResolver bidAdjustmentsResolver;
    private final JacksonMapper mapper;

    public BidAdjustmentsProcessor(CurrencyConversionService currencyService,
                                   BidAdjustmentFactorResolver bidAdjustmentFactorResolver,
                                   BidAdjustmentsResolver bidAdjustmentsResolver,
                                   JacksonMapper mapper) {

        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidAdjustmentFactorResolver = Objects.requireNonNull(bidAdjustmentFactorResolver);
        this.bidAdjustmentsResolver = Objects.requireNonNull(bidAdjustmentsResolver);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public AuctionParticipation enrichWithAdjustedBids(AuctionParticipation auctionParticipation,
                                                       BidRequest bidRequest,
                                                       BidAdjustments bidAdjustments) {

        if (auctionParticipation.isRequestBlocked()) {
            return auctionParticipation;
        }

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();

        final List<BidderBid> bidderBids = seatBid.getBids();
        if (bidderBids.isEmpty()) {
            return auctionParticipation;
        }

        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final String bidder = auctionParticipation.getBidder();

        final List<BidderBid> updatedBidderBids = bidderBids.stream()
                .map(bidderBid -> applyBidAdjustments(bidderBid, bidRequest, bidder, bidAdjustments, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final BidderResponse updatedBidderResponse = bidderResponse.with(seatBid.toBuilder()
                .bids(updatedBidderBids)
                .errors(errors)
                .build());

        return auctionParticipation.with(updatedBidderResponse);
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

    private BidderBid applyBidAdjustments(BidderBid bidderBid,
                                          BidRequest bidRequest,
                                          String bidder,
                                          BidAdjustments bidAdjustments,
                                          List<BidderError> errors) {
        try {
            final BidderBid bidWithOriginalPriceAndCurrency = enrichWithOriginalBidPriceAndCurrency(bidderBid);
            final BidderBid bidWithAdjustedPrice = applyBidAdjustmentFactors(
                    bidWithOriginalPriceAndCurrency,
                    bidder,
                    bidRequest);
            return applyBidAdjustmentRules(bidWithAdjustedPrice, bidder, bidRequest, bidAdjustments);
        } catch (PreBidException e) {
            errors.add(BidderError.generic(e.getMessage()));
            return null;
        }
    }

    private BidderBid enrichWithOriginalBidPriceAndCurrency(BidderBid bidderBid) {
        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();
        final BigDecimal price = bid.getPrice();

        final ObjectNode bidExt = bid.getExt();
        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();

        updatedBidExt.set(ORIGINAL_BID_CPM, new DecimalNode(price));
        if (StringUtils.isNotBlank(bidCurrency)) {
            updatedBidExt.set(ORIGINAL_BID_CURRENCY, new TextNode(bidCurrency));
        }

        final Bid updatedBid = bid.toBuilder()
                .ext(updatedBidExt.isEmpty() ? bidExt : updatedBidExt)
                .build();
        return bidderBid.toBuilder().bid(updatedBid).build();
    }

    private BidderBid applyBidAdjustmentFactors(BidderBid bidderBid, String bidder, BidRequest bidRequest) {
        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();
        final BigDecimal price = bid.getPrice();
        final String requestCurrency = bidRequest.getCur().getFirst();

        final BigDecimal priceInAdServerCurrency = currencyService.convertCurrency(
                price, bidRequest, StringUtils.stripToNull(bidCurrency), requestCurrency);

        final BigDecimal priceAdjustmentFactor = bidAdjustmentForBidder(bidder, bidRequest, bidderBid);
        final BigDecimal adjustedPrice = adjustPrice(priceAdjustmentFactor, priceInAdServerCurrency);

        final Bid adjustedBid = bid.toBuilder()
                .price(adjustedPrice.compareTo(price) != 0 ? adjustedPrice : price)
                .build();

        return bidderBid.toBuilder().bid(adjustedBid).build();
    }

    private BigDecimal bidAdjustmentForBidder(String bidder, BidRequest bidRequest, BidderBid bidderBid) {
        final ExtRequestBidAdjustmentFactors adjustmentFactors = extBidAdjustmentFactors(bidRequest);
        if (adjustmentFactors == null) {
            return null;
        }

        final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                bidderBid.getBid().getImpid(),
                bidRequest.getImp(),
                bidderBid.getType());

        return bidAdjustmentFactorResolver.resolve(mediaType, adjustmentFactors, bidder);
    }

    private BidderBid applyBidAdjustmentRules(BidderBid bidderBid,
                                              String bidder,
                                              BidRequest bidRequest,
                                              BidAdjustments bidAdjustments) {

        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();
        final BigDecimal bidPrice = bid.getPrice();

        final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                bid.getImpid(),
                bidRequest.getImp(),
                bidderBid.getType());

        final Price adjustedBidPrice = bidAdjustmentsResolver.resolve(
                Price.of(bidCurrency, bidPrice),
                bidRequest,
                bidAdjustments,
                mediaType == null || mediaType == ImpMediaType.video ? ImpMediaType.video_instream : mediaType,
                bidder,
                bid.getDealid());

        return bidderBid.toBuilder()
                .bidCurrency(adjustedBidPrice.getCurrency())
                .bid(bid.toBuilder().price(adjustedBidPrice.getValue()).build())
                .build();
    }
}
