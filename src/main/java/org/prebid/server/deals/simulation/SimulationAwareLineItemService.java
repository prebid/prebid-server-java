package org.prebid.server.deals.simulation;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.TargetingService;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.util.HttpUtil;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;

public class SimulationAwareLineItemService extends LineItemService {

    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";

    public SimulationAwareLineItemService(int maxDealsPerBidder,
                                          TargetingService targetingService,
                                          CurrencyConversionService conversionService,
                                          ApplicationEventService applicationEventService,
                                          @Value("${auction.ad-server-currency}}") String adServerCurrency,
                                          Clock clock,
                                          CriteriaLogManager criteriaLogManager) {

        super(
                maxDealsPerBidder,
                targetingService,
                conversionService,
                applicationEventService,
                adServerCurrency,
                clock,
                criteriaLogManager);
    }

    @Override
    public boolean accountHasDeals(AuctionContext auctionContext) {
        return accountHasDeals(
                auctionContext.getAccount().getId(),
                HttpUtil.getDateFromHeader(auctionContext.getHttpRequest().getHeaders(), PG_SIM_TIMESTAMP));
    }

    @Override
    public MatchLineItemsResult findMatchingLineItems(BidRequest bidRequest,
                                                      Imp imp,
                                                      String bidder,
                                                      BidderAliases aliases,
                                                      AuctionContext auctionContext) {

        return findMatchingLineItems(
                bidRequest,
                imp,
                bidder,
                aliases,
                auctionContext,
                HttpUtil.getDateFromHeader(auctionContext.getHttpRequest().getHeaders(), PG_SIM_TIMESTAMP));
    }
}
