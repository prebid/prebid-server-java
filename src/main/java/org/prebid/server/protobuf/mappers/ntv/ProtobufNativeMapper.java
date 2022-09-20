package org.prebid.server.protobuf.mappers.ntv;

import com.iab.openrtb.request.Native;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufNativeMapper<ProtobufExtensionType>
        implements ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> {

    private final ProtobufMapper<String, OpenRtb.NativeRequest> nativeRequestMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Native, ProtobufExtensionType> extensionMapper;

    public ProtobufNativeMapper(
            ProtobufMapper<String, OpenRtb.NativeRequest> nativeRequestMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Native, ProtobufExtensionType> extensionMapper) {

        this.nativeRequestMapper = Objects.requireNonNull(nativeRequestMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Native map(Native xNative) {
        final OpenRtb.BidRequest.Imp.Native.Builder resultBuilder = OpenRtb.BidRequest.Imp.Native.newBuilder();

        final OpenRtb.NativeRequest nativeRequest = nativeRequestMapper.map(xNative.getRequest());
        if (nativeRequest != null) {
            resultBuilder.setRequestNative(nativeRequest);
        } else {
            resultBuilder.setRequest(xNative.getRequest());
        }

        setNotNull(xNative.getRequest(), resultBuilder::setRequest);
        setNotNull(xNative.getVer(), resultBuilder::setVer);
        setNotNull(xNative.getApi(), resultBuilder::addAllApi);
        setNotNull(xNative.getBattr(), resultBuilder::addAllBattr);

        mapAndSetExtension(extensionMapper, xNative.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
