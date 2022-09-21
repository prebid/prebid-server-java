package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import com.iabtechlab.openrtb.v2.OpenRtb;
import java.util.Objects;

import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.extractExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufSeatBidMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> {

    private final ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> bidMapper;
    private final ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid, ProtobufExtensionType> extensionMapper;

    public ProtobufSeatBidMapper(
            ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> bidMapper,
            ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid, ProtobufExtensionType> extensionMapper) {

        this.bidMapper = Objects.requireNonNull(bidMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public SeatBid map(OpenRtb.BidResponse.SeatBid seatBid) {
        return SeatBid.builder()
                .bid(mapList(seatBid.getBidList(), bidMapper::map))
                .seat(seatBid.getSeat())
                .group(BooleanUtils.toInteger(seatBid.getGroup()))
                .ext(extractExtension(extensionMapper, seatBid))
                .build();
    }
}
