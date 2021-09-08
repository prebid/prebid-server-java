package org.prebid.server.deals.simulation;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.AlertHttpService;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.RegisterService;
import org.prebid.server.deals.events.AdminEventService;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.health.HealthMonitor;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.http.HttpClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class SimulationAwareRegisterService extends RegisterService {

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();
    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";

    public SimulationAwareRegisterService(PlannerProperties plannerProperties,
                                          DeploymentProperties deploymentProperties,
                                          AdminEventService adminEventService,
                                          DeliveryProgressService deliveryProgressService,
                                          AlertHttpService alertHttpService,
                                          HealthMonitor healthMonitor,
                                          CurrencyConversionService currencyConversionService,
                                          HttpClient httpClient,
                                          Vertx vertx,
                                          JacksonMapper mapper) {
        super(plannerProperties,
                deploymentProperties,
                adminEventService,
                deliveryProgressService,
                alertHttpService,
                healthMonitor,
                currencyConversionService,
                httpClient,
                vertx,
                mapper);
    }

    @Override
    public void initialize() {
        // disable timer initialization for simulation mode
    }

    public void performRegistration(ZonedDateTime now) {
        register(headers(now));
    }

    private MultiMap headers(ZonedDateTime now) {
        return headers().add(PG_SIM_TIMESTAMP, UTC_MILLIS_FORMATTER.format(now));
    }
}
