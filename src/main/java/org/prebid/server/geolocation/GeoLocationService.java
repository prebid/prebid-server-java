package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;

/**
 * Retrieves geolocation information by IP address.
 * <p>
 * Used for GDPR processing, etc.
 * <p>
 * Provided default implementation - {@link MaxMindGeoLocationService}
 * Each vendor (host company) might provide its own implementation and inject it via Spring configuration.
 */
@FunctionalInterface
public interface GeoLocationService {

    /**
     * Returns geo location data by IP address.
     */
    Future<GeoInfo> lookup(String ip, Timeout timeout);
}
