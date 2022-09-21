package org.prebid.server.protobuf.mappers.request.ntv.asset;

import com.iab.openrtb.request.TitleObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufTitleMapper<ProtobufExtensionType>
        implements ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> {

    private final JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Title, ProtobufExtensionType> extensionMapper;

    public ProtobufTitleMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Title, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.NativeRequest.Asset.Title map(TitleObject titleObject) {
        final OpenRtb.NativeRequest.Asset.Title.Builder resultBuilder = OpenRtb.NativeRequest.Asset.Title.newBuilder();

        setNotNull(titleObject.getLen(), resultBuilder::setLen);

        mapAndSetExtension(extensionMapper, titleObject.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
