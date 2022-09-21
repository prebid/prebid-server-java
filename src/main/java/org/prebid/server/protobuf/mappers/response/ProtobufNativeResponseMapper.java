package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.Asset;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.Response;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;

public class ProtobufNativeResponseMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse, Response> {

    private final ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> assetMapper;
    private final ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper;
    private final ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> eventTrackerMapper;
    private final ProtobufJsonExtensionMapper<OpenRtb.NativeResponse, ProtobufExtensionType> extensionMapper;

    public ProtobufNativeResponseMapper(
            ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> assetMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper,
            ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> eventTrackerMapper,
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse, ProtobufExtensionType> extensionMapper) {

        this.assetMapper = Objects.requireNonNull(assetMapper);
        this.linkMapper = Objects.requireNonNull(linkMapper);
        this.eventTrackerMapper = Objects.requireNonNull(eventTrackerMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public Response map(OpenRtb.NativeResponse response) {
        return Response.builder()
                .ver(response.getVer())
                .assets(mapList(response.getAssetsList(), assetMapper::map))
                .assetsurl(response.getAssetsurl())
                .dcourl(response.getDcourl())
                .link(mapNotNull(response.getLink(), linkMapper::map))
                .imptrackers(response.getImptrackersList())
                .jstracker(response.getJstracker())
                .eventtrackers(mapList(response.getEventtrackersList(), eventTrackerMapper::map))
                .privacy(response.getPrivacy())
                .ext(extractExtension(extensionMapper, response))
                .build();
    }
}
