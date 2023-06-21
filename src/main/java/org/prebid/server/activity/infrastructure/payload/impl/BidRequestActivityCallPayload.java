package org.prebid.server.activity.infrastructure.payload.impl;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityCallPayload;

import java.util.Optional;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidRequestActivityCallPayload implements GeoActivityCallPayload {

    @Delegate
    ActivityCallPayload componentInfo;

    BidRequest bidRequest;

    @Override
    public String country() {
        return Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .orElse(null);
    }

    @Override
    public String region() {
        return Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getGeo)
                .map(Geo::getRegion)
                .orElse(null);
    }
}
