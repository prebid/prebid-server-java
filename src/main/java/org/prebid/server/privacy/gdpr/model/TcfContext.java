package org.prebid.server.privacy.gdpr.model;

import com.iabtcf.decoder.TCString;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.geolocation.model.GeoInfo;

/**
 * Internal class for holding TCF information.
 */
@Builder
@Value
public class TcfContext {

    String gdpr;

    String consentString;

    TCString consent;

    GeoInfo geoInfo;

    Boolean inEea;

    String ipAddress;

    public static TcfContext empty() {
        return builder().build();
    }
}
