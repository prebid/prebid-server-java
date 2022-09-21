package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufMapper;

public class ProtobufVideoMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> {

    @Override
    public VideoObject map(OpenRtb.NativeResponse.Asset.Video videoObject) {
        return VideoObject.builder()
                .vasttag(videoObject.getVasttag())
                .build();
    }
}

