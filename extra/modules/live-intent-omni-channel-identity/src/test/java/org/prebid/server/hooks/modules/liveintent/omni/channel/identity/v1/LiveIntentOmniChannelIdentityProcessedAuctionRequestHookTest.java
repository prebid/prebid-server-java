package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks.LiveIntentOmniChannelIdentityProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHookTest {

    private ModuleConfig moduleConfig;
    private LiveIntentOmniChannelIdentityProcessedAuctionRequestHook target;
    private JacksonMapper jacksonMapper;

    @Mock
    private HttpClient httpClient;
    @Mock
    private Random random;

    private final ArgumentMatcher<MultiMap> bearerAuthHeaderMatcher = new ArgumentMatcher<>() {
        @Override
        public boolean matches(MultiMap entries) {
            return entries.contains("Authorization", "Bearer " + moduleConfig.getAuthToken(), true);
        }
    };

    @BeforeEach
    public void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);

        moduleConfig = new ModuleConfig();
        moduleConfig.setRequestTimeoutMs(5);
        moduleConfig.setIdentityResolutionEndpoint("https://test.com/idres");
        moduleConfig.setAuthToken("secret_auth_token");
        moduleConfig.setTreatmentRate(0.9f);

        target = new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(
                moduleConfig, jacksonMapper, httpClient, random);

    }

    @Test
    public void shouldAddResolvedEids() {
        // given
        final Uid providedUid = Uid.builder().id("id1").atype(2).build();
        final Eid providedEid = Eid.builder().source("some.source.com")
                .uids(Collections.singletonList(providedUid)).build();

        final Uid enrichedUid = Uid.builder().id("id2").atype(3).build();
        final Eid enrichedEid = Eid.builder().source("liveintent.com")
                .uids(Collections.singletonList(enrichedUid)).build();

        final User user = User.builder().eids(Collections.singletonList(providedEid)).build();
        final BidRequest bidRequest = BidRequest.builder().id("request").user(user).build();

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);

        // when
        when(random.nextFloat()).thenReturn(0.89f);

        when(mockResponse.getBody())
                .thenReturn("{\"eids\": [ { \"source\": \"" + enrichedEid.getSource()
                        + "\", \"uids\": [ { \"atype\": "
                        + enrichedUid.getAtype()
                        + ", \"id\" : \""
                        + enrichedUid.getId() + "\" } ] } ] }");

        when(httpClient.post(
            eq(moduleConfig.getIdentityResolutionEndpoint()),
            argThat(bearerAuthHeaderMatcher),
            eq(jacksonMapper.encodeToString(bidRequest)),
            eq(moduleConfig.getRequestTimeoutMs()))
        ).thenReturn(Future.succeededFuture(mockResponse));

        final Future<InvocationResult<AuctionRequestPayload>> future =
                target.call(AuctionRequestPayloadImpl.of(bidRequest), auctionInvocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.payloadUpdate().apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest().getUser().getEids()).isEqualTo(List.of(providedEid, enrichedEid));
    }

    @Test
    public void shouldNotAttemptToResolveEids() {
        // given
        final Uid providedUid = Uid.builder().id("id1").atype(2).build();
        final Eid providedEid = Eid.builder().source("some.source.com")
                .uids(Collections.singletonList(providedUid)).build();

        final User user = User.builder().eids(Collections.singletonList(providedEid)).build();
        final BidRequest bidRequest = BidRequest.builder().id("request").user(user).build();

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        // when
        when(random.nextFloat()).thenReturn(0.91f);

        final Future<InvocationResult<AuctionRequestPayload>> future =
                target.call(AuctionRequestPayloadImpl.of(bidRequest), auctionInvocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void shouldCreateUserWhenNotPresent() {
        // given
        final Uid enrichedUid = Uid.builder().id("id2").atype(3).build();
        final Eid enrichedEid = Eid.builder().source("liveintent.com")
                .uids(Collections.singletonList(enrichedUid)).build();

        final BidRequest bidRequest = BidRequest.builder().id("request").build();

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);

        // when
        when(random.nextFloat()).thenReturn(0.89f);

        when(mockResponse.getBody())
                .thenReturn("{\"eids\": [{ \"source\": \""
                        + enrichedEid.getSource()
                        + "\", \"uids\": [{ \"atype\": "
                        + enrichedUid.getAtype() + ", \"id\" : \""
                        + enrichedUid.getId() + "\" }]}]}");

        when(
                httpClient.post(
                        eq(moduleConfig.getIdentityResolutionEndpoint()),
                        argThat(bearerAuthHeaderMatcher),
                        eq(jacksonMapper.encodeToString(bidRequest)),
                        eq(moduleConfig.getRequestTimeoutMs())
                )
        ).thenReturn(Future.succeededFuture(mockResponse));

        final Future<InvocationResult<AuctionRequestPayload>> future
                = target.call(AuctionRequestPayloadImpl.of(bidRequest), auctionInvocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest()
                .getUser()
                .getEids()).isEqualTo(Collections.singletonList(enrichedEid));
    }
}
