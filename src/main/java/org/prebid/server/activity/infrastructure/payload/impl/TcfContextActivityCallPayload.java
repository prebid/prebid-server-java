package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityCallPayload;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.privacy.gdpr.model.TcfContext;

import java.util.Optional;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class TcfContextActivityCallPayload implements GeoActivityCallPayload {

    @Delegate
    ActivityCallPayload componentInfo;

    TcfContext tcfContext;

    @Override
    public String country() {
        return Optional.ofNullable(tcfContext)
                .map(TcfContext::getGeoInfo)
                .map(GeoInfo::getCountry)
                .orElse(null);
    }

    @Override
    public String region() {
        return Optional.ofNullable(tcfContext)
                .map(TcfContext::getGeoInfo)
                .map(GeoInfo::getRegion)
                .orElse(null);
    }
}
