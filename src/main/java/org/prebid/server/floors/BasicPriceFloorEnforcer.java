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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloors;
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

    @Override
    public AuctionParticipation enforce(AuctionParticipation auctionParticipation, Account account) {
        final AccountPriceFloorsConfig accountPriceFloorsConfig = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getPriceFloors);

        return shouldApplyEnforcement(auctionParticipation, accountPriceFloorsConfig)
                ? applyEnforcement(auctionParticipation, accountPriceFloorsConfig)
                : auctionParticipation;
    }

    private static boolean shouldApplyEnforcement(AuctionParticipation auctionParticipation,
                                                  AccountPriceFloorsConfig accountPriceFloorsConfig) {

        if (accountPriceFloorsConfig == null || BooleanUtils.isNotTrue(accountPriceFloorsConfig.getEnabled())) {
            return false;
        }

        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final BidRequest bidRequest = ObjectUtil.getIfNotNull(bidderRequest, BidderRequest::getBidRequest);
        final ExtRequestPrebidFloors floors = getFloors(bidRequest);
        if (floors == null) {
            return false;
        }

        return BooleanUtils.isNotFalse(floors.getEnforcePbs());
    }

    private static ExtRequestPrebidFloors getFloors(BidRequest bidRequest) {
        final ExtRequest ext = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getExt);
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(ext, ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
    }

    private static AuctionParticipation applyEnforcement(AuctionParticipation auctionParticipation,
                                                         AccountPriceFloorsConfig accountPriceFloorsConfig) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = ObjectUtil.getIfNotNull(bidderResponse, BidderResponse::getSeatBid);
        final List<BidderBid> bidderBids = ObjectUtil.getIfNotNull(seatBid, BidderSeatBid::getBids);
        if (CollectionUtils.isEmpty(bidderBids)) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids);
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());

        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final BidRequest bidRequest = ObjectUtil.getIfNotNull(bidderRequest, BidderRequest::getBidRequest);
        final List<Imp> imps = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getImp);
        final ExtRequestPrebidFloors floors = getFloors(bidRequest);
        final boolean enforcedDealFloors = isEnforcedDealFloors(floors, accountPriceFloorsConfig);

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            final Imp imp = correspondingImp(bid, ListUtils.emptyIfNull(imps));

            final BigDecimal price = bid.getPrice();
            final BigDecimal floor = imp.getBidfloor();

            // skip enforcement for deals if not allowed
            if (StringUtils.isNotEmpty(bid.getDealid()) && !enforcedDealFloors) {
                continue;
            }

            if (!isAllowedByEnforceRate(floors, accountPriceFloorsConfig)) {
                continue;
            }

            if (isPriceBelowFloor(price, floor)) {
                errors.add(BidderError.generic(
                        String.format("Bid with id '%s' was rejected: price %s is below the floor %s",
                                bid.getId(), price, floor)));

                // TODO: create a record for analytics adapters to be aware of this rejection and reason

                updatedBidderBids.remove(bidderBid);
            }
        }

        if (bidderBids.size() == updatedBidderBids.size()) {
            return auctionParticipation;
        }

        final BidderResponse resultBidderResponse =
                bidderResponse.with(BidderSeatBid.of(updatedBidderBids, seatBid.getHttpCalls(), errors));
        return auctionParticipation.with(resultBidderResponse);
    }

    private static boolean isAllowedByEnforceRate(ExtRequestPrebidFloors floors,
                                                  AccountPriceFloorsConfig accountPriceFloorsConfig) {

        final Integer requestEnforceRate = floors.getEnforceRate();
        final Integer accountEnforceRate = accountPriceFloorsConfig.getEnforceFloorsRate();
        final Integer enforceRate = ObjectUtils.defaultIfNull(requestEnforceRate, accountEnforceRate);

        return (enforceRate == null || enforceRate < 0 || enforceRate > 100)
                || ThreadLocalRandom.current().nextDouble() < enforceRate / 100.0;
    }

    private static boolean isEnforcedDealFloors(ExtRequestPrebidFloors floors,
                                                AccountPriceFloorsConfig accountPriceFloorsConfig) {

        final Boolean requestEnforceDealFloors = floors.getEnforceDeals();
        final Boolean accountEnforceDealFloors = accountPriceFloorsConfig.getEnforceDealFloors();

        return BooleanUtils.isTrue(requestEnforceDealFloors) && BooleanUtils.isTrue(accountEnforceDealFloors);
    }

    private static Imp correspondingImp(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        return imps.stream()
                .filter(imp -> Objects.equals(impId, imp.getId()))
                .findFirst()
                // Should never occur. See ResponseBidValidator
                .orElseThrow(() -> new PreBidException(
                        String.format("Bid with impId %s doesn't have matched imp", impId)));
    }

    private static boolean isPriceBelowFloor(BigDecimal price, BigDecimal bidFloor) {
        return bidFloor != null && price.compareTo(bidFloor) < 0;
    }
}
