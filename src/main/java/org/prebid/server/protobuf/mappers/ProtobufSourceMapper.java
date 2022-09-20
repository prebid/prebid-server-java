package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Source;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufSourceMapper<ProtobufExtensionType>
        implements ProtobufMapper<Source, OpenRtb.BidRequest.Source> {

    private final ProtobufExtensionMapper<OpenRtb.BidRequest.Source, ExtSource, ProtobufExtensionType> extensionMapper;

    public ProtobufSourceMapper(
            ProtobufExtensionMapper<OpenRtb.BidRequest.Source, ExtSource, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Source map(Source source) {
        final OpenRtb.BidRequest.Source.Builder resultBuilder = OpenRtb.BidRequest.Source.newBuilder();

        setNotNull(mapNotNull(source.getFd(), BooleanUtils::toBoolean), resultBuilder::setFd);
        setNotNull(source.getTid(), resultBuilder::setTid);
        setNotNull(source.getPchain(), resultBuilder::setPchain);

        mapAndSetExtension(extensionMapper, source.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
