package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Segment;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufDataMapper<ProtobufExtensionType>
        implements ProtobufMapper<Data, OpenRtb.BidRequest.Data> {

    private final ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> segmentMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data, ProtobufExtensionType> extensionMapper;

    public ProtobufDataMapper(
            ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> segmentMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data, ProtobufExtensionType> extensionMapper) {

        this.segmentMapper = Objects.requireNonNull(segmentMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Data map(Data data) {
        final OpenRtb.BidRequest.Data.Builder resultBuilder = OpenRtb.BidRequest.Data.newBuilder();

        setNotNull(data.getId(), resultBuilder::setId);
        setNotNull(data.getName(), resultBuilder::setName);
        setNotNull(mapList(data.getSegment(), segmentMapper::map), resultBuilder::addAllSegment);

        if (extensionMapper != null) {
            final ProtobufExtensionType ext = extensionMapper.map(data.getExt());
            resultBuilder.setExtension(extensionMapper.extensionType(), ext);
        }
        return resultBuilder.build();
    }
}
