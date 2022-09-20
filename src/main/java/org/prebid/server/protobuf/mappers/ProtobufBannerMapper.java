package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Format;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.*;

public class ProtobufBannerMapper<ProtobufExtensionType>
        implements ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> {

    private final ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> formatMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner, ProtobufExtensionType> extensionMapper;

    public ProtobufBannerMapper(
            ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> formatMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner, ProtobufExtensionType> extensionMapper) {

        this.formatMapper = Objects.requireNonNull(formatMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Banner map(Banner banner) {
        final OpenRtb.BidRequest.Imp.Banner.Builder resultBuilder = OpenRtb.BidRequest.Imp.Banner.newBuilder();

        setNotNull(banner.getId(), resultBuilder::setId);
        setNotNull(banner.getW(), resultBuilder::setW);
        setNotNull(banner.getH(), resultBuilder::setH);
        setNotNull(banner.getPos(), resultBuilder::setPos);
        setNotNull(mapNotNull(banner.getTopframe(), BooleanUtils::toBoolean), resultBuilder::setTopframe);
        setNotNull(mapNotNull(banner.getVcm(), BooleanUtils::toBoolean), resultBuilder::setVcm);

        setNotNull(banner.getBtype(), resultBuilder::addAllBtype);
        setNotNull(banner.getBattr(), resultBuilder::addAllBattr);
        setNotNull(banner.getMimes(), resultBuilder::addAllMimes);
        setNotNull(banner.getExpdir(), resultBuilder::addAllExpdir);
        setNotNull(banner.getApi(), resultBuilder::addAllApi);
        setNotNull(mapList(banner.getFormat(), formatMapper::map), resultBuilder::addAllFormat);

        mapAndSetExtension(extensionMapper, banner.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
