package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.PriceFloorInfo;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloorsEnforcement;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class BasicPriceFloorEnforcer implements PriceFloorEnforcer {

    private final CurrencyConversionService currencyConversionService;

    public BasicPriceFloorEnforcer(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
    }

    @Override
    public AuctionParticipation enforce(BidRequest bidRequest,
                                        AuctionParticipation auctionParticipation,
                                        Account account) {

        final AccountPriceFloorsConfig accountPriceFloorsConfig = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getPriceFloors);

        return shouldApplyEnforcement(auctionParticipation, accountPriceFloorsConfig)
                ? applyEnforcement(bidRequest, auctionParticipation, accountPriceFloorsConfig)
                : auctionParticipation;
    }

    private static boolean shouldApplyEnforcement(AuctionParticipation auctionParticipation,
                                                  AccountPriceFloorsConfig accountPriceFloorsConfig) {

        if (accountPriceFloorsConfig == null || BooleanUtils.isNotTrue(accountPriceFloorsConfig.getEnabled())) {
            return false;
        }

        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final BidRequest bidderBidRequest = ObjectUtil.getIfNotNull(bidderRequest, BidderRequest::getBidRequest);
        final ExtRequestPrebidFloors floors = getFloors(bidderBidRequest);
        final ExtRequestPrebidFloorsEnforcement enforcement = ObjectUtil.getIfNotNull(floors,
                ExtRequestPrebidFloors::getEnforcement);

        return isEnforcedByRequest(enforcement) && isSatisfiedByEnforceRate(enforcement);
    }

    private static ExtRequestPrebidFloors getFloors(BidRequest bidRequest) {
        final ExtRequest ext = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getExt);
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(ext, ExtRequest::getPrebid);

        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
    }

    private static boolean isEnforcedByRequest(ExtRequestPrebidFloorsEnforcement enforcement) {
        final Boolean enforcePbs = ObjectUtil.getIfNotNull(enforcement,
                ExtRequestPrebidFloorsEnforcement::getEnforcePbs);

        return BooleanUtils.isNotFalse(enforcePbs);
    }

    private static boolean isSatisfiedByEnforceRate(ExtRequestPrebidFloorsEnforcement enforcement) {
        final Integer enforceRate = ObjectUtil.getIfNotNull(enforcement,
                ExtRequestPrebidFloorsEnforcement::getEnforceRate);

        return enforceRate == null || (enforceRate >= 0 && enforceRate <= 100
                && ThreadLocalRandom.current().nextDouble() < enforceRate / 100.0);
    }

    private AuctionParticipation applyEnforcement(BidRequest bidRequest,
                                                  AuctionParticipation auctionParticipation,
                                                  AccountPriceFloorsConfig accountPriceFloorsConfig) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);
        if (CollectionUtils.isEmpty(bidderBids)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());

        final BidRequest bidderBidRequest = auctionParticipation.getBidderRequest().getBidRequest();
        final ExtRequestPrebidFloors floors = getFloors(bidderBidRequest);
        final boolean enforcedDealFloors = isEnforcedDealFloors(floors, accountPriceFloorsConfig);

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();

            // skip bid enforcement for deals if not allowed
            if (StringUtils.isNotEmpty(bid.getDealid()) && !enforcedDealFloors) {
                continue;
            }

            final BigDecimal price = bid.getPrice();
            final BigDecimal floor = resolveFloor(bidderBid, bidderBidRequest, bidRequest, errors);

            if (isPriceBelowFloor(price, floor)) {
                errors.add(BidderError.generic(
                        String.format("Bid with id '%s' was rejected by floor enforcement: "
                                + "price %s is below the floor %s", bid.getId(), price, floor)));

                // TODO: create a record for analytics adapters to be aware of this rejection and reason

                updatedBidderBids.remove(bidderBid);
            }
        }

        // just for optimization
        if (bidderBids.size() == updatedBidderBids.size() && seatBid.getErrors().size() == errors.size()) {
            return auctionParticipation;
        }

        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(updatedBidderBids, seatBid.getHttpCalls(), errors);
        return auctionParticipation.with(bidderResponse.with(bidderSeatBid));
    }

    private static boolean isEnforcedDealFloors(ExtRequestPrebidFloors floors,
                                                AccountPriceFloorsConfig accountPriceFloorsConfig) {

        final ExtRequestPrebidFloorsEnforcement enforcement = ObjectUtil.getIfNotNull(floors,
                ExtRequestPrebidFloors::getEnforcement);
        final Boolean requestEnforceDealFloors = ObjectUtil.getIfNotNull(enforcement,
                ExtRequestPrebidFloorsEnforcement::getFloorDeals);

        final Boolean accountEnforceDealFloors = accountPriceFloorsConfig.getEnforceDealFloors();

        return BooleanUtils.isTrue(requestEnforceDealFloors) && BooleanUtils.isTrue(accountEnforceDealFloors);
    }

    private BigDecimal resolveFloor(BidderBid bidderBid,
                                    BidRequest bidderBidRequest,
                                    BidRequest bidRequest,
                                    List<BidderError> errors) {

        final PriceFloorInfo priceFloorInfo = bidderBid.getPriceFloorInfo();
        final BigDecimal customBidderFloor = ObjectUtil.getIfNotNull(priceFloorInfo, PriceFloorInfo::getFloor);

        try {
            if (customBidderFloor != null) {
                return convertIfRequired(customBidderFloor, priceFloorInfo.getCurrency(), bidderBidRequest, bidRequest);
            }

            final Imp imp = correspondingImp(bidderBid.getBid(), bidderBidRequest.getImp());
            return convertIfRequired(imp.getBidfloor(), imp.getBidfloorcur(), bidderBidRequest, bidRequest);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(
                    String.format("Price floors enforcement failed: %s", e.getMessage())));
            return null;
        }
    }

    /**
     * Converts floor according to incoming auction request currency (since response bid is already converted).
     * <p>
     * Floor currency resolved in order: floor currency -> bidder request currency -> auction request currency.
     */
    private BigDecimal convertIfRequired(BigDecimal floor,
                                         String floorCurrency,
                                         BidRequest bidderBidRequest,
                                         BidRequest bidRequest) {

        final String resolvedFloorCurrency = ObjectUtils.defaultIfNull(floorCurrency,
                resolveBidRequestCurrency(bidderBidRequest));

        final String bidRequestCurrency = resolveBidRequestCurrency(bidRequest);

        if (resolvedFloorCurrency != null && !resolvedFloorCurrency.equals(bidRequestCurrency)) {
            return currencyConversionService.convertCurrency(
                    floor,
                    bidRequest,
                    resolvedFloorCurrency,
                    bidRequestCurrency);
        }

        return floor;
    }

    private static String resolveBidRequestCurrency(BidRequest bidRequest) {
        final List<String> currencies = bidRequest.getCur();
        return CollectionUtils.isEmpty(currencies) ? null : currencies.get(0);
    }

    private static Imp correspondingImp(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        return ListUtils.emptyIfNull(imps).stream()
                .filter(imp -> Objects.equals(impId, imp.getId()))
                .findFirst()
                // Should never happen, see ResponseBidValidator usage.
                .orElseThrow(() -> new PreBidException(
                        String.format("Bid with impId %s doesn't have matched imp", impId)));
    }

    private static boolean isPriceBelowFloor(BigDecimal price, BigDecimal bidFloor) {
        return bidFloor != null && price.compareTo(bidFloor) < 0;
    }
}
