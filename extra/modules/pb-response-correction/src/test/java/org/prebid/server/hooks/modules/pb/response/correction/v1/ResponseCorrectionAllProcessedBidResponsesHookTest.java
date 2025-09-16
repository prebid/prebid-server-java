package org.prebid.server.hooks.modules.pb.response.correction.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.pb.response.correction.core.ResponseCorrectionProvider;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.Correction;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ResponseCorrectionAllProcessedBidResponsesHookTest {

    private static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    @Mock
    private ResponseCorrectionProvider responseCorrectionProvider;

    private ResponseCorrectionAllProcessedBidResponsesHook target;

    @Mock
    private AllProcessedBidResponsesPayload payload;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private AuctionInvocationContext invocationContext;

    @BeforeEach
    public void setUp() {
        given(invocationContext.accountConfig()).willReturn(MAPPER.valueToTree(Config.of(true, null)));
        given(invocationContext.auctionContext())
                .willReturn(AuctionContext.builder().bidRequest(BidRequest.builder().build()).build());

        target = new ResponseCorrectionAllProcessedBidResponsesHook(responseCorrectionProvider, MAPPER);
    }

    @Test
    public void callShouldReturnFailedResultOnInvalidConfiguration() {
        // given
        given(invocationContext.accountConfig()).willReturn(MAPPER.valueToTree(Map.of("enabled", emptyList())));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> result =
                target.call(payload, invocationContext);

        //then
        assertThat(result.result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.failure);
            assertThat(invocationResult.message()).startsWith("Cannot deserialize value of type");
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
        });
    }

    @Test
    public void callShouldReturnNoActionOnDisabledConfig() {
        // given
        given(invocationContext.accountConfig()).willReturn(MAPPER.valueToTree(Config.of(false, null)));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> result =
                target.call(payload, invocationContext);

        //then
        assertThat(result.result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
        });
    }

    @Test
    public void callShouldReturnNoActionIfThereIsNoApplicableCorrections() {
        // given
        given(responseCorrectionProvider.corrections(any(), any())).willReturn(emptyList());

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> result =
                target.call(payload, invocationContext);

        //then
        assertThat(result.result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
        });
    }

    @Test
    public void callShouldReturnUpdate() {
        // given
        final Correction correction = mock(Correction.class);
        given(responseCorrectionProvider.corrections(any(), any())).willReturn(singletonList(correction));

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> result =
                target.call(payload, invocationContext);

        //then
        assertThat(result.result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            assertThat(invocationResult.payloadUpdate()).isNotNull();
        });
    }
}
