package org.prebid.server.protobuf.mappers.ntv.asset;

import com.iab.openrtb.request.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufNativeVideoMapper<ProtobufExtensionType>
        implements ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> {

    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ProtobufExtensionType> extensionMapper;

    public ProtobufNativeVideoMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Video map(VideoObject videoObject) {
        final OpenRtb.BidRequest.Imp.Video.Builder resultBuilder = OpenRtb.BidRequest.Imp.Video.newBuilder();

        setNotNull(videoObject.getMimes(), resultBuilder::addAllMimes);
        setNotNull(videoObject.getMinduration(), resultBuilder::setMinduration);
        setNotNull(videoObject.getMaxduration(), resultBuilder::setMaxduration);
        setNotNull(videoObject.getProtocols(), resultBuilder::addAllProtocols);

        mapAndSetExtension(extensionMapper, videoObject.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
