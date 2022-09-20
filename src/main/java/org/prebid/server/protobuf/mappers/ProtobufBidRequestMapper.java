package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufBidRequestMapper<ProtobufExtensionType>
        implements ProtobufMapper<BidRequest, OpenRtb.BidRequest> {

    private final ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> impMapper;
    private final ProtobufMapper<Site, OpenRtb.BidRequest.Site> siteMapper;
    private final ProtobufMapper<App, OpenRtb.BidRequest.App> appMapper;
    private final ProtobufMapper<Device, OpenRtb.BidRequest.Device> deviceMapper;
    private final ProtobufMapper<User, OpenRtb.BidRequest.User> userMapper;
    private final ProtobufMapper<Source, OpenRtb.BidRequest.Source> sourceMapper;
    private final ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> regsMapper;
    private final ProtobufExtensionMapper<OpenRtb.BidRequest, ExtRequest, ProtobufExtensionType> extensionMapper;

    public ProtobufBidRequestMapper(
            ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> impMapper,
            ProtobufMapper<Site, OpenRtb.BidRequest.Site> siteMapper,
            ProtobufMapper<App, OpenRtb.BidRequest.App> appMapper,
            ProtobufMapper<Device, OpenRtb.BidRequest.Device> deviceMapper,
            ProtobufMapper<User, OpenRtb.BidRequest.User> userMapper,
            ProtobufMapper<Source, OpenRtb.BidRequest.Source> sourceMapper,
            ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> regsMapper,
            ProtobufExtensionMapper<OpenRtb.BidRequest, ExtRequest, ProtobufExtensionType> extensionMapper) {

        this.impMapper = Objects.requireNonNull(impMapper);
        this.siteMapper = Objects.requireNonNull(siteMapper);
        this.appMapper = Objects.requireNonNull(appMapper);
        this.deviceMapper = Objects.requireNonNull(deviceMapper);
        this.userMapper = Objects.requireNonNull(userMapper);
        this.sourceMapper = Objects.requireNonNull(sourceMapper);
        this.regsMapper = Objects.requireNonNull(regsMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest map(BidRequest bidRequest) {
        final OpenRtb.BidRequest.Builder resultBuilder = OpenRtb.BidRequest.newBuilder();

        setNotNull(bidRequest.getId(), resultBuilder::setId);
        setNotNull(mapList(bidRequest.getImp(), impMapper::map), resultBuilder::addAllImp);
        setNotNull(mapNotNull(bidRequest.getSite(), siteMapper::map), resultBuilder::setSite);
        setNotNull(mapNotNull(bidRequest.getApp(), appMapper::map), resultBuilder::setApp);
        setNotNull(mapNotNull(bidRequest.getDevice(), deviceMapper::map), resultBuilder::setDevice);
        setNotNull(mapNotNull(bidRequest.getUser(), userMapper::map), resultBuilder::setUser);
        setNotNull(mapNotNull(bidRequest.getTest(), BooleanUtils::toBoolean), resultBuilder::setTest);
        setNotNull(bidRequest.getAt(), resultBuilder::setAt);
        setNotNull(mapNotNull(bidRequest.getTmax(), Long::intValue), resultBuilder::setTmax);
        setNotNull(bidRequest.getWseat(), resultBuilder::addAllWseat);
        setNotNull(bidRequest.getBseat(), resultBuilder::addAllBseat);
        setNotNull(mapNotNull(bidRequest.getAllimps(), BooleanUtils::toBoolean), resultBuilder::setAllimps);
        setNotNull(bidRequest.getCur(), resultBuilder::addAllCur);
        setNotNull(bidRequest.getWlang(), resultBuilder::addAllWlang);
        setNotNull(bidRequest.getBcat(), resultBuilder::addAllBcat);
        setNotNull(bidRequest.getBadv(), resultBuilder::addAllBadv);
        setNotNull(bidRequest.getBapp(), resultBuilder::addAllBapp);
        setNotNull(mapNotNull(bidRequest.getSource(), sourceMapper::map), resultBuilder::setSource);
        setNotNull(mapNotNull(bidRequest.getRegs(), regsMapper::map), resultBuilder::setRegs);

        if (extensionMapper != null) {
            final ProtobufExtensionType ext = extensionMapper.map(bidRequest.getExt());
            resultBuilder.setExtension(extensionMapper.extensionType(), ext);
        }
        return resultBuilder.build();
    }
}
