package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufAudioMapper<ProtobufExtensionType>
        implements ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> {

    private final ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Audio, ProtobufExtensionType> extensionMapper;

    public ProtobufAudioMapper(
            ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Audio, ProtobufExtensionType> extensionMapper) {

        this.bannerMapper = Objects.requireNonNull(bannerMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Audio map(Audio audio) {
        final OpenRtb.BidRequest.Imp.Audio.Builder resultBuilder = OpenRtb.BidRequest.Imp.Audio.newBuilder();

        setNotNull(audio.getMimes(), resultBuilder::addAllMimes);
        setNotNull(audio.getMinduration(), resultBuilder::setMinduration);
        setNotNull(audio.getMaxduration(), resultBuilder::setMaxduration);
        setNotNull(audio.getProtocols(), resultBuilder::addAllProtocols);
        setNotNull(audio.getStartdelay(), resultBuilder::setStartdelay);
        setNotNull(audio.getSequence(), resultBuilder::setSequence);
        setNotNull(audio.getBattr(), resultBuilder::addAllBattr);
        setNotNull(audio.getMaxextended(), resultBuilder::setMaxextended);
        setNotNull(audio.getMinbitrate(), resultBuilder::setMinbitrate);
        setNotNull(audio.getMaxbitrate(), resultBuilder::setMaxbitrate);
        setNotNull(audio.getDelivery(), resultBuilder::addAllDelivery);
        setNotNull(mapList(audio.getCompanionad(), bannerMapper::map), resultBuilder::addAllCompanionad);
        setNotNull(audio.getApi(), resultBuilder::addAllApi);
        setNotNull(audio.getCompaniontype(), resultBuilder::addAllCompaniontype);
        setNotNull(audio.getMaxseq(), resultBuilder::setMaxseq);
        setNotNull(audio.getFeed(), resultBuilder::setFeed);
        setNotNull(mapNotNull(audio.getStitched(), BooleanUtils::toBoolean), resultBuilder::setStitched);
        setNotNull(audio.getNvol(), resultBuilder::setNvol);

        mapAndSetExtension(extensionMapper, audio.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
