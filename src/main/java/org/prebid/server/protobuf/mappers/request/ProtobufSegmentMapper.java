package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Segment;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufSegmentMapper<ProtobufExtensionType>
        implements ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> {

    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data.Segment, ProtobufExtensionType> extensionMapper;

    public ProtobufSegmentMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data.Segment, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Data.Segment map(Segment segment) {
        final OpenRtb.BidRequest.Data.Segment.Builder resultBuilder = OpenRtb.BidRequest.Data.Segment.newBuilder();

        setNotNull(segment.getId(), resultBuilder::setId);
        setNotNull(segment.getName(), resultBuilder::setName);
        setNotNull(segment.getValue(), resultBuilder::setValue);

        mapAndSetExtension(extensionMapper, segment.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
