package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.LiveIntentOmniChannelProperties;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks.LiveIntentOmniChannelIdentityProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHookTest {

    private static final JacksonMapper MAPPER = new JacksonMapper(ObjectMapperProvider.mapper());

    @Mock
    private HttpClient httpClient;

    @Mock(strictness = LENIENT)
    private Random random;

    @Mock(strictness = LENIENT)
    private LiveIntentOmniChannelProperties properties;

    private LiveIntentOmniChannelIdentityProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        given(properties.getRequestTimeoutMs()).willReturn(5L);
        given(properties.getIdentityResolutionEndpoint()).willReturn("https://test.com/idres");
        given(properties.getAuthToken()).willReturn("auth_token");
        given(properties.getTreatmentRate()).willReturn(0.9f);
        given(random.nextFloat()).willReturn(0.89f);

        target = new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(properties, MAPPER, httpClient, random);
    }

    @Test
    public void creationShouldFailOnInvalidIdentityUrl() {
        given(properties.getIdentityResolutionEndpoint()).willReturn("invalid_url");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(properties, MAPPER, httpClient, random));
    }

    @Test
    public void callShouldEnrichUserEidsWithRequestedEids() {
        // given
        final Uid givenUid = Uid.builder().id("id1").atype(2).build();
        final Eid givenEid = Eid.builder().source("some.source.com").uids(singletonList(givenUid)).build();
        final User givenUser = User.builder().eids(singletonList(givenEid)).build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").user(givenUser).build();

        final String givenResponseBody = """
                {
                    "eids": [{
                        "source": "liveintent.com",
                        "uids": [{
                            "atype": 3,
                            "id": "id2"
                        }]
                    }]
                }""";

        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, givenResponseBody)));

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();

        // then
        final Eid expectedEid = Eid.builder()
                .source("liveintent.com")
                .uids(singletonList(Uid.builder().id("id2").atype(3).build()))
                .build();

        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.payloadUpdate().apply(AuctionRequestPayloadImpl.of(givenBidRequest)))
                .extracting(AuctionRequestPayload::bidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getEids)
                .isEqualTo(List.of(givenEid, expectedEid));

        verify(httpClient).post(
                eq("https://test.com/idres"),
                argThat(headers -> headers.contains("Authorization", "Bearer auth_token", true)),
                eq(MAPPER.encodeToString(givenBidRequest)),
                eq(5L));
    }

    @Test
    public void callShouldCreateUserAndUseRequestedEidsWhenUserIsAbsent() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().id("request").user(null).build();

        final String givenResponseBody = """
                {
                    "eids": [{
                        "source": "liveintent.com",
                        "uids": [{
                            "atype": 3,
                            "id": "id2"
                        }]
                    }]
                }""";

        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, givenResponseBody)));

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();

        // then
        final Eid expectedEid = Eid.builder()
                .source("liveintent.com")
                .uids(singletonList(Uid.builder().id("id2").atype(3).build()))
                .build();

        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.payloadUpdate().apply(AuctionRequestPayloadImpl.of(givenBidRequest)))
                .extracting(AuctionRequestPayload::bidRequest)
                .extracting(BidRequest::getUser)
                .extracting(User::getEids)
                .isEqualTo(List.of(expectedEid));

        verify(httpClient).post(
                eq("https://test.com/idres"),
                argThat(headers -> headers.contains("Authorization", "Bearer auth_token", true)),
                eq(MAPPER.encodeToString(givenBidRequest)),
                eq(5L));
    }

    @Test
    public void callShouldReturnNoActionSuccessfullyWhenTreatmentRateIsLowerThanThreshold() {
        // given
        final Uid givenUid = Uid.builder().id("id1").atype(2).build();
        final Eid givebEid = Eid.builder().source("some.source.com").uids(singletonList(givenUid)).build();
        final User givenUser = User.builder().eids(singletonList(givebEid)).build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").user(givenUser).build();

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        given(properties.getTreatmentRate()).willReturn(0.9f);
        given(random.nextFloat()).willReturn(0.91f);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(
                        AuctionRequestPayloadImpl.of(givenBidRequest),
                        auctionInvocationContext)
                .result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void callShouldReturnFailureWhenRequestingEidsIsFailed() {
        // given
        final Uid givenUid = Uid.builder().id("id1").atype(2).build();
        final Eid givebEid = Eid.builder().source("some.source.com").uids(singletonList(givenUid)).build();
        final User givenUser = User.builder().eids(singletonList(givebEid)).build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").user(givenUser).build();

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("Timeout exceeded")));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> result = target.call(
                AuctionRequestPayloadImpl.of(givenBidRequest),
                auctionInvocationContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(TimeoutException.class);
        assertThat(result.cause())
                .isInstanceOf(TimeoutException.class)
                .hasMessage("Timeout exceeded");
    }
}
