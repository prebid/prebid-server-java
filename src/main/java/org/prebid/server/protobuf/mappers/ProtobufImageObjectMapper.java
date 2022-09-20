package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.ImageObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufImageObjectMapper<ProtobufExtensionType>
        implements ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> {

    private final JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Image, ProtobufExtensionType> extensionMapper;

    public ProtobufImageObjectMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Image, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.NativeRequest.Asset.Image map(ImageObject imageObject) {
        final OpenRtb.NativeRequest.Asset.Image.Builder resultBuilder = OpenRtb.NativeRequest.Asset.Image.newBuilder();

        setNotNull(imageObject.getType(), resultBuilder::setType);
        setNotNull(imageObject.getW(), resultBuilder::setW);
        setNotNull(imageObject.getWmin(), resultBuilder::setWmin);
        setNotNull(imageObject.getH(), resultBuilder::setH);
        setNotNull(imageObject.getHmin(), resultBuilder::setHmin);
        setNotNull(imageObject.getMimes(), resultBuilder::addAllMimes);

        mapAndSetExtension(extensionMapper, imageObject.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
