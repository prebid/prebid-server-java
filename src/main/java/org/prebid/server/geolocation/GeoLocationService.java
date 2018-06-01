package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.prebid.server.geolocation.model.GeoInfo;

/**
 * Retrieves geolocation information by IP address.
 * <p>
 * Used for GDPR processing, etc.
 * <p>
 * There is no default implementation for this interface, so turned off initially.
 * Each vendor (host company) should provide its own and inject it via Spring configuration.
 */
public interface GeoLocationService {

    /**
     * Returns geo location data by IP address.
     */
    Future<GeoInfo> lookup(String ip);
}
