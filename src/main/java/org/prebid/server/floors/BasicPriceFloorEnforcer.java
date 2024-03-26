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
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.PriceFloorInfo;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class BasicPriceFloorEnforcer implements PriceFloorEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(BasicPriceFloorEnforcer.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final int ENFORCE_RATE_MIN = 0;
    private static final int ENFORCE_RATE_MAX = 100;

    private final CurrencyConversionService currencyConversionService;
    private final Metrics metrics;

    public BasicPriceFloorEnforcer(CurrencyConversionService currencyConversionService, Metrics metrics) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public AuctionParticipation enforce(BidRequest bidRequest,
                                        AuctionParticipation auctionParticipation,
                                        Account account,
                                        BidRejectionTracker rejectionTracker) {

        return shouldApplyEnforcement(auctionParticipation, account)
                ? applyEnforcement(bidRequest, auctionParticipation, account, rejectionTracker)
                : auctionParticipation;
    }

    private static boolean shouldApplyEnforcement(AuctionParticipation auctionParticipation, Account account) {
        final AccountPriceFloorsConfig accountPriceFloorsConfig = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getPriceFloors);
        if (accountPriceFloorsConfig == null || BooleanUtils.isFalse(accountPriceFloorsConfig.getEnabled())) {
            return false;
        }

        final Optional<PriceFloorRules> floors = extractFloors(auctionParticipation);
        final Boolean enabled = floors.map(PriceFloorRules::getEnabled).orElse(null);
        final Boolean skipped = floors.map(PriceFloorRules::getSkipped).orElse(null);
        if (BooleanUtils.isFalse(enabled) || BooleanUtils.isTrue(skipped)) {
            return false;
        }

        final PriceFloorEnforcement enforcement =
                floors.map(PriceFloorRules::getEnforcement).orElse(null);
        if (isNotEnforcedByRequest(enforcement)) {
            return false;
        }

        return isSatisfiedByEnforceRate(enforcement, accountPriceFloorsConfig);
    }

    private static Optional<PriceFloorRules> extractFloors(AuctionParticipation auctionParticipation) {
        return Optional.ofNullable(auctionParticipation.getBidderRequest())
                .map(BidderRequest::getBidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getFloors);
    }

    private static boolean isNotEnforcedByRequest(PriceFloorEnforcement enforcement) {
        final Boolean enforcePbs = ObjectUtil.getIfNotNull(enforcement, PriceFloorEnforcement::getEnforcePbs);

        return BooleanUtils.isFalse(enforcePbs);
    }

    private static boolean isSatisfiedByEnforceRate(PriceFloorEnforcement enforcement,
                                                    AccountPriceFloorsConfig accountPriceFloorsConfig) {

        final Integer requestEnforceRate = ObjectUtil.getIfNotNull(enforcement, PriceFloorEnforcement::getEnforceRate);
        final Integer accountEnforceRate = accountPriceFloorsConfig.getEnforceFloorsRate();
        if (requestEnforceRate == null && accountEnforceRate == null) {
            return true;
        }

        if (isNotValidEnforceRate(requestEnforceRate) || isNotValidEnforceRate(accountEnforceRate)) {
            return false;
        }

        final int pickedRate = ThreadLocalRandom.current().nextInt(ENFORCE_RATE_MAX);
        final boolean satisfiedByRequest = requestEnforceRate == null || pickedRate < requestEnforceRate;
        final boolean satisfiedByAccount = accountEnforceRate == null || pickedRate < accountEnforceRate;
        return satisfiedByRequest && satisfiedByAccount;
    }

    private static boolean isNotValidEnforceRate(Integer value) {
        return value != null && (value < ENFORCE_RATE_MIN || value > ENFORCE_RATE_MAX);
    }

    private AuctionParticipation applyEnforcement(BidRequest bidRequest,
                                                  AuctionParticipation auctionParticipation,
                                                  Account account,
                                                  BidRejectionTracker rejectionTracker) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);
        if (CollectionUtils.isEmpty(bidderBids)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final List<BidderError> warnings = new ArrayList<>(seatBid.getWarnings());

        final BidRequest bidderBidRequest = Optional.ofNullable(auctionParticipation.getBidderRequest())
                .map(BidderRequest::getBidRequest)
                .orElse(null);
        final boolean enforceDealFloors = enforceDealFloors(auctionParticipation, account);

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();

            // skip bid enforcement for deals if not allowed
            if (StringUtils.isNotEmpty(bid.getDealid()) && !enforceDealFloors) {
                continue;
            }

            final BigDecimal price = bid.getPrice();
            final BigDecimal floor = resolveFloor(bidderBid, bidderBidRequest, bidRequest, errors);

            if (isPriceBelowFloor(price, floor)) {
                final String impId = bid.getImpid();
                warnings.add(BidderError.rejectedIpf(
                        "Bid with id '%s' was rejected by floor enforcement: price %s is below the floor %s"
                                .formatted(bid.getId(), price, floor), impId));

                rejectionTracker.reject(impId, BidRejectionReason.REJECTED_DUE_TO_PRICE_FLOOR);
                updatedBidderBids.remove(bidderBid);
            }
        }

        if (bidderBids.size() == updatedBidderBids.size()
                && seatBid.getErrors().size() == errors.size()
                && seatBid.getWarnings().size() == warnings.size()) {

            return auctionParticipation;
        }

        rejectionTracker.restoreFromRejection(updatedBidderBids);
        final BidderSeatBid bidderSeatBid = seatBid.toBuilder()
                .bids(updatedBidderBids)
                .errors(errors)
                .warnings(warnings)
                .build();
        return auctionParticipation.with(bidderResponse.with(bidderSeatBid));
    }

    private static boolean enforceDealFloors(AuctionParticipation auctionParticipation, Account account) {
        final Boolean requestEnforceDealFloors = extractFloors(auctionParticipation)
                .map(PriceFloorRules::getEnforcement)
                .map(PriceFloorEnforcement::getFloorDeals)
                .orElse(null);
        final AccountPriceFloorsConfig accountPriceFloorsConfig = account.getAuction().getPriceFloors();
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

            final Imp imp = correspondingImp(bidderBid.getBid(), bidRequest.getImp());
            final String bidRequestCurrency = resolveBidRequestCurrency(bidRequest);
            return convertCurrency(imp.getBidfloor(), bidRequest, imp.getBidfloorcur(), bidRequestCurrency);
        } catch (PreBidException e) {
            final String logMessage = "Price floors enforcement failed for request id: %s, reason: %s"
                    .formatted(bidRequest.getId(), e.getMessage());
            logger.debug(logMessage);
            conditionalLogger.error(logMessage, 0.01d);
            metrics.updatePriceFloorGeneralAlertsMetric(MetricName.err);
            errors.add(BidderError.badServerResponse("Price floors enforcement failed: " + e.getMessage()));

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

        return convertCurrency(floor, bidRequest, resolvedFloorCurrency, bidRequestCurrency);
    }

    private BigDecimal convertCurrency(BigDecimal floor,
                                       BidRequest bidRequest,
                                       String fromCurrency,
                                       String toCurrency) {

        if (fromCurrency != null && !fromCurrency.equals(toCurrency)) {
            return currencyConversionService.convertCurrency(
                    floor,
                    bidRequest,
                    fromCurrency,
                    toCurrency);
        }

        return floor;
    }

    private static String resolveBidRequestCurrency(BidRequest bidRequest) {
        final List<String> currencies = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getCur);
        return CollectionUtils.isEmpty(currencies) ? null : currencies.get(0);
    }

    private static Imp correspondingImp(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        return ListUtils.emptyIfNull(imps).stream()
                .filter(imp -> Objects.equals(impId, imp.getId()))
                .findFirst()
                // Should never happen, see ResponseBidValidator usage.
                .orElseThrow(() -> new PreBidException("Bid with impId %s doesn't have matched imp".formatted(impId)));
    }

    private static boolean isPriceBelowFloor(BigDecimal price, BigDecimal bidFloor) {
        return bidFloor != null && price.compareTo(bidFloor) < 0;
    }
}
