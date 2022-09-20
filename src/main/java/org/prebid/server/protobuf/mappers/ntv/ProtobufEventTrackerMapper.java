package org.prebid.server.protobuf.mappers.ntv;

import com.iab.openrtb.request.EventTracker;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufEventTrackerMapper<ProtobufExtensionType>
        implements ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> {

    private final JsonProtobufExtensionMapper<
            OpenRtb.NativeRequest.EventTrackers,
            ProtobufExtensionType
            > extensionMapper;

    public ProtobufEventTrackerMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.EventTrackers, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.NativeRequest.EventTrackers map(EventTracker eventTracker) {
        final OpenRtb.NativeRequest.EventTrackers.Builder resultBuilder =
                OpenRtb.NativeRequest.EventTrackers.newBuilder();

        setNotNull(eventTracker.getEvent(), resultBuilder::setEvent);
        setNotNull(eventTracker.getMethods(), resultBuilder::addAllMethods);

        mapAndSetExtension(extensionMapper, eventTracker.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
