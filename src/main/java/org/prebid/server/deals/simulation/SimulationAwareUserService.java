package org.prebid.server.deals.simulation;

import io.vertx.core.Future;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.UserService;
import org.prebid.server.deals.model.SimulationProperties;
import org.prebid.server.deals.model.UserDetails;
import org.prebid.server.deals.model.UserDetailsProperties;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;

import java.time.Clock;

public class SimulationAwareUserService extends UserService {

    private final boolean winEventsEnabled;
    private final boolean userDetailsEnabled;

    public SimulationAwareUserService(UserDetailsProperties userDetailsProperties,
                                      SimulationProperties simulationProperties,
                                      String dataCenterRegion,
                                      LineItemService lineItemService,
                                      HttpClient httpClient,
                                      Clock clock,
                                      Metrics metrics,
                                      JacksonMapper mapper) {
        super(
                userDetailsProperties,
                dataCenterRegion,
                lineItemService,
                httpClient,
                clock,
                metrics,
                mapper);

        this.winEventsEnabled = simulationProperties.isWinEventsEnabled();
        this.userDetailsEnabled = simulationProperties.isUserDetailsEnabled();
    }

    @Override
    public Future<UserDetails> getUserDetails(AuctionContext context, Timeout timeout) {
        return userDetailsEnabled
                ? super.getUserDetails(context, timeout)
                : Future.succeededFuture(UserDetails.empty());
    }

    @Override
    public void processWinEvent(String lineItemId, String bidId, UidsCookie uids) {
        if (winEventsEnabled) {
            super.processWinEvent(lineItemId, bidId, uids);
        }
    }
}
