package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.DataObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufDataObjectMapper<ProtobufExtensionType>
        implements ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> {

    private final JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Data, ProtobufExtensionType> extensionMapper;

    public ProtobufDataObjectMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Data, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.NativeRequest.Asset.Data map(DataObject dataObject) {
        final OpenRtb.NativeRequest.Asset.Data.Builder resultBuilder = OpenRtb.NativeRequest.Asset.Data.newBuilder();

        setNotNull(dataObject.getType(), resultBuilder::setType);
        setNotNull(dataObject.getLen(), resultBuilder::setLen);

        mapAndSetExtension(extensionMapper, dataObject.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
