package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.DataObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;

public class ProtobufDataMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> {

    private final ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Data, ProtobufExtensionType> extensionMapper;

    public ProtobufDataMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Data, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public DataObject map(OpenRtb.NativeResponse.Asset.Data dataObject) {
        return DataObject.builder()
                .type(dataObject.getType())
                .len(dataObject.getLen())
                .value(dataObject.getValue())
                .ext(extractExtension(extensionMapper, dataObject))
                .build();
    }
}
