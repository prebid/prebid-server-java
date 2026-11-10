package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.UserAgent;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheResult;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityCache;
import org.prebid.server.hooks.modules.intentiq.identity.cache.KeyType;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.IntentiqIdentityModuleContext;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.FirstPartyKeyExtractor;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class IntentiqIdentityProcessedAuctionRequestHookTest {

    private static final JacksonMapper MAPPER = new JacksonMapper(ObjectMapperProvider.mapper());
    private static final JsonMerger MERGER = new JsonMerger(MAPPER);
    private static final String API_ENDPOINT = "https://dev.example.com/resolve";
    private static final String EIDS_BODY =
            "{\"data\":{\"eids\":[{\"source\":\"intentiq.com\",\"uids\":[{\"id\":\"abc\",\"atype\":1}]}]}}";
    private static final String EMPTY_BODY = "{\"data\":{\"eids\":[]}}";

    @Mock
    private HttpClient httpClient;

    @Mock
    private AuctionInvocationContext invocationContext;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    @Captor
    private ArgumentCaptor<MultiMap> headersCaptor;

    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final IntentiqIdentityMetrics metrics = new IntentiqIdentityMetrics(metricRegistry);

    @BeforeEach
    public void setUp() {
        when(invocationContext.accountConfig()).thenReturn(MAPPER.mapper().createObjectNode());
    }

    // Counter-asserting tests all run with partner id "123"; metrics are suffixed with the dpi.
    private long counter(String name) {
        return metricRegistry.counter("modules.module.intentiq-identity.custom." + name + "_123").getCount();
    }

    private IntentiqIdentityProcessedAuctionRequestHook target(String apiEndpoint, String partnerId) {
        return target(apiEndpoint, partnerId, null);
    }

    private IntentiqIdentityProcessedAuctionRequestHook target(String apiEndpoint, String partnerId,
                                                               IdentityCache cache) {
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setApiEndpoint(apiEndpoint);
        properties.setPartnerId(partnerId);
        properties.setTimeout(1000L);
        if (cache != null) {
            properties.getCache().setEnabled(true);
        }
        final ConfigResolver configResolver = new ConfigResolver(MAPPER.mapper(), MERGER, properties);
        return new IntentiqIdentityProcessedAuctionRequestHook(
                configResolver, httpClient, MAPPER, cache, new FirstPartyKeyExtractor(10), metrics);
    }

    @Test
    public void codeShouldReturnExpectedValue() {
        assertThat(target(API_ENDPOINT, "123").code())
                .isEqualTo("intentiq-identity-processed-auction-request-hook");
    }

    @Test
    public void callShouldEnrichUserEidsWhenApiReturnsEids() {
        // given
        givenResponse(EIDS_BODY);
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());

        // when
        final InvocationResult<AuctionRequestPayload> result = target(API_ENDPOINT, "123")
                .call(payload, invocationContext).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        final Eid enriched = applyUpdate(result, payload).getUser().getEids().getFirst();
        assertThat(enriched.getSource()).isEqualTo("intentiq.com");
        assertThat(enriched.getUids().getFirst().getId()).isEqualTo("abc");
    }

    @Test
    public void callShouldFallBackToGlobalPropertiesWhenAccountConfigIsNull() {
        // given — host-level-only config: no account override, so accountConfig() is null in production
        // (JsonMergePatch rejects a null patch, which previously failed the whole hook invocation).
        givenResponse(EIDS_BODY);
        when(invocationContext.accountConfig()).thenReturn(null);
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());

        // when
        final InvocationResult<AuctionRequestPayload> result = target(API_ENDPOINT, "123")
                .call(payload, invocationContext).result();

        // then — resolver falls back to global properties and the hook still enriches
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(applyUpdate(result, payload).getUser().getEids().getFirst().getSource())
                .isEqualTo("intentiq.com");
    }

    @Test
    public void callShouldAppendResolvedEidsAfterExistingUserEids() {
        // given
        givenResponse(EIDS_BODY);
        final Eid existing = Eid.builder()
                .source("pubcid.org")
                .uids(singletonList(Uid.builder().id("existing-uid").build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(singletonList(existing)).build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final InvocationResult<AuctionRequestPayload> result = target(API_ENDPOINT, "123")
                .call(payload, invocationContext).result();

        // then
        assertThat(applyUpdate(result, payload).getUser().getEids())
                .extracting(Eid::getSource)
                .containsExactly("pubcid.org", "intentiq.com");
    }

    @Test
    public void callShouldSendGetWithConstantsPartnerAndSrvrReq() {
        // given
        givenCapturedResponse();

        // when
        target(API_ENDPOINT, "partner-42").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext);

        // then
        assertThat(urlCaptor.getValue())
                .isEqualTo(API_ENDPOINT + "?at=39&mi=10&dpi=partner-42&pt=17&dpn=1&srvrReq=true&source=pbjv");
    }

    @Test
    public void callShouldApplyAccountLevelPartnerIdOverride() {
        // given
        givenCapturedResponse();
        final ObjectNode account = MAPPER.mapper().createObjectNode().put("partner-id", "acct-9");
        when(invocationContext.accountConfig()).thenReturn(account);

        // when
        target(API_ENDPOINT, "global-1").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext);

        // then — account-level partner-id wins over the global value
        assertThat(urlCaptor.getValue()).contains("&dpi=acct-9");
    }

    @Test
    public void callShouldAppendDeviceIpIpv6AndUserAgent() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ip("125.253.50.47").ipv6("2001:db8::1").ua("Mozilla/5.0 (iPhone)").build())
                .build();

        // when
        target(API_ENDPOINT, "383342646").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue())
                .isEqualTo(API_ENDPOINT + "?at=39&mi=10&dpi=383342646&pt=17&dpn=1&srvrReq=true&source=pbjv"
                        + "&ip=125.253.50.47&ipv6=2001%3Adb8%3A%3A1&uas=Mozilla%2F5.0%20%28iPhone%29");
    }

    @Test
    public void callShouldSendDeviceIfaAsPcidWithIdtype4ForMobile() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ifa("maid-AbC").devicetype(1).build())
                .build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).contains("&pcid=maid-AbC&idtype=4");
    }

    @Test
    public void callShouldUppercasePcidAndSendIdtype8ForCtv() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ifa("rida-abc").devicetype(3).build())
                .build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).contains("&pcid=RIDA-ABC&idtype=8");
    }

    @Test
    public void callShouldNotSendPcidWhenLimitAdTracking() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ifa("maid-1").lmt(1).build())
                .build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).doesNotContain("pcid").doesNotContain("idtype");
    }

    @Test
    public void callShouldSendSiteDomainAsRef() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder().site(Site.builder().domain("example.com").build()).build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).contains("&ref=example.com");
    }

    @Test
    public void callShouldSendExistingIiqUniversalIdAsIiquid() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(singletonList(Eid.builder()
                        .source("intentiq.com")
                        .uids(singletonList(Uid.builder().id("IIQ-UID-1").build()))
                        .build())).build())
                .build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).contains("&iiquid=IIQ-UID-1");
    }

    @Test
    public void callShouldAppendGdprUsPrivacyAndGppAsQueryParamsFromTopLevelFields() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.builder().gdpr(1).usPrivacy("1YNN").gpp("DBABMA~CONSENT").gppSid(List.of(2, 6)).build())
                .user(User.builder().consent("CO-TCF-STRING").build())
                .build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then — consent goes in the gdpr-consent header (GDPR guide), not the query string
        assertThat(urlCaptor.getValue())
                .contains("&gdpr=1")
                .contains("&us_privacy=1YNN")
                .contains("&gpp=DBABMA%7ECONSENT")
                .contains("&gpp_sid=2%2C6")
                .doesNotContain("gdpr_consent");
        assertThat(headersCaptor.getValue().get("gdpr-consent")).isEqualTo("CO-TCF-STRING");
    }

    @Test
    public void callShouldSendConsentInHeaderFromUserExtFallback() {
        // given
        givenCapturedResponse();
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.builder().ext(ExtRegs.of(1, "1NYN", null, null)).build())
                .user(User.builder().ext(ExtUser.builder().consent("EXT-TCF-STRING").build()).build())
                .build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue())
                .contains("&gdpr=1")
                .contains("&us_privacy=1NYN")
                .doesNotContain("gdpr_consent");
        assertThat(headersCaptor.getValue().get("gdpr-consent")).isEqualTo("EXT-TCF-STRING");
    }

    @Test
    public void callShouldNotAppendConsentParamsOrHeaderWhenAbsent() {
        // given
        givenCapturedResponse();

        // when
        target(API_ENDPOINT, "123").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext);

        // then
        assertThat(urlCaptor.getValue())
                .doesNotContain("gdpr")
                .doesNotContain("us_privacy")
                .doesNotContain("gpp");
        assertThat(headersCaptor.getValue().get("gdpr-consent")).isNull();
    }

    @Test
    public void callShouldAppendUaHintsFromDeviceSuaInUhParam() throws Exception {
        // given
        givenCapturedResponse();
        final UserAgent sua = UserAgent.builder()
                .source(2)
                .browsers(List.of(
                        new BrandVersion("Chromium", List.of("108", "0", "5359", "125"), null),
                        new BrandVersion("Google Chrome", List.of("108", "0", "5359", "125"), null),
                        new BrandVersion("Not?A_Brand", List.of("8", "0", "0", "0"), null)))
                .platform(new BrandVersion("Windows", List.of("15", "0", "0"), null))
                .mobile(0)
                .architecture("x86")
                .bitness("64")
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(Device.builder().sua(sua).build()).build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then — uh is the numeric-keyed UA-CH JSON the IntentIQ backend expects (brands sorted, major vs full version)
        final JsonNode uh = MAPPER.mapper().readTree(extractParam(urlCaptor.getValue(), "uh"));
        assertThat(uh.get("0").asText())
                .isEqualTo("\"Chromium\";v=\"108\", \"Google Chrome\";v=\"108\", \"Not?A_Brand\";v=\"8\"");
        assertThat(uh.get("8").asText()).isEqualTo("\"Chromium\";v=\"108.0.5359.125\", "
                + "\"Google Chrome\";v=\"108.0.5359.125\", \"Not?A_Brand\";v=\"8.0.0.0\"");
        assertThat(uh.get("1").asText()).isEqualTo("?0");
        assertThat(uh.get("2").asText()).isEqualTo("\"Windows\"");
        assertThat(uh.get("3").asText()).isEqualTo("\"x86\"");
        assertThat(uh.get("4").asText()).isEqualTo("\"64\"");
        assertThat(uh.get("6").asText()).isEqualTo("\"15.0.0\"");
        assertThat(uh.has("5")).isFalse();
        assertThat(uh.has("7")).isFalse();
    }

    @Test
    public void callShouldNotAppendUhWhenSuaSourceIsNotHighEntropy() {
        // given — the IntentIQ backend only consumes hints when sua.source == 2 (high-entropy)
        givenCapturedResponse();
        final UserAgent sua = UserAgent.builder()
                .source(1)
                .browsers(List.of(new BrandVersion("Chrome", List.of("120"), null)))
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(Device.builder().sua(sua).build()).build();

        // when
        target(API_ENDPOINT, "123").call(AuctionRequestPayloadImpl.of(bidRequest), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).doesNotContain("uh=");
    }

    @Test
    public void callShouldCarryTerminationCauseFromResponseInModuleContext() {
        // given
        givenResponse("{\"data\":{\"eids\":[{\"source\":\"intentiq.com\",\"uids\":[{\"id\":\"abc\"}]}]},\"tc\":5}");

        // when
        final InvocationResult<AuctionRequestPayload> result = target(API_ENDPOINT, "123")
                .call(AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        final IntentiqIdentityModuleContext ctx = (IntentiqIdentityModuleContext) result.moduleContext();
        assertThat(ctx.abTestUuid()).isNull();
        assertThat(ctx.terminationCause()).isEqualTo(5L);
    }

    @Test
    public void callShouldTreatEmptyStringDataAsValidEmptyResponseNotApiError() {
        // given — GDPR/invalid BE responses return data as an empty string (""), not an object
        givenResponse("{\"adt\":4,\"ct\":2,\"data\":\"\",\"cttl\":600000,\"tc\":36}");

        // when
        final InvocationResult<AuctionRequestPayload> result = target(API_ENDPOINT, "123")
                .call(AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then — parsed cleanly as an empty result, not counted as an API error
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(counter("api.success")).isEqualTo(1);
        assertThat(counter("api.error")).isZero();
        assertThat(counter("eids.none")).isEqualTo(1);
        assertThat(counter("tc.36")).isEqualTo(1);
    }

    @Test
    public void callShouldPassResponseCttlToNegativeCacheOnMiss() {
        // given — BE supplies the suppression window via cttl on an empty response
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(CacheResult.miss()));
        givenResponse("{\"data\":{\"eids\":[]},\"cttl\":600000}");
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        verify(cache).putNegative(any(), eq(600_000L));
    }

    @Test
    public void callShouldReturnNoActionWhenApiReturnsNoEids() {
        // given
        givenResponse(EMPTY_BODY);

        // when
        final InvocationResult<AuctionRequestPayload> result = target(API_ENDPOINT, "123")
                .call(AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
    }

    @Test
    public void callShouldReturnNoActionWhenHttpCallFails() {
        // given
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.failedFuture(new RuntimeException("boom")));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target(API_ENDPOINT, "123")
                .call(AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnNoActionAndSkipHttpWhenApiEndpointBlank() {
        // when
        final InvocationResult<AuctionRequestPayload> result = target(null, "123")
                .call(AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        verifyNoInteractions(httpClient);
    }

    @Test
    public void callShouldUseCachedEidsAndSkipHttpOnCacheHit() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        final Eid cached = Eid.builder()
                .source("intentiq.com")
                .uids(singletonList(Uid.builder().id("cached-uid").build()))
                .build();
        when(cache.get(any()))
                .thenReturn(Future.succeededFuture(
                        CacheResult.hit(singletonList(cached), KeyType.FIRST_PARTY, CacheResult.Layer.L1)));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(applyUpdate(result, payload).getUser().getEids()).containsExactly(cached);
        verifyNoInteractions(httpClient);
    }

    @Test
    public void callShouldFetchAndPopulateCacheOnMiss() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(CacheResult.miss()));
        givenResponse(EIDS_BODY);
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        verify(cache).put(any(), any(), anyLong());
    }

    @Test
    public void callShouldWriteNegativeCacheEntryWhenApiReturnsNoEidsOnMiss() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(CacheResult.miss()));
        givenResponse(EMPTY_BODY);
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then — EMPTY_BODY carries no cttl, so the negative entry uses the default suppression TTL
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        verify(cache).putNegative(any(), eq(0L));
    }

    @Test
    public void callShouldReturnNoActionAndSkipHttpOnNegativeCacheHit() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(
                CacheResult.negative(KeyType.FIRST_PARTY, CacheResult.Layer.L2)));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(counter("cache.l2.negative.hit.first_party")).isEqualTo(1);
        assertThat(counter("cache.miss.first_party")).isEqualTo(1);
        assertThat(counter("cache.l1.hit.first_party")).isZero();
        verifyNoInteractions(httpClient);
    }

    @Test
    public void callShouldIncrementApiSuccessAndEnrichedOnEnrichment() {
        // given
        givenResponse(EIDS_BODY);

        // when
        target(API_ENDPOINT, "123").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(counter("api.success")).isEqualTo(1);
        assertThat(counter("enriched")).isEqualTo(1);
    }

    @Test
    public void callShouldRecordRawTerminationCauseFromResponse() {
        // given
        givenResponse("{\"data\":{\"eids\":[]},\"tc\":20}");

        // when
        target(API_ENDPOINT, "123").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(counter("tc.20")).isEqualTo(1);
    }

    @Test
    public void callShouldDropOutOfRangeTerminationCause() {
        // given — an out-of-range tc emits no counter
        givenResponse("{\"data\":{\"eids\":[]},\"tc\":120088}");

        // when
        target(API_ENDPOINT, "123").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(counter("tc.120088")).isZero();
    }

    @Test
    public void callShouldIncrementApiErrorWhenHttpFails() {
        // given
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.failedFuture(new RuntimeException("boom")));

        // when
        target(API_ENDPOINT, "123").call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(counter("api.error")).isEqualTo(1);
    }

    @Test
    public void callShouldIncrementCacheHitOnCacheHit() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(CacheResult.hit(singletonList(Eid.builder()
                .source("intentiq.com")
                .uids(singletonList(Uid.builder().id("u").build()))
                .build()), KeyType.FIRST_PARTY, CacheResult.Layer.L1)));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        assertThat(counter("cache.l1.hit.first_party")).isEqualTo(1);
        assertThat(counter("cache.miss.first_party")).isZero();
    }

    @Test
    public void callShouldIncrementCacheMissOnCacheMiss() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(CacheResult.miss()));
        givenResponse(EIDS_BODY);
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        assertThat(counter("cache.miss.first_party")).isEqualTo(1);
        assertThat(counter("cache.l1.hit.first_party")).isZero();
    }

    @Test
    public void callShouldMarkInProgressBeforeFetchingOnCacheMiss() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(CacheResult.miss()));
        givenResponse(EIDS_BODY);
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then — marker written so concurrent requests don't fire a duplicate, then the call is made
        verify(cache).putInProgress(any());
        verify(httpClient).get(any(), any(), anyLong());
    }

    @Test
    public void callShouldReturnNoActionAndSkipHttpOnInProgressCacheResult() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(
                CacheResult.inProgress(KeyType.FIRST_PARTY, CacheResult.Layer.L1)));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        final InvocationResult<AuctionRequestPayload> result =
                target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then — a resolution is already in flight: do not fire a duplicate, do not enrich
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        verifyNoInteractions(httpClient);
        verify(cache, never()).putInProgress(any());
    }

    @Test
    public void callShouldIncrementCacheInProgressOnInProgressResult() {
        // given
        final IdentityCache cache = mock(IdentityCache.class);
        when(cache.get(any())).thenReturn(Future.succeededFuture(
                CacheResult.inProgress(KeyType.FIRST_PARTY, CacheResult.Layer.L1)));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(
                BidRequest.builder().device(Device.builder().ifa("MAID-1").build()).build());

        // when
        target(API_ENDPOINT, "123", cache).call(payload, invocationContext).result();

        // then
        assertThat(counter("cache.l1.in_progress.first_party")).isEqualTo(1);
    }

    private void givenResponse(String body) {
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200, null, body)));
    }

    private void givenCapturedResponse() {
        when(httpClient.get(urlCaptor.capture(), headersCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200, null, EMPTY_BODY)));
    }

    private static BidRequest applyUpdate(InvocationResult<AuctionRequestPayload> result,
                                          AuctionRequestPayload payload) {
        final PayloadUpdate<AuctionRequestPayload> update = result.payloadUpdate();
        return update.apply(payload).bidRequest();
    }

    private static String extractParam(String url, String key) {
        final int start = url.indexOf("&" + key + "=") + key.length() + 2;
        final int end = url.indexOf('&', start);
        final String value = url.substring(start, end < 0 ? url.length() : end);
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
