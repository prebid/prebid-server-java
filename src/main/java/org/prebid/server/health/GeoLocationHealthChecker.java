package org.prebid.server.health;

import io.vertx.core.Vertx;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.health.model.Status;
import org.prebid.server.health.model.StatusResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

public class GeoLocationHealthChecker extends PeriodicHealthChecker {

    private static final String NAME = "geolocation";
    private static final String PREBID_ORG_IP = "185.199.111.153";
    private static final Long TIMEOUT_MILLIS = 1000L;

    private final GeoLocationService geoLocationService;
    private final TimeoutFactory timeoutFactory;
    private final Clock clock;

    private StatusResponse status;

    public GeoLocationHealthChecker(Vertx vertx,
                                    long refreshPeriod,
                                    GeoLocationService geoLocationService,
                                    TimeoutFactory timeoutFactory,
                                    Clock clock) {

        super(vertx, refreshPeriod);
        this.geoLocationService = Objects.requireNonNull(geoLocationService);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    void updateStatus() {
        geoLocationService.lookup(PREBID_ORG_IP, timeoutFactory.create(TIMEOUT_MILLIS))
                .setHandler(result ->
                        status = StatusResponse.of(
                                result.succeeded() ? Status.UP.name() : Status.DOWN.name(),
                                ZonedDateTime.now(clock)));
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StatusResponse status() {
        return status;
    }
}
