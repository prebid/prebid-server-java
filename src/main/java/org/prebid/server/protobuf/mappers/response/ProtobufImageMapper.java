package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.ImageObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;

public class ProtobufImageMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> {

    private final ProtobufJsonExtensionMapper<
            OpenRtb.NativeResponse.Asset.Image,
            ProtobufExtensionType
            > extensionMapper;

    public ProtobufImageMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Image, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public ImageObject map(OpenRtb.NativeResponse.Asset.Image image) {
        return ImageObject.builder()
                .type(image.getType())
                .url(image.getUrl())
                .w(image.getW())
                .h(image.getH())
                .ext(extractExtension(extensionMapper, image))
                .build();
    }
}
