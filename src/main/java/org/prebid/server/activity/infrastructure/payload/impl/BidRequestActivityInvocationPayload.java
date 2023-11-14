package org.prebid.server.activity.infrastructure.payload.impl;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;

import java.util.Optional;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidRequestActivityInvocationPayload implements GeoActivityInvocationPayload, GpcActivityInvocationPayload {

    @Delegate
    ActivityInvocationPayload componentInfo;

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

    @Override
    public String gpc() {
        return Optional.ofNullable(bidRequest.getRegs())
                .map(Regs::getExt)
                .map(ExtRegs::getGpc)
                .orElse(null);
    }
}
