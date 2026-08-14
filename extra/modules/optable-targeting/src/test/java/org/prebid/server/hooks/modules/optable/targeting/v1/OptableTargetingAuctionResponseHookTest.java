package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingAuctionResponseHookTest extends BaseOptableTest {

    private ConfigResolver configResolver;
    private AuctionResponseHook target;

    @Mock
    private AuctionResponsePayload auctionResponsePayload;
    @Mock(strictness = LENIENT)
    private AuctionInvocationContext invocationContext;

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
    public void shouldReturnResultWithNoActionAndWithPBSAnalyticsTagsWhenTargetingIsEmptyAndNoId5Signature() {
        // given
        when(invocationContext.moduleContext()).thenReturn(
                givenModuleContext(null, Future.failedFuture(new RuntimeException("error"))));

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AuctionResponsePayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.analyticsTags())
                .extracting(Tags::activities)
                .extracting(List::getFirst)
                .extracting(Activity::results)
                .extracting(List::getFirst)
                .extracting(Result::values)
                .extracting(it -> it.get("reason"))
                .isNotNull();
        assertThat(result.errors()).isNull();
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndId5SignatureWhenTargetingIsEmptyButId5SignatureIsPresent() {
        // given
        final String signature = "id5Signature";
        final ModuleContext moduleContext =
                givenModuleContext(null, Future.failedFuture(new RuntimeException("error")));
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(givenBidResponse()))
                .bidResponse();
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    @Test
    public void shouldReturnResultWithUpdateActionWhenAdvertiserTargetingOptionIsOn() {
        // given
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext(List.of(
                new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)),
                Future.succeededFuture(givenEmptyTargetingResult())));
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
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
    public void shouldEnrichBidResponseWithBothTargetingKeywordsAndId5Signature() {
        // given
        final String signature = "id5Signature";
        final ModuleContext moduleContext = givenModuleContext(List.of(
                new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)));
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
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
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);

        assertThat(targeting.get("keyspace").asText()).isEqualTo("audienceId");
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    @Test
    public void shouldEnrichBidResponseWithId5SignatureOnlyWhenNoTargeting() {
        // given
        final String signature = "id5Signature";
        final ModuleContext moduleContext =
                givenModuleContext(null, Future.succeededFuture(givenEmptyTargetingResult()));
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(givenBidResponse()))
                .bidResponse();
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    @Test
    public void shouldReturnUpdateActionWhenTargetingIsPresentEvenIfOptableTargetingCallFails() {
        // given
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext(
                List.of(new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)),
                Future.failedFuture(new RuntimeException("error"))));
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
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
        assertThat(targeting.get("keyspace").asText()).isEqualTo("audienceId");
    }

    @Test
    public void shouldReturnNoActionWhenOptableTargetingCallFailsAndTargetingIsEmptyAndNoId5Signature() {
        // given
        when(invocationContext.moduleContext()).thenReturn(
                givenModuleContext(null, Future.failedFuture(new RuntimeException("error"))));

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void shouldReturnUpdateActionWithId5SignatureWhenOptableTargetingCallFailsAndTargetingIsEmpty() {
        // given
        final String signature = "id5Signature";
        final ModuleContext moduleContext =
                givenModuleContext(null, Future.failedFuture(new RuntimeException("error")));
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(auctionResponsePayload.bidResponse()).thenReturn(givenBidResponse());

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(givenBidResponse()))
                .bidResponse();
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    @Test
    public void shouldReturnNoActionWhenTargetingCallFailsAndNoBidsAndNoId5Signature() {
        // given
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext(List.of(
                new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)),
                Future.failedFuture(new RuntimeException("error"))));

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void shouldReturnUpdateActionWithId5SignatureWhenTargetingCallFailsAndNoBids() {
        // given
        final String signature = "id5Signature";
        final ModuleContext moduleContext = givenModuleContext(List.of(
                new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)),
                Future.failedFuture(new RuntimeException("error")));
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        final BidResponse bidlessResponse = BidResponse.builder().build();
        when(auctionResponsePayload.bidResponse()).thenReturn(bidlessResponse);

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(bidlessResponse))
                .bidResponse();
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    @Test
    public void shouldReturnNoActionWhenAdserverTargetingIsDisabledAndNoId5Signature() {
        // given
        final OptableTargetingProperties properties = givenOptableTargetingProperties(false);
        properties.setAdserverTargeting(false);
        configResolver = new ConfigResolver(mapper, jsonMerger, properties);
        target = new OptableTargetingAuctionResponseHook(configResolver, mapper, jsonMerger);
        when(invocationContext.accountConfig()).thenReturn(mapper.valueToTree(properties));
        when(invocationContext.moduleContext()).thenReturn(givenModuleContext(List.of(
                new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)),
                Future.failedFuture(new RuntimeException("error"))));

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void shouldReturnUpdateActionWithId5SignatureWhenAdserverTargetingIsDisabled() {
        // given
        final String signature = "id5Signature";
        final OptableTargetingProperties properties = givenOptableTargetingProperties(false);
        properties.setAdserverTargeting(false);
        configResolver = new ConfigResolver(mapper, jsonMerger, properties);
        target = new OptableTargetingAuctionResponseHook(configResolver, mapper, jsonMerger);
        when(invocationContext.accountConfig()).thenReturn(mapper.valueToTree(properties));
        final ModuleContext moduleContext = givenModuleContext(List.of(
                new Audience(
                        "provider",
                        List.of(new AudienceId("audienceId")),
                        "keyspace",
                        1)),
                Future.failedFuture(new RuntimeException("error")));
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(givenBidResponse()))
                .bidResponse();
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    @Test
    public void shouldReturnNoActionWhenSkipEnrichmentIsTrueAndNoId5Signature() {
        // given
        final ModuleContext moduleContext = givenModuleContext();
        moduleContext.setShouldSkipEnrichment(true);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void shouldReturnUpdateActionWithId5SignatureWhenSkipEnrichmentIsTrue() {
        // given
        final String signature = "id5Signature";
        final ModuleContext moduleContext = givenModuleContext();
        moduleContext.setShouldSkipEnrichment(true);
        moduleContext.setId5Signature(signature);
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<AuctionResponsePayload>> future =
                target.call(auctionResponsePayload, invocationContext);
        final InvocationResult<AuctionResponsePayload> result = future.result();
        final BidResponse bidResponse = result
                .payloadUpdate()
                .apply(AuctionResponsePayloadImpl.of(givenBidResponse()))
                .bidResponse();
        final JsonNode passthrough = bidResponse.getExt().getPrebid().getPassthrough();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action);
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo(signature);
    }

    private ObjectNode givenAccountConfig(boolean cacheEnabled) {
        return mapper.valueToTree(givenOptableTargetingProperties(cacheEnabled));
    }
}
