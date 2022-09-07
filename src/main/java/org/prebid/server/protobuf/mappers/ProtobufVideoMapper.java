package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Video;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufVideoMapper<ProtobufExtensionType>
        implements ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> {

    private final ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ProtobufExtensionType> extensionMapper;

    public ProtobufVideoMapper(
            ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ProtobufExtensionType> extensionMapper) {

        this.bannerMapper = Objects.requireNonNull(bannerMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Video map(Video video) {
        final OpenRtb.BidRequest.Imp.Video.Builder resultBuilder =
                OpenRtb.BidRequest.Imp.Video.newBuilder();

        setNotNull(video.getMimes(), resultBuilder::addAllMimes);
        setNotNull(video.getMinduration(), resultBuilder::setMinduration);
        setNotNull(video.getMaxduration(), resultBuilder::setMaxduration);
        setNotNull(video.getProtocols(), resultBuilder::addAllProtocols);
        setNotNull(video.getW(), resultBuilder::setW);
        setNotNull(video.getH(), resultBuilder::setH);
        setNotNull(video.getStartdelay(), resultBuilder::setStartdelay);
        setNotNull(video.getPlacement(), resultBuilder::setPlacement);
        setNotNull(video.getLinearity(), resultBuilder::setLinearity);
        setNotNull(mapNotNull(video.getSkip(), BooleanUtils::toBoolean), resultBuilder::setSkip);
        setNotNull(video.getSkipmin(), resultBuilder::setSkipmin);
        setNotNull(video.getSkipafter(), resultBuilder::setSkipafter);
        setNotNull(video.getSequence(), resultBuilder::setSequence);
        setNotNull(video.getBattr(), resultBuilder::addAllBattr);
        setNotNull(video.getMaxextended(), resultBuilder::setMaxextended);
        setNotNull(video.getMinbitrate(), resultBuilder::setMinbitrate);
        setNotNull(mapNotNull(video.getBoxingallowed(), BooleanUtils::toBoolean), resultBuilder::setBoxingallowed);
        setNotNull(video.getPlaybackmethod(), resultBuilder::addAllPlaybackmethod);
        setNotNull(video.getPlaybackend(), resultBuilder::setPlaybackend);
        setNotNull(video.getDelivery(), resultBuilder::addAllDelivery);
        setNotNull(video.getPos(), resultBuilder::setPos);
        setNotNull(mapList(video.getCompanionad(), bannerMapper::map), resultBuilder::addAllCompanionad);
        setNotNull(video.getApi(), resultBuilder::addAllApi);
        setNotNull(video.getCompaniontype(), resultBuilder::addAllCompaniontype);

        if (extensionMapper != null) {
            final ProtobufExtensionType ext = extensionMapper.map(video.getExt());
            resultBuilder.setExtension(extensionMapper.extensionType(), ext);
        }
        return resultBuilder.build();
    }
}
