package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.iabtechlab.openrtb.v2.OpenRtb;
import java.util.Objects;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufBidResponseMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.BidResponse, BidResponse> {

    private final ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> seatbidMapper;
    private final ProtobufExtensionMapper<OpenRtb.BidResponse, ProtobufExtensionType, ExtBidResponse> extensionMapper;

    public ProtobufBidResponseMapper(
            ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> seatbidMapper,
            ProtobufExtensionMapper<OpenRtb.BidResponse, ProtobufExtensionType, ExtBidResponse> extensionMapper) {

        this.seatbidMapper = Objects.requireNonNull(seatbidMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public BidResponse map(OpenRtb.BidResponse bidResponse) {
        return BidResponse.builder()
                .id(bidResponse.getId())
                .seatbid(mapList(bidResponse.getSeatbidList(), seatbidMapper::map))
                .bidid(bidResponse.getBidid())
                .cur(bidResponse.getCur())
                .customdata(bidResponse.getCustomdata())
                .nbr(bidResponse.getNbr())
                .ext(extractExtension(extensionMapper, bidResponse))
                .build();
    }
}
