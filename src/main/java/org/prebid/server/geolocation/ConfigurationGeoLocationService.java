package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.geolocation.model.GeoInfoConfiguration;

import java.util.List;
import java.util.Objects;

public class ConfigurationGeoLocationService implements GeoLocationService {

    public static final String VENDOR = "configuration";

    private final List<GeoInfoConfiguration> configurations;

    public ConfigurationGeoLocationService(List<GeoInfoConfiguration> configurations) {
        this.configurations = Objects.requireNonNull(configurations);
    }

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        return configurations.stream()
                .filter(config -> matches(config, ip))
                .findFirst()
                .map(GeoInfoConfiguration::getGeoInfo)
                .map(ConfigurationGeoLocationService::specifyVendor)
                .map(Future::succeededFuture)
                .orElse(Future.failedFuture(
                        ConfigurationGeoLocationService.class.getSimpleName() + ": Geo location lookup failed."));
    }

    private static boolean matches(GeoInfoConfiguration configuration, String ip) {
        return ip != null && ip.startsWith(configuration.getAddressPattern());
    }

    private static GeoInfo specifyVendor(GeoInfo geoInfo) {
        return geoInfo != null ? geoInfo.toBuilder().vendor(VENDOR).build() : null;
    }
}
