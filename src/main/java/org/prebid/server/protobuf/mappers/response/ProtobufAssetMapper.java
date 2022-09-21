package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.Asset;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.TitleObject;
import com.iab.openrtb.response.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;

public class ProtobufAssetMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> {

    private final ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> titleMapper;
    private final ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> imageMapper;
    private final ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> videoMapper;
    private final ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> dataMapper;
    private final ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper;
    private final ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset, ProtobufExtensionType> extensionMapper;

    public ProtobufAssetMapper(
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> titleMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> imageMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> videoMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> dataMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper,
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset, ProtobufExtensionType> extensionMapper) {

        this.titleMapper = Objects.requireNonNull(titleMapper);
        this.imageMapper = Objects.requireNonNull(imageMapper);
        this.videoMapper = Objects.requireNonNull(videoMapper);
        this.dataMapper = Objects.requireNonNull(dataMapper);
        this.linkMapper = Objects.requireNonNull(linkMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public Asset map(OpenRtb.NativeResponse.Asset asset) {
        return Asset.builder()
                .id(asset.getId())
                .required(BooleanUtils.toInteger(asset.getRequired()))
                .title(titleMapper.map(asset.getTitle()))
                .img(imageMapper.map(asset.getImg()))
                .video(videoMapper.map(asset.getVideo()))
                .data(dataMapper.map(asset.getData()))
                .link(linkMapper.map(asset.getLink()))
                .ext(extractExtension(extensionMapper, asset))
                .build();
    }
}
