package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufUserMapper<ProtobufExtensionType>
        implements ProtobufMapper<User, OpenRtb.BidRequest.User> {

    private final ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper;
    private final ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper;
    private final ProtobufExtensionMapper<OpenRtb.BidRequest.User, ExtUser, ProtobufExtensionType> extensionMapper;

    public ProtobufUserMapper(
            ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper,
            ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper,
            ProtobufExtensionMapper<OpenRtb.BidRequest.User, ExtUser, ProtobufExtensionType> extensionMapper) {

        this.geoMapper = Objects.requireNonNull(geoMapper);
        this.dataMapper = Objects.requireNonNull(dataMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.User map(User user) {
        final OpenRtb.BidRequest.User.Builder resultBuilder = OpenRtb.BidRequest.User.newBuilder();

        setNotNull(user.getId(), resultBuilder::setId);
        setNotNull(user.getBuyeruid(), resultBuilder::setBuyeruid);
        setNotNull(user.getYob(), resultBuilder::setYob);
        setNotNull(user.getGender(), resultBuilder::setGender);
        setNotNull(user.getKeywords(), resultBuilder::setKeywords);
        setNotNull(user.getCustomdata(), resultBuilder::setCustomdata);
        setNotNull(mapNotNull(user.getGeo(), geoMapper::map), resultBuilder::setGeo);
        setNotNull(mapList(user.getData(), dataMapper::map), resultBuilder::addAllData);

        mapAndSetExtension(extensionMapper, user.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
