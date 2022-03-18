package org.prebid.server.privacy.gdpr.model;

import com.iabtcf.decoder.TCString;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.geolocation.model.GeoInfo;

import java.util.Collections;
import java.util.List;

/**
 * Internal class for holding TCF information.
 */
@Builder
@Value
public class TcfContext {

    String gdpr;

    String consentString;

    TCString consent;

    Boolean isConsentValid;

    GeoInfo geoInfo;

    Boolean inEea;

    String ipAddress;

    List<String> warnings;

    public static TcfContext empty() {
        return builder().warnings(Collections.emptyList()).build();
    }
}
