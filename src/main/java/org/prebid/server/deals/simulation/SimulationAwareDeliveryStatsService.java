package org.prebid.server.deals.simulation;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.prebid.server.deals.AlertHttpService;
import org.prebid.server.deals.DeliveryProgressReportFactory;
import org.prebid.server.deals.DeliveryStatsService;
import org.prebid.server.deals.model.DeliveryStatsProperties;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class SimulationAwareDeliveryStatsService extends DeliveryStatsService {

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";

    public SimulationAwareDeliveryStatsService(DeliveryStatsProperties deliveryStatsProperties,
                                               DeliveryProgressReportFactory deliveryProgressReportFactory,
                                               AlertHttpService alertHttpService,
                                               HttpClient httpClient,
                                               Metrics metrics,
                                               Clock clock,
                                               Vertx vertx,
                                               JacksonMapper mapper) {
        super(deliveryStatsProperties,
                deliveryProgressReportFactory,
                alertHttpService,
                httpClient,
                metrics,
                clock,
                vertx,
                mapper);
    }

    @Override
    protected Future<Void> sendReport(DeliveryProgressReport deliveryProgressReport, MultiMap headers,
                                      ZonedDateTime now) {
        return super.sendReport(deliveryProgressReport,
                headers().add(PG_SIM_TIMESTAMP, UTC_MILLIS_FORMATTER.format(now)), now);
    }
}
