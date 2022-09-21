package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.EventTracker;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;

public class ProtobufEventTrackerMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> {

    private final ProtobufJsonExtensionMapper<
            OpenRtb.NativeResponse.EventTracker,
            ProtobufExtensionType
            > extensionMapper;

    public ProtobufEventTrackerMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.EventTracker, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public EventTracker map(OpenRtb.NativeResponse.EventTracker eventTracker) {
        return EventTracker.builder()
                .event(eventTracker.getEvent())
                .method(eventTracker.getMethod())
                .url(eventTracker.getUrl())
                .ext(extractExtension(extensionMapper, eventTracker))
                .build();
    }
}
