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
@Value
@Builder(toBuilder = true)
public class TcfContext {

    boolean inGdprScope;

    String consentString;

    TCString consent;

    boolean consentValid;

    GeoInfo geoInfo;

    Boolean inEea;

    String ipAddress;

    List<String> warnings;

    public static TcfContext empty() {
        return builder().warnings(Collections.emptyList()).build();
    }
}
