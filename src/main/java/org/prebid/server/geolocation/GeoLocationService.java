package org.prebid.server.geolocation;

import io.vertx.core.Future;

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
     * Returns country code in ISO-3166 (https://www.iso.org/glossary-for-iso-3166.html) by IP address.
     */
    Future<String> lookup(String ip);
}
