package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionResponseValidator;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingAuctionResponseHookTest extends BaseOptableTest {

    @Mock
    AuctionResponsePayload auctionResponsePayload;
    @Mock(strictness = LENIENT)
    AuctionInvocationContext invocationContext;
    private AuctionResponseValidator auctionResponseValidator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonMerger jsonMerger = new JsonMerger(new JacksonMapper(mapper));
    private AuctionResponseHook target;

    private ConfigResolver configResolver;

    @BeforeEach
    public void setUp() {
        when(invocationContext.accountConfig()).thenReturn(givenAccountConfig(true));
        configResolver = new ConfigResolver(mapper, jsonMerger, givenOptableTargetingProperties(false));
        target = new OptableTargetingAuctionResponseHook(
                configResolver,
                mapper,
                jsonMerger);
    }

    @Test
    public void shouldHaveCode() {
        // when and then
        assertThat(target.code()).isEqualTo("optable-targeting-auction-response-hook");

    }

    @Test
    public void shouldReturnResultWithNoActionAndWithPBSAnalyticsTags() {
        // given
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future = target.call(auctionResponsePayload,
                invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionResponsePayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.analyticsTags().activities().getFirst()
                .results().getFirst().values().get("reason")).isNotNull();
        assertThat(result.errors()).isNull();
    }

    @Test
    public void shouldReturnResultWithUpdateActionWhenAdvertiserTargetingOptionIsOn() {
        // given
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext(List.of(new Audience("provider",
                List.of(new AudienceId("audienceId")), "keyspace", 1))));
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future = target.call(auctionResponsePayload,
                invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(givenBidResponse()))
                .bidResponse();
        final ObjectNode targeting = (ObjectNode) bidResponse.getSeatbid()
                .getFirst()
                .getBid()
                .getFirst()
                .getExt()
                .get("prebid")
                .get("targeting");

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);

        assertThat(targeting)
                .isNotNull()
                .hasSize(3);

        assertThat(targeting.get("keyspace").asText()).isEqualTo("audienceId");
    }

    @Test
    public void shouldReturnResultWithNoActionWhenAdvertiserTargetingOptionIsOff() {
        // given
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext(List.of(new Audience("provider",
                List.of(new AudienceId("audienceId")), "keyspace", 1))));
        target = new OptableTargetingAuctionResponseHook(
                configResolver,
                mapper,
                jsonMerger);

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future = target.call(auctionResponsePayload,
                invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
    }

    private ObjectNode givenAccountConfig(boolean cacheEnabled) {
        return mapper.valueToTree(givenOptableTargetingProperties(cacheEnabled));
    }
}
