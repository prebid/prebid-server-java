package org.prebid.server.it.hooks;

import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.exitpoint.ExitpointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultUtils;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.exit.ExitpointHook;
import org.prebid.server.hooks.v1.exit.ExitpointPayload;
import org.prebid.server.json.JacksonMapper;

import java.util.List;

public class SampleItExitpointHook implements ExitpointHook {

    private final JacksonMapper mapper;

    public SampleItExitpointHook(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Future<InvocationResult<ExitpointPayload>> call(ExitpointPayload exitpointPayload,
                                                           AuctionInvocationContext invocationContext) {

        final BidResponse bidResponse = mapper.decodeValue(exitpointPayload.responseBody(), BidResponse.class);
        final List<SeatBid> seatBids = updateBids(bidResponse.getSeatbid());
        final BidResponse updatedResponse = bidResponse.toBuilder().seatbid(seatBids).build();

        return Future.succeededFuture(InvocationResultUtils.succeeded(payload ->
                ExitpointPayloadImpl.of(exitpointPayload.responseHeaders(), mapper.encodeToString(updatedResponse))));
    }

    private List<SeatBid> updateBids(List<SeatBid> seatBids) {
        return seatBids.stream()
                .map(seatBid -> seatBid.toBuilder().bid(seatBid.getBid().stream()
                                .map(bid -> bid.toBuilder()
                                        .adm(bid.getAdm()
                                                + "<Impression><![CDATA[Exitpoint hook have been here]]>"
                                                + "</Impression>")
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    @Override
    public String code() {
        return "exitpoint";
    }


}
