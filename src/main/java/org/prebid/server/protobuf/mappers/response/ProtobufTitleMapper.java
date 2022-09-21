package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.TitleObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;

public class ProtobufTitleMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> {

    private final ProtobufJsonExtensionMapper<
            OpenRtb.NativeResponse.Asset.Title,
            ProtobufExtensionType
            > extensionMapper;

    public ProtobufTitleMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Title, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public TitleObject map(OpenRtb.NativeResponse.Asset.Title title) {
        return TitleObject.builder()
                .text(title.getText())
                .len(title.getLen())
                .ext(extractExtension(extensionMapper, title))
                .build();
    }
}
