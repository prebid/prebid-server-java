package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Optional;

public class NoSignalBidderPriceFloorAdjuster implements PriceFloorAdjuster {

    private static final String ALL_BIDDERS = "*";

    private final PriceFloorAdjuster delegate;

    public NoSignalBidderPriceFloorAdjuster(PriceFloorAdjuster delegate) {
        this.delegate = delegate;
    }

    @Override
    public Price adjustForImp(Imp imp,
                              String bidder,
                              BidRequest bidRequest,
                              Account account,
                              List<String> debugWarnings) {

        final Optional<PriceFloorRules> optionalFloors = Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getFloors);

        final Boolean shouldSkip = optionalFloors
                .map(floors -> BooleanUtils.isFalse(floors.getEnabled()) || BooleanUtils.isTrue(floors.getSkipped()))
                .orElse(false);

        if (shouldSkip) {
            return delegate.adjustForImp(imp, bidder, bidRequest, account, debugWarnings);
        }

        return optionalFloors
                .map(PriceFloorRules::getData)
                .map(PriceFloorData::getModelGroups)
                .filter(CollectionUtils::isNotEmpty)
                .map(List::getFirst)
                .map(PriceFloorModelGroup::getNoFloorSignalBidders)
                .or(() -> optionalFloors
                        .map(PriceFloorRules::getData)
                        .map(PriceFloorData::getNoFloorSignalBidders))
                .or(() -> optionalFloors
                        .map(PriceFloorRules::getEnforcement)
                        .map(PriceFloorEnforcement::getNoFloorSignalBidders))
                .filter(noSignalBidders -> isNoSignalBidder(bidder, noSignalBidders))
                .map(ignored -> {
                    debugWarnings.add("noFloorSignal to bidder " + bidder);
                    return Price.empty();
                })
                .orElseGet(() -> delegate.adjustForImp(imp, bidder, bidRequest, account, debugWarnings));
    }

    @Override
    public Price revertAdjustmentForImp(Imp imp, String bidder, BidRequest bidRequest, Account account) {
        return delegate.revertAdjustmentForImp(imp, bidder, bidRequest, account);
    }

    private static boolean isNoSignalBidder(String bidder, List<String> noSignalBidders) {
        return noSignalBidders.stream().anyMatch(noSignalBidder -> StringUtils.equalsIgnoreCase(noSignalBidder, bidder))
                || noSignalBidders.contains(ALL_BIDDERS);
    }
}
