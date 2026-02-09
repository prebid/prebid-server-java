package org.prebid.server.hooks.modules.id5.userid.fetch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.config.Id5IdModuleProperties;
import org.prebid.server.hooks.modules.id5.userid.v1.fetch.HttpFetchClient;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchResponse;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpFetchClientTest {

    private static final String URL = "http://example.test/fetch";

    private JacksonMapper mapper;
    private HttpClient httpClient;
    private VersionInfo versionInfo;
    private Clock fixedClock;
    private Id5IdModuleProperties props;

    @BeforeEach
    void setUp() {
        mapper = new JacksonMapper(new ObjectMapper());
        httpClient = Mockito.mock(HttpClient.class);
        versionInfo = Mockito.mock(VersionInfo.class);
        when(versionInfo.getVersion()).thenReturn("1.2.3");
        fixedClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        props = new Id5IdModuleProperties();
        props.setProviderName("pbs");
    }

    @Test
    void shouldReturnEmptyOnNon200Response() {
        // given
        final long partnerId = 123L;
        final String expectedUrl = URL + "/" + partnerId + ".json";
        when(httpClient.post(eq(expectedUrl), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(
                        HttpClientResponse.of(503, MultiMap.caseInsensitiveMultiMap(), "oops"))
                );

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper, fixedClock, versionInfo, props);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().id("r1").build());
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = auctionInvocationContext(timeout,
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(), false);

        // when
        final Id5UserId result = client.fetch(partnerId, payload, invocation).result();

        // then
        assertThat(result.toEIDs()).isEmpty();
    }

    @Test
    void shouldReturnEmptyOnException() {
        // given
        final long partnerId = 123L;
        final String expectedUrl = URL + "/" + partnerId + ".json";
        when(httpClient.post(eq(expectedUrl), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.failedFuture(new RuntimeException("boom")));

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper, fixedClock, versionInfo, props);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().id("r1").build());
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = auctionInvocationContext(timeout,
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(), false);

        // when
        final Id5UserId result = client.fetch(partnerId, payload, invocation).result();

        // then
        assertThat(result.toEIDs()).isEmpty();
    }

    @Test
    void shouldParseSuccessfulResponse() {
        // given
        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .uids(List.of(Uid.builder().id("id5-xyz").build()))
                .build();
        final FetchResponse response = new FetchResponse(java.util.Map.of("id5", new FetchResponse.UserId(eid)));
        final String body = mapper.encodeToString(response);
        final String expectedUrl123b = URL + "/123.json";
        when(httpClient.post(eq(expectedUrl123b), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), body))
                );

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper,
                fixedClock, versionInfo, props);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().id("r1").build());
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = auctionInvocationContext(timeout,
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(), false);

        // when
        final Id5UserId result = client.fetch(123L, payload, invocation).result();

        // then
        assertThat(result.toEIDs()).hasSize(1);
        assertThat(result.toEIDs().getFirst().getSource()).isEqualTo("id5-sync.com");
    }

    @Test
    void shouldBuildRequestWithExpectedFieldsAndUseTimeout() {
        // given
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        final long remainingTime = 100L;
        final Timeout timeout = mock(Timeout.class);
        when(timeout.remaining()).thenReturn(remainingTime);
        when(httpClient.post(anyString(), headersCaptor.capture(), bodyCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(), mapper.encodeToString(new FetchResponse(null)))));

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper,
                fixedClock, versionInfo, props);

        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().bundle("com.example.app").build())
                .site(Site.builder().domain("example.com").ref("https://ref.example").build())
                .device(Device.builder()
                        .ifa("ifa-123")
                        .ua("UA/1.0")
                        .ip("203.0.113.10")
                        .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                        .ext(ExtDevice.of(3, null))
                        .build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final Privacy privacy = Privacy.builder()
                .gdpr("1")
                .consentString("CONSENT_STRING")
                .ccpa(Ccpa.of("1YNN"))
                .coppa(1)
                .gpp("GPP_STRING")
                .gppSid(List.of(7, 8))
                .build();
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc-1").build())
                .privacyContext(PrivacyContext.of(privacy, org.prebid.server.privacy.gdpr.model.TcfContext.empty()))
                .build();

        final AuctionInvocationContext invocation = auctionInvocationContext(
                timeout,
                auctionContext,
                false);

        // when
        client.fetch(999L, payload, invocation).result();

        // then
        final MultiMap headers = headersCaptor.getValue();
        final String contentType = headers.get("Content-Type");
        assertThat(contentType).isEqualTo("application/json;charset=utf-8");

        // then: request body
        final String captured = bodyCaptor.getValue();
        final Map<String, Object> json = mapper.decodeValue(captured, new TypeReference<>() {
        });
        assertThat(((Number) json.get("partner")).longValue()).isEqualTo(999L);
        assertThat(json.get("version")).isEqualTo("1.2.3");
        assertThat(json.get("bundle")).isEqualTo("com.example.app");
        assertThat(json.get("domain")).isEqualTo("example.com");
        assertThat(json.get("maid")).isEqualTo("ifa-123");
        assertThat(json.get("ua")).isEqualTo("UA/1.0");
        assertThat(json.get("ref")).isEqualTo("https://ref.example");
        assertThat(json.get("ipv4")).isEqualTo("203.0.113.10");
        assertThat(json.get("ipv6")).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertThat(json.get("att")).isEqualTo("3");
        assertThat(json.get("gdpr")).isEqualTo("1");
        assertThat(json.get("gdpr_consent")).isEqualTo("CONSENT_STRING");
        assertThat(json.get("us_privacy")).isEqualTo("1YNN");
        assertThat(json.get("coppa")).isEqualTo("1");
        assertThat(json.get("gpp_string")).isEqualTo("GPP_STRING");
        assertThat(json.get("gpp_sid")).isEqualTo("7,8");
        assertThat(json.get("origin")).isEqualTo("pbs-java");
        assertThat(json.get("provider")).isEqualTo("pbs");
        assertThat(json.get("ts")).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(json.get("_trace")).isEqualTo(false);

        verify(httpClient, times(1)).post(eq(URL + "/999.json"),
                any(MultiMap.class), anyString(), eq(remainingTime));
    }

    @Test
    void shouldSetTraceWhenDebugEnabled() {
        // given
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        final Timeout timeout = mock(Timeout.class);
        when(timeout.remaining()).thenReturn(100L);
        when(httpClient.post(anyString(), any(MultiMap.class), bodyCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(), mapper.encodeToString(new FetchResponse(null)))));

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper, fixedClock, versionInfo, props);

        final AuctionInvocationContext invocation = auctionInvocationContext(timeout,
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(), true);

        // when
        client.fetch(999L,
                AuctionRequestPayloadImpl.of(BidRequest.builder().id("r1").build()), invocation).result();

        // then: __trace should be true
        final Map<String, Object> json = mapper.decodeValue(bodyCaptor.getValue(), new TypeReference<>() {
        });
        assertThat(json.get("_trace")).isEqualTo(true);
    }

    @Test
    void shouldHandleEmptyGppSidList() {
        // given
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), any(MultiMap.class), bodyCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(), mapper.encodeToString(new FetchResponse(null)))));

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper,
                fixedClock, versionInfo, props);

        final Privacy privacy = Privacy.builder()
                .gpp("GPP_STRING")
                .gppSid(List.of()) // Empty list should result in null gpp_sid
                .build();
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc-1").build())
                .privacyContext(PrivacyContext.of(privacy, org.prebid.server.privacy.gdpr.model.TcfContext.empty()))
                .build();
        final AuctionInvocationContext invocation = auctionInvocationContext(
                new TimeoutFactory(Clock.systemUTC()).create(1000), auctionContext, false);

        // when
        client.fetch(999L,
                AuctionRequestPayloadImpl.of(BidRequest.builder().id("r1").build()), invocation).result();

        // then: gpp_sid should be null when list is empty (not empty string "")
        final Map<String, Object> json = mapper.decodeValue(bodyCaptor.getValue(), new TypeReference<>() {
        });
        assertThat(json.get("gpp_string")).isEqualTo("GPP_STRING");
        assertThat(json.get("gpp_sid")).isNull();
    }

    public static Stream<Arguments> publisherSources() {

        return Stream.of(
                Arguments.of("site",
                        (BiConsumer<BidRequestBuilder, Publisher>) (rq, p) ->
                                rq.site(Site.builder()
                                        .publisher(p)
                                        .build())),
                Arguments.of("app",
                        (BiConsumer<BidRequestBuilder, Publisher>) (rq1, p1) ->
                                rq1.app(App.builder()
                                        .publisher(p1)
                                        .build()))
        );
    }

    @MethodSource("publisherSources")
    @ParameterizedTest(name = "from {0}")
    @SuppressWarnings("unchecked")
    void shouldIncludePublisher(String ignore, BiConsumer<BidRequestBuilder, Publisher> publisherSetter) {
        // given
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), any(MultiMap.class), bodyCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(), mapper.encodeToString(new FetchResponse(null)))));

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper,
                fixedClock, versionInfo, props);

        final BidRequestBuilder bidRequestBuilder = BidRequest.builder();
        publisherSetter.accept(bidRequestBuilder, Publisher.builder()
                .id("pub-123")
                .domain("pub.domain")
                .name("Test Publisher")
                .build());
        final BidRequest bidRequest = bidRequestBuilder.build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc-1").build())
                .build();
        final AuctionInvocationContext invocation = auctionInvocationContext(
                new TimeoutFactory(Clock.systemUTC()).create(1000), auctionContext, false);

        // when
        client.fetch(999L, payload, invocation).result();

        // then
        final Map<String, Object> json = mapper.decodeValue(bodyCaptor.getValue(), new TypeReference<>() {
        });
        final Map<String, Object> metadata = (Map<String, Object>) json.get("providerMetadata");

        assertThat(metadata.get("publisher")).isNotNull();
        final Map<String, Object> publisher = (Map<String, Object>) metadata.get("publisher");
        assertThat(publisher.get("id")).isEqualTo("pub-123");
        assertThat(publisher.get("name")).isEqualTo("Test Publisher");
        assertThat(publisher.get("domain")).isEqualTo("pub.domain");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeAllProviderMetadataFieldsWhenAllPresent() {
        // given
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), any(MultiMap.class), bodyCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(), mapper.encodeToString(new FetchResponse(null)))));

        final Id5IdModuleProperties moduleProps = new Id5IdModuleProperties();
        moduleProps.setProviderName("comprehensive-provider");
        moduleProps.setPartner(789L);

        final HttpFetchClient client = new HttpFetchClient(URL, httpClient, mapper,
                fixedClock, versionInfo, moduleProps);

        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder()
                                .id("pub-789")
                                .name("Comprehensive Publisher")
                                .build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("mobile-app", "3.5"))
                        .data(ExtRequestPrebidData.of(List.of("bidder1", "bidder2"), null))
                        .build()))
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc-1").build())
                .build();
        final AuctionInvocationContext invocation = auctionInvocationContext(
                new TimeoutFactory(Clock.systemUTC()).create(1000), auctionContext, false);

        // when
        client.fetch(999L, payload, invocation).result();

        // then
        final Map<String, Object> json = mapper.decodeValue(bodyCaptor.getValue(), new TypeReference<>() {
        });
        final Map<String, Object> metadata = (Map<String, Object>) json.get("providerMetadata");

        // Verify all fields are present
        assertThat(metadata.get("id5ModuleConfig")).isNotNull();
        assertThat(metadata.get("publisher")).isNotNull();
        assertThat(metadata.get("channel")).isEqualTo("mobile-app");
        assertThat(metadata.get("channelVersion")).isEqualTo("3.5");
        assertThat(metadata.get("bidders")).isNotNull();

        final Map<String, Object> config = (Map<String, Object>) metadata.get("id5ModuleConfig");
        assertThat(config.get("providerName")).isEqualTo("comprehensive-provider");
        assertThat(((Number) config.get("partner")).longValue()).isEqualTo(789L);

        final Map<String, Object> publisher = (Map<String, Object>) metadata.get("publisher");
        assertThat(publisher.get("id")).isEqualTo("pub-789");

        final List<String> bidders = (List<String>) metadata.get("bidders");
        assertThat(bidders).containsExactly("bidder1", "bidder2");
    }

    private static AuctionInvocationContextImpl auctionInvocationContext(Timeout timeout,
                                                                         AuctionContext auctionContext,
                                                                         boolean debugEnabled) {
        return AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout,
                        org.prebid.server.model.Endpoint.openrtb2_auction),
                auctionContext, debugEnabled, null, null);
    }

}
