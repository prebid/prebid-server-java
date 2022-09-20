package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.*;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;
import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.*;

public class ProtobufImpMapper<ProtobufExtensionType>
        implements ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> {

    private final ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> metricMapper;
    private final ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper;
    private final ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> videoMapper;
    private final ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> audioMapper;
    private final ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> nativeMapper;
    private final ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> pmpMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp, ProtobufExtensionType> extensionMapper;

    public ProtobufImpMapper(
            ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> metricMapper,
            ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper,
            ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> videoMapper,
            ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> audioMapper,
            ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> nativeMapper,
            ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> pmpMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp, ProtobufExtensionType> extensionMapper) {

        this.metricMapper = Objects.requireNonNull(metricMapper);
        this.bannerMapper = Objects.requireNonNull(bannerMapper);
        this.videoMapper = Objects.requireNonNull(videoMapper);
        this.audioMapper = Objects.requireNonNull(audioMapper);
        this.nativeMapper = Objects.requireNonNull(nativeMapper);
        this.pmpMapper = Objects.requireNonNull(pmpMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp map(Imp imp) {
        final OpenRtb.BidRequest.Imp.Builder resultBuilder = OpenRtb.BidRequest.Imp.newBuilder();

        setNotNull(imp.getId(), resultBuilder::setId);
        setNotNull(mapList(imp.getMetric(), metricMapper::map), resultBuilder::addAllMetric);
        setNotNull(mapNotNull(imp.getBanner(), bannerMapper::map), resultBuilder::setBanner);
        setNotNull(mapNotNull(imp.getVideo(), videoMapper::map), resultBuilder::setVideo);
        setNotNull(mapNotNull(imp.getAudio(), audioMapper::map), resultBuilder::setAudio);
        setNotNull(mapNotNull(imp.getXNative(), nativeMapper::map), resultBuilder::setNative);
        setNotNull(mapNotNull(imp.getPmp(), pmpMapper::map), resultBuilder::setPmp);
        setNotNull(imp.getDisplaymanager(), resultBuilder::setDisplaymanager);
        setNotNull(imp.getDisplaymanagerver(), resultBuilder::setDisplaymanagerver);
        setNotNull(mapNotNull(imp.getInstl(), BooleanUtils::toBoolean), resultBuilder::setInstl);
        setNotNull(imp.getTagid(), resultBuilder::setTagid);
        setNotNull(mapNotNull(imp.getBidfloor(), BigDecimal::doubleValue), resultBuilder::setBidfloor);
        setNotNull(imp.getBidfloorcur(), resultBuilder::setBidfloorcur);
        setNotNull(mapNotNull(imp.getClickbrowser(), BooleanUtils::toBoolean), resultBuilder::setClickbrowser);
        setNotNull(mapNotNull(imp.getSecure(), BooleanUtils::toBoolean), resultBuilder::setSecure);
        setNotNull(imp.getIframebuster(), resultBuilder::addAllIframebuster);
        setNotNull(imp.getExp(), resultBuilder::setExp);

        mapAndSetExtension(extensionMapper, imp.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
