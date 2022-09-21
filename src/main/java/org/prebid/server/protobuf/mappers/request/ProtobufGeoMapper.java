package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Geo;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufGeoMapper<ProtobufExtensionType>
        implements ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> {

    private final ProtobufExtensionMapper<OpenRtb.BidRequest.Geo, ExtGeo, ProtobufExtensionType> extensionMapper;

    public ProtobufGeoMapper(
            ProtobufExtensionMapper<OpenRtb.BidRequest.Geo, ExtGeo, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Geo map(Geo geo) {
        final OpenRtb.BidRequest.Geo.Builder resultBuilder = OpenRtb.BidRequest.Geo.newBuilder();

        setNotNull(mapNotNull(geo.getLat(), Float::doubleValue), resultBuilder::setLat);
        setNotNull(mapNotNull(geo.getLon(), Float::doubleValue), resultBuilder::setLon);
        setNotNull(geo.getType(), resultBuilder::setType);
        setNotNull(geo.getAccuracy(), resultBuilder::setAccuracy);
        setNotNull(geo.getLastfix(), resultBuilder::setLastfix);
        setNotNull(geo.getIpservice(), resultBuilder::setIpservice);
        setNotNull(geo.getCountry(), resultBuilder::setCountry);
        setNotNull(geo.getRegion(), resultBuilder::setRegion);
        setNotNull(geo.getRegionfips104(), resultBuilder::setRegionfips104);
        setNotNull(geo.getMetro(), resultBuilder::setMetro);
        setNotNull(geo.getCity(), resultBuilder::setCity);
        setNotNull(geo.getZip(), resultBuilder::setZip);
        setNotNull(geo.getUtcoffset(), resultBuilder::setUtcoffset);

        mapAndSetExtension(extensionMapper, geo.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
