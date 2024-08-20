package org.prebid.server.activity.infrastructure.payload;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.impl.ComponentActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.GeoActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.GpcActivityInvocationPayloadImpl;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ActivityInvocationPayloadBuilder {

    private final Map<Type, ActivityInvocationPayload> payloads;

    public ActivityInvocationPayloadBuilder() {
        payloads = new HashMap<>();
    }

    public ActivityInvocationPayloadBuilder component(ComponentType componentType, String componentName) {
        payloads.put(Type.COMPONENT, ComponentActivityInvocationPayloadImpl.of(componentType, componentName));
        return this;
    }

    public ActivityInvocationPayloadBuilder geo(String country, String region) {
        payloads.put(Type.GEO, GeoActivityInvocationPayloadImpl.of(country, region));
        return this;
    }

    public ActivityInvocationPayloadBuilder gpc(String gpc) {
        payloads.put(Type.GPC, GpcActivityInvocationPayloadImpl.of(gpc));
        return this;
    }

    public ActivityInvocationPayloadBuilder forBidRequest(BidRequest bidRequest) {
        final Optional<BidRequest> optionalBidRequest = Optional.ofNullable(bidRequest);
        final Optional<Device> device = optionalBidRequest.map(BidRequest::getDevice);
        return this
                .geo(
                        device
                                .map(Device::getGeo)
                                .map(Geo::getCountry)
                                .orElse(null),
                        device
                                .map(Device::getGeo)
                                .map(Geo::getRegion)
                                .orElse(null))
                .gpc(optionalBidRequest
                        .map(BidRequest::getRegs)
                        .map(Regs::getExt)
                        .map(ExtRegs::getGpc)
                        .orElse(null));
    }

    public ActivityInvocationPayloadBuilder forTcfContext(TcfContext tcfContext) {
        final Optional<GeoInfo> geoInfo = Optional.ofNullable(tcfContext).map(TcfContext::getGeoInfo);
        return this.geo(
                geoInfo.map(GeoInfo::getCountry).orElse(null),
                geoInfo.map(GeoInfo::getRegion).orElse(null));
    }

    public ActivityInvocationPayload build() {
        return new CompositeActivityInvocationPayload(payloads.values());
    }

    public enum Type {

        COMPONENT, GEO, GPC
    }
}
