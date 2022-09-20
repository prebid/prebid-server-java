package org.prebid.server.protobuf.mappers.ntv;

import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Request;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufNativeRequestMapper<ProtobufExtensionType>
        implements ProtobufMapper<Request, OpenRtb.NativeRequest> {

    private final ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> assetsMapper;
    private final ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> eventtrackersMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.NativeRequest, ProtobufExtensionType> extensionMapper;

    public ProtobufNativeRequestMapper(
            ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> assetsMapper,
            ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> eventtrackersMapper,
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest, ProtobufExtensionType> extensionMapper) {

        this.assetsMapper = Objects.requireNonNull(assetsMapper);
        this.eventtrackersMapper = Objects.requireNonNull(eventtrackersMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.NativeRequest map(Request request) {
        final OpenRtb.NativeRequest.Builder resultBuilder = OpenRtb.NativeRequest.newBuilder();

        setNotNull(request.getVer(), resultBuilder::setVer);
        setNotNull(request.getContext(), resultBuilder::setContext);
        setNotNull(request.getContextsubtype(), resultBuilder::setContextsubtype);
        setNotNull(request.getPlcmttype(), resultBuilder::setPlcmttype);
        setNotNull(request.getPlcmtcnt(), resultBuilder::setPlcmtcnt);
        setNotNull(request.getSeq(), resultBuilder::setSeq);
        setNotNull(mapList(request.getAssets(), assetsMapper::map), resultBuilder::addAllAssets);
        setNotNull(mapNotNull(request.getAurlsupport(), BooleanUtils::toBoolean), resultBuilder::setAurlsupport);
        setNotNull(mapNotNull(request.getDurlsupport(), BooleanUtils::toBoolean), resultBuilder::setDurlsupport);
        setNotNull(mapList(request.getEventtrackers(), eventtrackersMapper::map), resultBuilder::addAllEventtrackers);
        setNotNull(mapNotNull(request.getPrivacy(), BooleanUtils::toBoolean), resultBuilder::setPrivacy);

        mapAndSetExtension(extensionMapper, request.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
