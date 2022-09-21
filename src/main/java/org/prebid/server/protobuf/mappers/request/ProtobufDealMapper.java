package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Deal;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufDealMapper<ProtobufExtensionType>
        implements ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> {

    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp.Deal, ProtobufExtensionType> extensionMapper;

    public ProtobufDealMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp.Deal, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Pmp.Deal map(Deal deal) {
        final OpenRtb.BidRequest.Imp.Pmp.Deal.Builder resultBuilder = OpenRtb.BidRequest.Imp.Pmp.Deal.newBuilder();

        setNotNull(deal.getId(), resultBuilder::setId);
        setNotNull(mapNotNull(deal.getBidfloor(), BigDecimal::doubleValue), resultBuilder::setBidfloor);
        setNotNull(deal.getBidfloorcur(), resultBuilder::setBidfloorcur);
        setNotNull(deal.getAt(), resultBuilder::setAt);
        setNotNull(deal.getWseat(), resultBuilder::addAllWseat);
        setNotNull(deal.getWadomain(), resultBuilder::addAllWadomain);

        mapAndSetExtension(extensionMapper, deal.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
