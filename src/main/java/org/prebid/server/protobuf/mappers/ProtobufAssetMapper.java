package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.*;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufAssetMapper<ProtobufExtensionType>
        implements ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> {

    private final ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> titleMapper;
    private final ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> imgMapper;
    private final ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> videoMapper;
    private final ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> dataMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset, ProtobufExtensionType> extensionMapper;

    public ProtobufAssetMapper(
            ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> titleMapper,
            ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> imgMapper,
            ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> videoMapper,
            ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> dataMapper,
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset, ProtobufExtensionType> extensionMapper) {

        this.titleMapper = Objects.requireNonNull(titleMapper);
        this.imgMapper = Objects.requireNonNull(imgMapper);
        this.videoMapper = Objects.requireNonNull(videoMapper);
        this.dataMapper = Objects.requireNonNull(dataMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.NativeRequest.Asset map(Asset asset) {
        final OpenRtb.NativeRequest.Asset.Builder resultBuilder = OpenRtb.NativeRequest.Asset.newBuilder();

        setNotNull(asset.getId(), resultBuilder::setId);
        setNotNull(mapNotNull(asset.getRequired(), BooleanUtils::toBoolean), resultBuilder::setRequired);
        setNotNull(mapNotNull(asset.getTitle(), titleMapper::map), resultBuilder::setTitle);
        setNotNull(mapNotNull(asset.getImg(), imgMapper::map), resultBuilder::setImg);
        setNotNull(mapNotNull(asset.getVideo(), videoMapper::map), resultBuilder::setVideo);
        setNotNull(mapNotNull(asset.getData(), dataMapper::map), resultBuilder::setData);

        mapAndSetExtension(extensionMapper, asset.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
