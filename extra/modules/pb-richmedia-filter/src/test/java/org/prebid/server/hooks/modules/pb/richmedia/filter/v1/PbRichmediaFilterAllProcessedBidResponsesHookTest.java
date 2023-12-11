package org.prebid.server.hooks.modules.pb.richmedia.filter.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.core.BidResponsesMraidFilter;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.AnalyticsResult;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.MraidFilterResult;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.model.analytics.TagsImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

public class PbRichmediaFilterAllProcessedBidResponsesHookTest {

    private static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AllProcessedBidResponsesPayload allProcessedBidResponsesPayload;

    @Mock
    private AuctionInvocationContext auctionInvocationContext;

    @Mock
    private BidResponsesMraidFilter mraidFilter;

    private PbRichmediaFilterAllProcessedBidResponsesHook target;

    @Before
    public void setUp() {
        target = new PbRichmediaFilterAllProcessedBidResponsesHook(ObjectMapperProvider.mapper(), mraidFilter, true);
    }

    @Test
    public void shouldHaveValidInitialConfigs() {
        // given and when and then
        assertThat(target.code()).isEqualTo("pb-richmedia-filter-all-processed-bid-responses-hook");
    }

    @Test
    public void callShouldReturnResultWithNoActionWhenFilterMraidIsFalse() {
        // given
        target = new PbRichmediaFilterAllProcessedBidResponsesHook(ObjectMapperProvider.mapper(), mraidFilter, false);
        final List<BidderResponse> givenResponses = givenBidderResponses(2);
        doReturn(givenResponses).when(allProcessedBidResponsesPayload).bidResponses();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
                allProcessedBidResponsesPayload,
                auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.analyticsTags()).isNull();
        assertThat(result.payloadUpdate().apply(AllProcessedBidResponsesPayloadImpl.of(List.of())).bidResponses())
                .isEqualTo(givenResponses);

        verifyNoInteractions(mraidFilter);
    }

    @Test
    public void callShouldReturnResultWithUpdateActionWhenSomeResponsesWereFilteredOut() {
        // given
        final List<BidderResponse> givenResponses = givenBidderResponses(2);
        doReturn(givenResponses).when(allProcessedBidResponsesPayload).bidResponses();
        given(mraidFilter.filter(givenResponses))
                .willReturn(MraidFilterResult.of(givenResponses, List.of(givenAnalyticsResult("bidder", "imp_id"))));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
                allProcessedBidResponsesPayload,
                auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnResultWithNoActionWhenNothingWereFilteredOut() {
        // given
        final List<BidderResponse> givenResponses = givenBidderResponses(2);
        doReturn(givenResponses).when(allProcessedBidResponsesPayload).bidResponses();
        given(mraidFilter.filter(givenResponses))
                .willReturn(MraidFilterResult.of(givenResponses, Collections.emptyList()));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
                allProcessedBidResponsesPayload,
                auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnResultOfFilteredResponses() {
        // given
        final List<BidderResponse> givenResponses = givenBidderResponses(3);
        doReturn(givenResponses).when(allProcessedBidResponsesPayload).bidResponses();
        final List<BidderResponse> expectedResponses = givenBidderResponses(2);
        given(mraidFilter.filter(givenResponses))
                .willReturn(MraidFilterResult.of(expectedResponses, Collections.emptyList()));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
                allProcessedBidResponsesPayload,
                auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.payloadUpdate().apply(AllProcessedBidResponsesPayloadImpl.of(givenResponses)).bidResponses())
                .isEqualTo(expectedResponses);
    }

    @Test
    public void callShouldReturnAnalyticsResultsOfRejectedBids() {
        // given
        final List<BidderResponse> givenResponses = givenBidderResponses(3);
        doReturn(givenResponses).when(allProcessedBidResponsesPayload).bidResponses();
        given(mraidFilter.filter(givenResponses))
                .willReturn(MraidFilterResult.of(
                        givenResponses,
                        List.of(
                                givenAnalyticsResult("bidderA", "imp_id1", "imp_id2"),
                                givenAnalyticsResult("bidderB", "imp_id3"))));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
                allProcessedBidResponsesPayload,
                auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);

        final ResultImpl expectedResult1 = ResultImpl.of(
                "status",
                MAPPER.createObjectNode().put("key", "value"),
                AppliedToImpl.builder()
                        .bidders(singletonList("bidderA"))
                        .impIds(List.of("imp_id1", "imp_id2"))
                        .build());
        final ResultImpl expectedResult2 = ResultImpl.of(
                "status",
                MAPPER.createObjectNode().put("key", "value"),
                AppliedToImpl.builder()
                        .bidders(singletonList("bidderB"))
                        .impIds(singletonList("imp_id3"))
                        .build());
        assertThat(result.analyticsTags()).isEqualTo(
                TagsImpl.of(List.of(ActivityImpl.of("reject-richmedia", "success", List.of(expectedResult1, expectedResult2)))));
    }

    @Test
    public void callShouldReturnEmptyAnalyticsResultsWhenThereAreNoRejectedBids() {
        // given
        final List<BidderResponse> givenResponses = givenBidderResponses(3);
        doReturn(givenResponses).when(allProcessedBidResponsesPayload).bidResponses();
        given(mraidFilter.filter(givenResponses))
                .willReturn(MraidFilterResult.of(givenResponses, Collections.emptyList()));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
                allProcessedBidResponsesPayload,
                auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.analyticsTags()).isNull();
    }

    private static List<BidderResponse> givenBidderResponses(int number) {
        return IntStream.range(0, number)
                .mapToObj(i -> BidderResponse.of("bidder" + i, BidderSeatBid.empty(), 100 + i))
                .toList();
    }

    private static AnalyticsResult givenAnalyticsResult(String bidder, String... rejectedImpIds) {
        return AnalyticsResult.of("status", Map.of("key", "value"), bidder, List.of(rejectedImpIds));
    }

}
