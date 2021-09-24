package org.prebid.server.deals.simulation;

import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.DeliveryProgressReportFactory;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.DeliveryStatsService;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.DeliveryProgressProperties;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Map;

public class SimulationAwareDeliveryProgressService extends DeliveryProgressService {

    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";

    private long readyAtAdjustment;
    private volatile boolean firstReportUpdate;

    public SimulationAwareDeliveryProgressService(DeliveryProgressProperties deliveryProgressProperties,
                                                  LineItemService lineItemService,
                                                  DeliveryStatsService deliveryStatsService,
                                                  DeliveryProgressReportFactory deliveryProgressReportFactory,
                                                  long readyAtAdjustment,
                                                  Clock clock,
                                                  CriteriaLogManager criteriaLogManager) {

        super(
                deliveryProgressProperties,
                lineItemService,
                deliveryStatsService,
                deliveryProgressReportFactory,
                clock,
                criteriaLogManager);
        this.readyAtAdjustment = readyAtAdjustment;
        this.firstReportUpdate = true;
    }

    @Override
    public void shutdown() {
        // disable sending report during bean destroying process
    }

    @Override
    public void processAuctionEvent(AuctionContext auctionContext) {
        final ZonedDateTime now = HttpUtil.getDateFromHeader(auctionContext.getHttpRequest().getHeaders(),
                PG_SIM_TIMESTAMP);
        if (firstReportUpdate) {
            firstReportUpdate = false;
            updateDeliveryProgressesStartTime(now);
        }
        super.processAuctionEvent(auctionContext.getTxnLog(), auctionContext.getAccount().getId(), now);
    }

    protected void incrementTokens(LineItem lineItem, ZonedDateTime now, Map<String, Integer> planIdToTokenPriority) {
        final Integer classPriority = lineItem.incSpentToken(now, readyAtAdjustment);
        if (classPriority != null) {
            planIdToTokenPriority.put(lineItem.getActiveDeliveryPlan().getPlanId(), classPriority);
        }
    }

    private void updateDeliveryProgressesStartTime(ZonedDateTime now) {
        overallDeliveryProgress.setStartTimeStamp(now);
        currentDeliveryProgress.setStartTimeStamp(now);
    }

    void createDeliveryProgressReport(ZonedDateTime now) {
        createDeliveryProgressReports(now);
    }
}
