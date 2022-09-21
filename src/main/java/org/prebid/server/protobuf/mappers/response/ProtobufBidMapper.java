package org.prebid.server.protobuf.mappers.response;

import com.iab.openrtb.response.Bid;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufJsonExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;

import static org.prebid.server.protobuf.MapperUtils.extractAndMapExtension;

public class ProtobufBidMapper<ProtobufExtensionType>
        implements ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> {

    private final ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid.Bid, ProtobufExtensionType> extensionMapper;

    public ProtobufBidMapper(
            ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid.Bid, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public Bid map(OpenRtb.BidResponse.SeatBid.Bid bid) {
        return Bid.builder()
                .id(bid.getId())
                .impid(bid.getImpid())
                .price(BigDecimal.valueOf(bid.getPrice()))
                .nurl(bid.getNurl())
                .burl(bid.getBurl())
                .lurl(bid.getLurl())
                .adm(bid.getAdm())
                .adid(bid.getAdid())
                .adomain(bid.getAdomainList())
                .bundle(bid.getBundle())
                .iurl(bid.getIurl())
                .cid(bid.getCid())
                .crid(bid.getCrid())
                .tactic(bid.getTactic())
                .cat(bid.getCatList())
                .attr(bid.getAttrList())
                .api(bid.getApi())
                .protocol(bid.getProtocol())
                .qagmediarating(bid.getQagmediarating())
                .language(bid.getLanguage())
                .dealid(bid.getDealid())
                .w(bid.getW())
                .h(bid.getH())
                .wratio(bid.getWratio())
                .hratio(bid.getHratio())
                .exp(bid.getExp())
                .ext(extractAndMapExtension(extensionMapper, bid))
                .build();
    }
}
