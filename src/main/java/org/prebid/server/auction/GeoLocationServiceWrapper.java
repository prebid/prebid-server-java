package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountSettings;

import java.util.Objects;
import java.util.Optional;

public class GeoLocationServiceWrapper {

    private static final Logger logger = LoggerFactory.getLogger(GeoLocationServiceWrapper.class);

    private final GeoLocationService geoLocationService;
    private final Metrics metrics;

    public GeoLocationServiceWrapper(GeoLocationService geoLocationService, Metrics metrics) {
        this.geoLocationService = geoLocationService;
        this.metrics = Objects.requireNonNull(metrics);
    }

    //todo: account settings will work as expected if the default account resolving refactoring is done
    public Future<GeoInfo> lookup(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Timeout timeout = auctionContext.getTimeoutContext().getTimeout();

        final boolean isGeoLookupEnabled = Optional.ofNullable(account.getSettings())
                .map(AccountSettings::getGeoLookup)
                .map(BooleanUtils::isTrue)
                .orElse(false);

        final Device device = bidRequest.getDevice();

        return isGeoLookupEnabled
                ? doLookup(getIpAddress(device), getCountry(device), timeout).otherwiseEmpty()
                : Future.succeededFuture();
    }

    public Future<GeoInfo> doLookup(String ipAddress, String requestCountry, Timeout timeout) {
        if (geoLocationService == null || StringUtils.isNotBlank(requestCountry) || ipAddress == null) {
            return Future.failedFuture("Geolocation lookup is skipped");
        }
        return geoLocationService.lookup(ipAddress, timeout)
                .onSuccess(geoInfo -> metrics.updateGeoLocationMetric(true))
                .onFailure(this::logError);
    }

    private String getCountry(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }

    private String getIpAddress(Device device) {
        final Optional<Device> optionalDevice = Optional.ofNullable(device);
        return optionalDevice.map(Device::getIp)
                .filter(StringUtils::isNotBlank)
                .or(() -> optionalDevice
                        .map(Device::getIpv6)
                        .filter(StringUtils::isNotBlank))
                .orElse(null);
    }

    private void logError(Throwable error) {
        final String message = "Geolocation lookup failed: " + error.getMessage();
        logger.warn(message);
        logger.debug(message, error);

        metrics.updateGeoLocationMetric(false);
    }
}
