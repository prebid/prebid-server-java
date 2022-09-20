package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;
import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufDeviceMapper<ProtobufExtensionType>
        implements ProtobufMapper<Device, OpenRtb.BidRequest.Device> {

    private final ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper;
    private final ProtobufExtensionMapper<OpenRtb.BidRequest.Device, ExtDevice, ProtobufExtensionType> extensionMapper;

    public ProtobufDeviceMapper(
            ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper,
            ProtobufExtensionMapper<OpenRtb.BidRequest.Device, ExtDevice, ProtobufExtensionType> extensionMapper) {

        this.geoMapper = Objects.requireNonNull(geoMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Device map(Device device) {
        final OpenRtb.BidRequest.Device.Builder resultBuilder = OpenRtb.BidRequest.Device.newBuilder();

        setNotNull(mapNotNull(device.getGeo(), geoMapper::map), resultBuilder::setGeo);
        setNotNull(mapNotNull(device.getDnt(), BooleanUtils::toBoolean), resultBuilder::setDnt);
        setNotNull(mapNotNull(device.getLmt(), BooleanUtils::toBoolean), resultBuilder::setLmt);
        setNotNull(device.getUa(), resultBuilder::setUa);
        setNotNull(device.getIp(), resultBuilder::setIp);
        setNotNull(device.getIpv6(), resultBuilder::setIpv6);
        setNotNull(device.getDevicetype(), resultBuilder::setDevicetype);
        setNotNull(device.getMake(), resultBuilder::setMake);
        setNotNull(device.getModel(), resultBuilder::setModel);
        setNotNull(device.getOs(), resultBuilder::setOs);
        setNotNull(device.getOsv(), resultBuilder::setOsv);
        setNotNull(device.getHwv(), resultBuilder::setHwv);
        setNotNull(device.getH(), resultBuilder::setH);
        setNotNull(device.getW(), resultBuilder::setW);
        setNotNull(device.getPpi(), resultBuilder::setPpi);
        setNotNull(mapNotNull(device.getPxratio(), BigDecimal::doubleValue), resultBuilder::setPxratio);
        setNotNull(mapNotNull(device.getJs(), BooleanUtils::toBoolean), resultBuilder::setJs);
        setNotNull(mapNotNull(device.getGeofetch(), BooleanUtils::toBoolean), resultBuilder::setGeofetch);
        setNotNull(device.getFlashver(), resultBuilder::setFlashver);
        setNotNull(device.getLanguage(), resultBuilder::setLanguage);
        setNotNull(device.getCarrier(), resultBuilder::setCarrier);
        setNotNull(device.getMccmnc(), resultBuilder::setMccmnc);
        setNotNull(device.getConnectiontype(), resultBuilder::setConnectiontype);
        setNotNull(device.getIfa(), resultBuilder::setIfa);
        setNotNull(device.getDidsha1(), resultBuilder::setDidsha1);
        setNotNull(device.getDidmd5(), resultBuilder::setDidmd5);
        setNotNull(device.getDpidsha1(), resultBuilder::setDpidsha1);
        setNotNull(device.getDpidmd5(), resultBuilder::setDpidmd5);
        setNotNull(device.getMacsha1(), resultBuilder::setMacsha1);
        setNotNull(device.getMacmd5(), resultBuilder::setMacmd5);

        mapAndSetExtension(extensionMapper, device.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
