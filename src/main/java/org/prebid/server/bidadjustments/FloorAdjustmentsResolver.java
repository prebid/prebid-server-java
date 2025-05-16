package org.prebid.server.bidadjustments;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidadjustments.model.BidAdjustmentType;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRule;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FloorAdjustmentsResolver {

    private final BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver;
    private final CurrencyConversionService currencyService;

    public FloorAdjustmentsResolver(BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver,
                                    CurrencyConversionService currencyService) {

        this.bidAdjustmentsRulesResolver = Objects.requireNonNull(bidAdjustmentsRulesResolver);
        this.currencyService = Objects.requireNonNull(currencyService);
    }

    public Price resolve(Price initialBidFloorPrice,
                         BidRequest bidRequest,
                         Set<ImpMediaType> targetMediaTypes,
                         String targetBidder) {

        final String currency = bidRequest.getCur().getFirst();
        Price minimalBidFloorPrice = null;
        BigDecimal minimalPriceBidFloorValue = new BigDecimal(Integer.MAX_VALUE);

        for (ImpMediaType targetMediaType : targetMediaTypes) {
            final Price resolvedPrice = resolve(initialBidFloorPrice, bidRequest, targetMediaType, targetBidder);
            final BigDecimal convertedResolvedValue = currencyService.convertCurrency(
                    resolvedPrice.getValue(), bidRequest, resolvedPrice.getCurrency(), currency);
            if (convertedResolvedValue.compareTo(minimalPriceBidFloorValue) < 0) {
                minimalBidFloorPrice = resolvedPrice;
                minimalPriceBidFloorValue = convertedResolvedValue;
            }
        }

        return ObjectUtils.firstNonNull(minimalBidFloorPrice, initialBidFloorPrice);
    }

    public Price resolve(Price initialBidFloorPrice,
                         BidRequest bidRequest,
                         ImpMediaType targetMediaType,
                         String targetBidder) {

        final List<BidAdjustmentsRule> rules = bidAdjustmentsRulesResolver.resolve(
                bidRequest, targetMediaType, targetBidder, null);

        return reversePrice(initialBidFloorPrice, rules, bidRequest);
    }

    private Price reversePrice(Price price,
                               List<BidAdjustmentsRule> bidAdjustmentRules,
                               BidRequest bidRequest) {

        final List<BidAdjustmentsRule> reversedRules = bidAdjustmentRules.reversed();
        final String resolvedCurrency = price.getCurrency();
        BigDecimal resolvedPrice = price.getValue();

        for (BidAdjustmentsRule rule : reversedRules) {
            final BidAdjustmentType adjustmentType = rule.getAdjType();
            final BigDecimal adjustmentValue = rule.getValue();
            final String adjustmentCurrency = rule.getCurrency();

            switch (adjustmentType) {
                case MULTIPLIER -> resolvedPrice = resolvedPrice.divide(adjustmentValue, 4, RoundingMode.HALF_EVEN);
                case CPM -> {
                    final BigDecimal convertedAdjustmentValue = currencyService.convertCurrency(
                            adjustmentValue, bidRequest, adjustmentCurrency, resolvedCurrency);
                    resolvedPrice = BidderUtil.roundFloor(resolvedPrice.add(convertedAdjustmentValue));
                }
                case STATIC -> throw new PreBidException("STATIC type can't be applied to a floor price");
            }
        }

        return Price.of(resolvedCurrency, resolvedPrice);
    }
}
