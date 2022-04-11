package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.log.ConditionalLogger;
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
                                        Account account) {

        return shouldApplyEnforcement(auctionParticipation, account)
                ? applyEnforcement(bidRequest, auctionParticipation, account)
                : auctionParticipation;
    }

    private static boolean shouldApplyEnforcement(AuctionParticipation auctionParticipation, Account account) {
        final AccountPriceFloorsConfig accountPriceFloorsConfig = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getPriceFloors);
        if (accountPriceFloorsConfig == null || BooleanUtils.isNotTrue(accountPriceFloorsConfig.getEnabled())) {
            return false;
        }

        final PriceFloorRules floors = extractFloors(auctionParticipation);
        final Boolean skipped = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getSkipped);
        if (BooleanUtils.isTrue(skipped)) {
            return false;
        }

        final PriceFloorEnforcement enforcement = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getEnforcement);
        if (isNotEnforcedByRequest(enforcement)) {
            return false;
        }

        return isSatisfiedByEnforceRate(enforcement, accountPriceFloorsConfig);
    }

    private static PriceFloorRules extractFloors(AuctionParticipation auctionParticipation) {
        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final BidRequest bidderBidRequest = ObjectUtil.getIfNotNull(bidderRequest, BidderRequest::getBidRequest);
        final ExtRequest ext = ObjectUtil.getIfNotNull(bidderBidRequest, BidRequest::getExt);
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(ext, ExtRequest::getPrebid);

        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
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
                                                  Account account) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);
        if (CollectionUtils.isEmpty(bidderBids)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final List<BidderError> warnings = new ArrayList<>(seatBid.getErrors());

        final BidRequest bidderBidRequest = auctionParticipation.getBidderRequest().getBidRequest();
        final PriceFloorRules floors = extractFloors(auctionParticipation);
        final boolean enforceDealFloors = enforceDealFloors(floors, account);

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();

            // skip bid enforcement for deals if not allowed
            if (StringUtils.isNotEmpty(bid.getDealid()) && !enforceDealFloors) {
                continue;
            }

            final BigDecimal price = bid.getPrice();
            final BigDecimal floor = resolveFloor(bidderBid, bidderBidRequest, bidRequest, errors);

            if (isPriceBelowFloor(price, floor)) {
                warnings.add(BidderError.generic(
                        String.format("Bid with id '%s' was rejected by floor enforcement: "
                                + "price %s is below the floor %s", bid.getId(), price, floor)));

                // TODO: create a record for analytics adapters to be aware of this rejection and reason

                updatedBidderBids.remove(bidderBid);
            }
        }

        if (bidderBids.size() == updatedBidderBids.size()
                && seatBid.getErrors().size() == errors.size()
                && seatBid.getWarnings().size() == warnings.size()) {

            return auctionParticipation;
        }

        final BidderSeatBid bidderSeatBid =
                BidderSeatBid.of(updatedBidderBids, seatBid.getHttpCalls(), errors, warnings);
        return auctionParticipation.with(bidderResponse.with(bidderSeatBid));
    }

    private static boolean enforceDealFloors(PriceFloorRules floors, Account account) {
        final PriceFloorEnforcement enforcement = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getEnforcement);
        final Boolean requestEnforceDealFloors = ObjectUtil.getIfNotNull(enforcement,
                PriceFloorEnforcement::getFloorDeals);

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
            final String logMessage =
                    String.format("Price floors enforcement failed for request id: %s, reason: %s",
                            bidRequest.getId(),
                            e.getMessage());
            logger.debug(logMessage);
            conditionalLogger.error(logMessage, 0.01d);
            metrics.updatePriceFloorGeneralAlertsMetric(MetricName.err);
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
