package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Pmp;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufPmpMapper<ProtobufExtensionType>
        implements ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> {

    private final ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> dealsMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp, ProtobufExtensionType> extensionMapper;

    public ProtobufPmpMapper(
            ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> dealsMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp, ProtobufExtensionType> extensionMapper) {

        this.dealsMapper = Objects.requireNonNull(dealsMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Pmp map(Pmp pmp) {
        final OpenRtb.BidRequest.Imp.Pmp.Builder resultBuilder = OpenRtb.BidRequest.Imp.Pmp.newBuilder();

        setNotNull(mapNotNull(pmp.getPrivateAuction(), BooleanUtils::toBoolean), resultBuilder::setPrivateAuction);
        setNotNull(mapList(pmp.getDeals(), dealsMapper::map), resultBuilder::addAllDeals);

        mapAndSetExtension(extensionMapper, pmp.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
