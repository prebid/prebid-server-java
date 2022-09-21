package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.Link;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;

public class ProtobufLinkMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.Link, Link> {

    private final ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Link, ProtobufExtensionType> extensionMapper;

    public ProtobufLinkMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Link, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public Link map(OpenRtb.NativeResponse.Link link) {
        return Link.of(
                link.getUrl(),
                link.getClicktrackersList(),
                link.getFallback(),
                extractExtension(extensionMapper, link));
    }
}
