package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.IdResResponse;
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
    private UserFpdActivityMask userFpdActivityMask;

    @Mock
    private HttpClient httpClient;

    @Mock(strictness = LENIENT)
    private LiveIntentOmniChannelProperties properties;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @Mock
    private AuctionInvocationContext auctionInvocationContext;

    @Mock
    private AuctionContext auctionContext;

    private LiveIntentOmniChannelIdentityProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        given(properties.getRequestTimeoutMs()).willReturn(5L);
        given(properties.getIdentityResolutionEndpoint()).willReturn("https://test.com/idres");
        given(properties.getAuthToken()).willReturn("auth_token");
        given(properties.getTreatmentRate()).willReturn(1.0f);

        given(auctionInvocationContext.auctionContext()).willReturn(auctionContext);
        given(auctionContext.getActivityInfrastructure()).willReturn(activityInfrastructure);
        given(activityInfrastructure.isAllowed(any(), any())).willReturn(true);

        target = new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(
                properties, userFpdActivityMask, MAPPER, httpClient, 0.01d);
    }

    @Test
    public void creationShouldFailOnInvalidIdentityUrl() {
        given(properties.getIdentityResolutionEndpoint()).willReturn("invalid_url");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(
                        properties, userFpdActivityMask, MAPPER, httpClient, 0.01d));
    }

    @Test
    public void geoPassingRestrictionShouldBeRespected() {
        // given
        final Geo givenGeo = Geo.builder()
                .lat(52.51671856406936f)
                .lon(13.377639726342583f)
                .city("Berlin")
                .country("Germany")
                .build();
        final Device givenDevice = Device.builder()
                .geo(givenGeo)
                .ip("192.168.127.12")
                .ifa("foo")
                .macsha1("bar")
                .macmd5("baz")
                .dpidsha1("boo")
                .dpidmd5("far")
                .didsha1("zoo")
                .didmd5("goo")
                .build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").device(givenDevice).build();

        final Geo expectedGeo = givenGeo.toBuilder()
                .country(null)
                .city(null)
                .lat(52.52f)
                .lon(13.38f)
                .build();
        final Device expectedDevice = givenDevice.toBuilder()
                .geo(expectedGeo)
                .ip("192.168.127.0")
                .ifa(null)
                .macsha1(null)
                .macmd5(null)
                .dpidsha1(null)
                .dpidmd5(null)
                .didsha1(null)
                .didmd5(null)
                .build();
        final BidRequest expectedBidRequest = givenBidRequest.toBuilder().device(expectedDevice).build();

        final Eid expectedEid = Eid.builder().source("liveintent.com").build();

        final String responseBody = MAPPER.encodeToString(IdResResponse.of(List.of(expectedEid)));
        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)));

        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_GEO), any())).willReturn(false);
        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_UFPD), any())).willReturn(false);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();
        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);

        verify(httpClient).post(
                eq("https://test.com/idres"),
                argThat(headers -> headers.contains("Authorization", "Bearer auth_token", true)),
                eq(MAPPER.encodeToString(expectedBidRequest)),
                eq(5L));
    }

    @Test
    public void tidPassingRestrictionShouldBeRespected() {
        // given
        final Source givenSource = Source.builder().tid("tid1").build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").source(givenSource).build();

        final Source expectedSource = givenSource.toBuilder().tid(null).build();
        final BidRequest expectedBidRequest = givenBidRequest.toBuilder().source(expectedSource).build();

        final Eid expectedEid = Eid.builder().source("liveintent.com").build();

        final String responseBody = MAPPER.encodeToString(IdResResponse.of(List.of(expectedEid)));
        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)));

        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_TID), any())).willReturn(false);
        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_UFPD), any())).willReturn(false);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();
        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);

        verify(httpClient).post(
                eq("https://test.com/idres"),
                argThat(headers -> headers.contains("Authorization", "Bearer auth_token", true)),
                eq(MAPPER.encodeToString(expectedBidRequest)),
                eq(5L));
    }

    @Test
    public void eidPassingRestrictionShouldBeRespected() {
        // given
        final Uid givenUid = Uid.builder().id("id1").atype(2).build();
        final Eid givenEid = Eid.builder().source("some.source.com").uids(singletonList(givenUid)).build();
        final User givenUser = User.builder().eids(singletonList(givenEid)).build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").user(givenUser).build();

        final User expectedUser = givenUser.toBuilder().eids(null).build();
        final BidRequest expectedBidRequest = givenBidRequest.toBuilder().user(expectedUser).build();

        final Eid expectedEid = Eid.builder().source("liveintent.com").build();

        final String responseBody = MAPPER.encodeToString(IdResResponse.of(List.of(expectedEid)));
        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)));

        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_EIDS), any())).willReturn(false);
        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_UFPD), any())).willReturn(false);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();
        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);

        verify(httpClient).post(
                eq("https://test.com/idres"),
                argThat(headers -> headers.contains("Authorization", "Bearer auth_token", true)),
                eq(MAPPER.encodeToString(expectedBidRequest)),
                eq(5L));
    }

    @Test
    public void callShouldEnrichUserEidsWithRequestedEids() {
        // given
        final Uid givenUid = Uid.builder().id("id1").atype(2).build();
        final Eid givenEid = Eid.builder().source("some.source.com").uids(singletonList(givenUid)).build();
        final User givenUser = User.builder().eids(singletonList(givenEid)).build();
        final BidRequest givenBidRequest = BidRequest.builder().id("request").user(givenUser).build();

        final Eid expectedEid = Eid.builder()
                .source("liveintent.com")
                .uids(singletonList(Uid.builder().id("id2").atype(3).build()))
                .build();

        final String responseBody = MAPPER.encodeToString(IdResResponse.of(List.of(expectedEid)));
        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)));

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();

        // then
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

        final Eid expectedEid = Eid.builder()
                .source("liveintent.com")
                .uids(singletonList(Uid.builder().id("id2").atype(3).build()))
                .build();

        final String responseBody = MAPPER.encodeToString(IdResResponse.of(List.of(expectedEid)));
        given(httpClient.post(any(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)));

        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null, null, false, null, null);

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target.call(AuctionRequestPayloadImpl.of(givenBidRequest), auctionInvocationContext).result();

        // then
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

        given(properties.getTreatmentRate()).willReturn(0.0f);

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
