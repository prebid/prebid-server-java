package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Format;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufFormatMapper<ProtobufExtensionType>
        implements ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> {

    private final JsonProtobufExtensionMapper<
            OpenRtb.BidRequest.Imp.Banner.Format,
            ProtobufExtensionType
            > extensionMapper;

    public ProtobufFormatMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner.Format, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Banner.Format map(Format format) {
        final OpenRtb.BidRequest.Imp.Banner.Format.Builder resultBuilder =
                OpenRtb.BidRequest.Imp.Banner.Format.newBuilder();

        setNotNull(format.getW(), resultBuilder::setW);
        setNotNull(format.getH(), resultBuilder::setH);
        setNotNull(format.getWratio(), resultBuilder::setWratio);
        setNotNull(format.getHratio(), resultBuilder::setHratio);
        setNotNull(format.getWmin(), resultBuilder::setWmin);

        mapAndSetExtension(extensionMapper, format.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
