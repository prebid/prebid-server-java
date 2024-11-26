package org.prebid.server.it.hooks;

import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.exitpoint.ExitpointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.v1.analytics.ResultImpl;
import org.prebid.server.hooks.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.exitpoint.ExitpointHook;
import org.prebid.server.hooks.v1.exitpoint.ExitpointPayload;
import org.prebid.server.json.JacksonMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SampleItExitpointHook implements ExitpointHook {

    private final JacksonMapper mapper;

    public SampleItExitpointHook(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Future<InvocationResult<ExitpointPayload>> call(ExitpointPayload exitpointPayload,
                                                           AuctionInvocationContext invocationContext) {

        final BidResponse bidResponse = invocationContext.auctionContext().getBidResponse();
        final List<SeatBid> seatBids = updateBids(bidResponse.getSeatbid());
        final BidResponse updatedResponse = bidResponse.toBuilder().seatbid(seatBids).build();

        return Future.succeededFuture(InvocationResultImpl.<ExitpointPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payload -> ExitpointPayloadImpl.of(
                        exitpointPayload.responseHeaders().add("Exitpoint-Hook-Header", "Exitpoint-Hook-Value"),
                        mapper.encodeToString(updatedResponse)))
                .debugMessages(Arrays.asList(
                        "exitpoint debug message 1",
                        "exitpoint debug message 2"))
                .analyticsTags(TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                        "exitpoint-device-id",
                        "success",
                        Collections.singletonList(ResultImpl.of(
                                "success",
                                mapper.mapper().createObjectNode().put("exitpoint-some-field", "exitpoint-some-value"),
                                AppliedToImpl.builder()
                                        .impIds(Collections.singletonList("impId1"))
                                        .request(true)
                                        .build()))))))
                .build());
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
