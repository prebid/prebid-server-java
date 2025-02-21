package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cache.proto.request.bid.BidCacheRequest;
import org.prebid.server.cache.proto.request.bid.BidPutObject;
import org.prebid.server.cache.proto.response.bid.BidCacheResponse;
import org.prebid.server.cache.proto.response.bid.CacheObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vast.VastModifier;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class CoreCacheServiceTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private HttpClient httpClient;
    @Mock
    private EventsService eventsService;
    @Mock(strictness = LENIENT)
    private VastModifier vastModifier;
    @Mock
    private Metrics metrics;
    @Mock
    private UUIDIdGenerator idGenerator;

    private Clock clock;

    private CoreCacheService target;

    private EventsContext eventsContext;

    private Timeout timeout;

    private Timeout expiredTimeout;

    @BeforeEach
    public void setUp() throws MalformedURLException, JsonProcessingException {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                null,
                false,
                false,
                null,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        eventsContext = EventsContext.builder().auctionId("auctionId").build();

        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);

        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        given(httpClient.post(anyString(), any(), any(), anyLong())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, null, mapper.writeValueAsString(
                        BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))))));
    }

    @Test
    public void getCachedAssetURLShouldReturnExpectedValue() {
        // when
        final String cachedAssetURL = target.getCachedAssetURLTemplate();

        // then
        assertThat(cachedAssetURL).isEqualTo("http://cache-service-host/cache?uuid=");
    }

    @Test
    public void cacheBidsOpenrtbShouldNeverCallCacheServiceIfNoBidsPassed() {
        // when
        target.cacheBidsOpenrtb(emptyList(), null, null, null);

        // then
        verifyNoInteractions(httpClient);
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        target.cacheBidsOpenrtb(
                singletonList(givenBidInfo(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        verify(httpClient).post(anyString(), any(), any(), eq(500L));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(givenBidInfo(identity())),
                givenAuctionContext().toBuilder()
                        .timeoutContext(TimeoutContext.of(0, expiredTimeout, 0))
                        .build(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isNotNull().hasMessage("Timeout has been exceeded");
        assertThat(result.getHttpCall()).isNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldStoreWinUrl() {
        // given
        final EventsContext eventsContext = EventsContext.builder()
                .auctionId("auctionId")
                .enabledForAccount(true)
                .enabledForRequest(true)
                .build();
        // when
        target.cacheBidsOpenrtb(
                singletonList(givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder")),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        verify(eventsService).winUrl(
                eq("bidId1"),
                eq("bidder"),
                eq("accountId"),
                eq(true),
                eq(EventsContext.builder()
                        .enabledForAccount(true)
                        .enabledForRequest(true)
                        .auctionId("auctionId")
                        .build()));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateReadingHttpResponseFails() throws JsonProcessingException {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        verify(metrics).updateCacheRequestFailedTime(eq("accountId"), anyLong());

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .requestHeaders(givenDebugHeaders())
                        .endpoint("http://cache-service/cache")
                        .requestBody(request.getBody())
                        .requestUri(request.getUri())
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseCodeIsNot200() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(503, "response");
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("HTTP status code 503");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestBody(request.getBody())
                        .requestUri(request.getUri())
                        .responseStatus(503)
                        .requestHeaders(givenDebugHeaders())
                        .responseBody("response")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseBodyCouldNotBeParsed() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "response");
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("Cannot parse response: response");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestUri(request.getUri())
                        .requestBody(request.getBody())
                        .requestHeaders(givenDebugHeaders())
                        .responseStatus(200)
                        .responseBody("response")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateCacheEntriesNumberDoesNotMatchBidsNumber()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "{}");
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class)
                .hasMessage("The number of response cache objects doesn't match with bids");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestBody(request.getBody())
                        .requestUri(request.getUri())
                        .requestHeaders(givenDebugHeaders())
                        .responseStatus(200).responseBody("{}")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedDebugInfo() throws JsonProcessingException {
        // given
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestUri(request.getUri())
                        .requestBody(request.getBody())
                        .requestHeaders(givenDebugHeaders())
                        .responseStatus(200)
                        .responseBody("{\"responses\":[{\"uuid\":\"uuid1\"}]}")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldUseApiKeyWhenProvided() throws MalformedURLException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                true,
                false,
                null,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getHttpCall().getRequestHeaders().get(HttpUtil.X_PBC_API_KEY_HEADER.toString()))
                .containsExactly("ApiKey");
        assertThat(captureBidCacheRequestHeaders().get(HttpUtil.X_PBC_API_KEY_HEADER.toString()))
                .isEqualTo("ApiKey");
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedCacheBids() {
        // given
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).hasSize(1)
                .containsEntry(bidinfo.getBid(), CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        final ObjectNode bidExt2 = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().bidid("generatedId").build(),
                        emptyMap()));
        final String receivedBid2Adm = "adm2";
        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder1")
                .toBuilder().ttl(1).build();
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2").adm(receivedBid2Adm).ext(bidExt2),
                BidType.video, "bidder2").toBuilder().ttl(2).build();

        final EventsContext eventsContext = EventsContext.builder()
                .auctionId("auctionId")
                .auctionTimestamp(1000L)
                .build();

        // when
        target.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        // Second value is adm length for each
        verify(metrics).updateCacheCreativeSize(eq("accountId"), eq(0), eq(MetricName.json));
        verify(metrics).updateCacheCreativeSize(eq("accountId"), eq(4), eq(MetricName.json));
        verify(metrics).updateCacheCreativeSize(eq("accountId"), eq(4), eq(MetricName.xml));

        verify(metrics).updateCacheCreativeTtl(eq("accountId"), eq(1), eq(MetricName.json));
        verify(metrics).updateCacheCreativeTtl(eq("accountId"), eq(2), eq(MetricName.json));

        final Bid bid1 = bidInfo1.getBid();
        final Bid bid2 = bidInfo2.getBid();

        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts())
                .containsExactly(
                        BidPutObject.builder().aid("auctionId").type("json")
                                .value(mapper.valueToTree(bid1)).ttlseconds(1).build(),
                        BidPutObject.builder().aid("auctionId").type("json")
                                .value(mapper.valueToTree(bid2)).ttlseconds(2).build(),
                        BidPutObject.builder().aid("auctionId").type("xml")
                                .value(new TextNode(receivedBid2Adm)).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromBidInfo() throws IOException {
        // given
        final BidInfo bidInfo = givenBidInfo(
                bidBuilder -> bidBuilder.id("bidId1").exp(10),
                impBuilder -> impBuilder.id("impId1").exp(20))
                .toBuilder().ttl(1000).build();

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder().shouldCacheBids(true).build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(BidPutObject::getTtlseconds)
                .containsOnly(1000);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(1000);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithNoTtlAndSetEmptyTtl() throws IOException {
        // given
        final BidInfo givenBidInfo = givenBidInfo(bidBuilder -> bidBuilder.id("bidId1")).toBuilder().ttl(null).build();

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(givenBidInfo),
                givenAuctionContext(),
                CacheContext.builder().shouldCacheBids(true).build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(BidPutObject::getExpiry)
                .containsNull();

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBids() {
        // given
        final BidInfo bidInfo = givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bidInfo.getBid(), CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForVideoBids() {
        // given
        final BidInfo bidInfo = givenBidInfo(bidBuilder -> bidBuilder.id("bidId1").adm("adm1"), BidType.video,
                "bidder");

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bidInfo.getBid(), CacheInfo.of(null, "uuid1", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldAddDebugLogCacheAlongWithBids() throws IOException {
        // given
        final BidInfo bidInfo = givenBidInfo(bidBuilder -> bidBuilder.id("bidId").adm("adm1"), BidType.video, "bidder");
        final Imp imp = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp)))
                .toBuilder().cachedDebugLog(new CachedDebugLog(true, 100, null, jacksonMapper)).build();

        // when
        target.cacheBidsOpenrtb(
                singletonList(bidInfo),
                auctionContext,
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .extracting(BidPutObject::getKey)
                .contains("log_null");
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(
                        CacheObject.of("uuid1"),
                        CacheObject.of("uuid2"),
                        CacheObject.of("videoUuid1")))));

        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.video, "bidder1");
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2"), BidType.banner, "bidder2");

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(2)
                .containsOnly(
                        entry(bidInfo1.getBid(), CacheInfo.of("uuid1", "videoUuid1", null, null)),
                        entry(bidInfo2.getBid(), CacheInfo.of("uuid2", null, null, null)));
    }

    @Test
    public void cacheBidsOpenrtbShouldUpdateVastXmlPutObjectWithKeyWhenBidHasCategoryDuration() throws IOException {
        // given
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        final BidInfo bidInfo1 = givenBidInfo(
                builder -> builder.id("bid1").adm("adm"), BidType.video, "bidder")
                .toBuilder().category("bid1Category").build();

        given(idGenerator.generateId()).willReturn("randomId");

        // when
        target.cacheBidsOpenrtb(
                singletonList(bidInfo1),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        BidPutObject.builder().type("json").value(mapper.valueToTree(Bid.builder().id("bid1")
                                .adm("adm").build())).aid("auctionId").build(),
                        BidPutObject.builder().key("bid1Category_randomId").type("xml")
                                .value(new TextNode("adm")).aid("auctionId").build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotUpdateVastXmlPutObjectWithKeyWhenDoesNotHaveCatDur() throws IOException {
        // given
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final BidInfo bidInfo1 = givenBidInfo(
                builder -> builder.id("bid1").impid("impId1").adm("adm"), BidType.video, "bidder");

        given(vastModifier.createBidVastXml(any(), any(), any(), any(), any(), any(), any())).willReturn("adm");

        // when
        target.cacheBidsOpenrtb(
                singletonList(bidInfo1),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        BidPutObject.builder()
                                .type("json")
                                .aid("auctionId")
                                .value(mapper.valueToTree(bidInfo1.getBid()))
                                .build(),
                        BidPutObject.builder().type("xml").aid("auctionId").value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldRemoveCatDurPrefixFromVideoUuidFromResponse() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(CacheObject.of("uuid"), CacheObject.of("catDir_randomId")))));
        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bid1").impid("impId1").adm("adm"),
                BidType.video, "bidder").toBuilder().category("catDir").build();
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        given(idGenerator.generateId()).willReturn("randomId");

        // when
        final Future<CacheServiceResult> resultFuture = target.cacheBidsOpenrtb(
                singletonList(bidInfo1),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getCacheBids()).hasSize(1)
                .containsEntry(Bid.builder().id("bid1").impid("impId1").adm("adm").build(),
                        CacheInfo.of("uuid", "randomId", null, null));
    }

    @Test
    public void cachePutObjectsShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<BidCacheResponse> future = target.cachePutObjects(
                singletonList(BidPutObject.builder().build()), true, emptySet(), "", "",
                expiredTimeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isNotNull().hasMessage("Timeout has been exceeded");
    }

    @Test
    public void cachePutObjectsShouldReturnResultWithEmptyListWhenPutObjectsIsEmpty() {
        // when
        final Future<BidCacheResponse> result = target.cachePutObjects(emptyList(), true,
                emptySet(), null, null, null);

        // then
        verifyNoInteractions(httpClient);
        assertThat(result.result().getResponses()).isEmpty();
    }

    @Test
    public void cachePutObjectsShould() throws IOException {
        // given
        final BidPutObject firstBidPutObject = BidPutObject.builder()
                .type("json")
                .bidid("bidId1")
                .bidder("bidder1")
                .timestamp(1L)
                .value(new TextNode("vast"))
                .ttlseconds(1)
                .build();
        final BidPutObject secondBidPutObject = BidPutObject.builder()
                .type("xml")
                .bidid("bidId2")
                .bidder("bidder2")
                .timestamp(1L)
                .value(new TextNode("VAST"))
                .ttlseconds(2)
                .build();
        final BidPutObject thirdBidPutObject = BidPutObject.builder()
                .type("text")
                .bidid("bidId3")
                .bidder("bidder3")
                .timestamp(1L)
                .value(new TextNode("VAST"))
                .ttlseconds(3)
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"))
                .willReturn(new TextNode("VAST"))
                .willReturn(new TextNode("updatedVast"));

        // when
        target.cachePutObjects(
                asList(firstBidPutObject, secondBidPutObject, thirdBidPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(12), eq(MetricName.json));
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(4), eq(MetricName.xml));
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(11), eq(MetricName.unknown));

        verify(metrics).updateCacheCreativeTtl(eq("account"), eq(1), eq(MetricName.json));
        verify(metrics).updateCacheCreativeTtl(eq("account"), eq(2), eq(MetricName.xml));
        verify(metrics).updateCacheCreativeTtl(eq("account"), eq(3), eq(MetricName.unknown));

        verify(vastModifier).modifyVastXml(true, singleton("bidder1"), firstBidPutObject, "account", "pbjs");
        verify(vastModifier).modifyVastXml(true, singleton("bidder1"), secondBidPutObject, "account", "pbjs");

        final BidPutObject modifiedFirstBidPutObject = firstBidPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .value(new TextNode("modifiedVast"))
                .build();
        final BidPutObject modifiedSecondBidPutObject = secondBidPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .build();
        final BidPutObject modifiedThirdBidPutObject = thirdBidPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .value(new TextNode("updatedVast"))
                .build();

        assertThat(captureBidCacheRequest().getPuts())
                .containsExactly(modifiedFirstBidPutObject, modifiedSecondBidPutObject, modifiedThirdBidPutObject);
    }

    @Test
    public void cachePutObjectsShouldUseApiKeyWhenProvided() throws MalformedURLException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                true,
                false,
                null,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        final BidPutObject firstBidPutObject = BidPutObject.builder()
                .type("json")
                .bidid("bidId1")
                .bidder("bidder1")
                .timestamp(1L)
                .value(new TextNode("vast"))
                .build();

        // when
        target.cachePutObjects(
                singletonList(firstBidPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        assertThat(captureBidCacheRequestHeaders().get(HttpUtil.X_PBC_API_KEY_HEADER.toString()))
                .isEqualTo("ApiKey");
    }

    @Test
    public void cacheBidsOpenrtbShouldPrependTraceInfoWhenEnabled() throws IOException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                false,
                true,
                null,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        given(idGenerator.generateId())
                .willReturn("1-high-entropy-cache-id")
                .willReturn("2-high-entropy-cache-id")
                .willReturn("3-high-entropy-cache-id");

        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder1")
                .toBuilder()
                .build();
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2").adm("adm"), BidType.video, "bidder2")
                .toBuilder()
                .build();

        final EventsContext eventsContext = EventsContext.builder()
                .auctionId("auctionId")
                .auctionTimestamp(1000L)
                .build();

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(captureBidCacheRequest().getPuts())
                .containsExactly(
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("json")
                                .key("accountId-1-high-entrop")
                                .value(mapper.valueToTree(bidInfo1.getBid()))
                                .build(),
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("json")
                                .key("accountId-2-high-entrop")
                                .value(mapper.valueToTree(bidInfo2.getBid()))
                                .build(),
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("xml")
                                .key("accountId-3-high-entrop")
                                .value(new TextNode("adm"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldPrependTraceInfoWithDatacenterWhenEnabled() throws IOException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                false,
                true,
                "apacific",
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        given(idGenerator.generateId())
                .willReturn("1-high-entropy-cache-id-foo-bar-")
                .willReturn("2-high-entropy-cache-id-foo-bar-")
                .willReturn("3-high-entropy-cache-id-foo-bar-");

        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder1")
                .toBuilder()
                .build();
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2").adm("adm"), BidType.video, "bidder2")
                .toBuilder()
                .build();

        final EventsContext eventsContext = EventsContext.builder()
                .auctionId("auctionId")
                .auctionTimestamp(1000L)
                .build();

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(captureBidCacheRequest().getPuts())
                .containsExactly(
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("json")
                                .key("accountId-apac-1-high-entropy-ca")
                                .value(mapper.valueToTree(bidInfo1.getBid()))
                                .build(),
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("json")
                                .key("accountId-apac-2-high-entropy-ca")
                                .value(mapper.valueToTree(bidInfo2.getBid()))
                                .build(),
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("xml")
                                .key("accountId-apac-3-high-entropy-ca")
                                .value(new TextNode("adm"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotPrependTraceInfoToLowEntoryCacheIds() throws IOException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                false,
                true,
                "apacific",
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        given(idGenerator.generateId()).willReturn("low-entropy");

        final BidInfo bidInfo = givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder1")
                .toBuilder()
                .build();

        final EventsContext eventsContext = EventsContext.builder()
                .auctionId("auctionId")
                .auctionTimestamp(1000L)
                .build();

        // when
        final Future<CacheServiceResult> future = target.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(captureBidCacheRequest().getPuts())
                .containsExactly(
                        BidPutObject.builder()
                                .aid("auctionId")
                                .type("json")
                                .value(mapper.valueToTree(bidInfo.getBid()))
                                .build());
    }

    @Test
    public void cachePutObjectsShouldPrependTraceInfoWhenEnabled() throws IOException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                false,
                true,
                null,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        given(idGenerator.generateId()).willReturn("high-entropy-cache-id");

        final BidPutObject bidPutObject = BidPutObject.builder()
                .type("text")
                .value(new TextNode("VAST"))
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"));

        // when
        target.cachePutObjects(
                singletonList(bidPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        final BidPutObject modifiedBidPutObject = bidPutObject.toBuilder()
                .key("account-high-entropy-")
                .value(new TextNode("modifiedVast"))
                .build();

        assertThat(captureBidCacheRequest().getPuts()).containsExactly(modifiedBidPutObject);
    }

    @Test
    public void cachePutObjectsShouldPrependTraceInfoWithDatacenterWhenEnabled() throws IOException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                false,
                true,
                "apacific",
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        given(idGenerator.generateId()).willReturn("high-entropy-cache-id-foo-bar");

        final BidPutObject bidPutObject = BidPutObject.builder()
                .type("text")
                .value(new TextNode("VAST"))
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"));

        // when
        target.cachePutObjects(
                singletonList(bidPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        final BidPutObject modifiedBidPutObject = bidPutObject.toBuilder()
                .key("account-apac-high-entropy-cac")
                .value(new TextNode("modifiedVast"))
                .build();

        assertThat(captureBidCacheRequest().getPuts()).containsExactly(modifiedBidPutObject);
    }

    @Test
    public void cachePutObjectsShouldNotPrependTraceInfoToPassedInKey() throws IOException {
        // given
        target = new CoreCacheService(
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                "ApiKey",
                false,
                true,
                null,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        final BidPutObject bidPutObject = BidPutObject.builder()
                .type("text")
                .key("passed-in-key")
                .value(new TextNode("VAST"))
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"));

        // when
        target.cachePutObjects(
                singletonList(bidPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        final BidPutObject modifiedBidPutObject = bidPutObject.toBuilder()
                .key("passed-in-key")
                .value(new TextNode("modifiedVast"))
                .build();

        assertThat(captureBidCacheRequest().getPuts()).containsExactly(modifiedBidPutObject);
        verify(idGenerator, never()).generateId();
    }

    @Test
    public void cacheBidsOpenrtbShouldNotEmitEmptyTtlMetrics() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder1")
                .toBuilder()
                .build();
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2"), BidType.video, "bidder2")
                .toBuilder()
                .build();

        final EventsContext eventsContext = EventsContext.builder()
                .auctionId("auctionId")
                .auctionTimestamp(1000L)
                .build();

        // when
        target.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        verify(metrics, never()).updateCacheCreativeTtl(any(), any(), any());
    }

    @Test
    public void cachePutObjectsShouldNotEmitEmptyTtlMetrics() {
        // given
        final BidPutObject firstBidPutObject = BidPutObject.builder()
                .type("json")
                .value(new TextNode("vast"))
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"));

        // when
        target.cachePutObjects(
                singletonList(firstBidPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        verify(metrics, never()).updateCacheCreativeTtl(any(), any(), any());
    }

    private AuctionContext givenAuctionContext(UnaryOperator<Account.AccountBuilder> accountCustomizer,
                                               UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        final Account.AccountBuilder accountBuilder = Account.builder()
                .id("accountId");
        final BidRequest.BidRequestBuilder bidRequestBuilder = BidRequest.builder()
                .id("auctionId")
                .imp(singletonList(givenImp(identity())));
        return AuctionContext.builder()
                .account(accountCustomizer.apply(accountBuilder).build())
                .bidRequest(bidRequestCustomizer.apply(bidRequestBuilder).build())
                .timeoutContext(TimeoutContext.of(0, timeout, 0))
                .build();
    }

    private AuctionContext givenAuctionContext(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return givenAuctionContext(identity(), bidRequestCustomizer);
    }

    private AuctionContext givenAuctionContext() {
        return givenAuctionContext(identity(), identity());
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return givenBidInfo(bidCustomizer, UnaryOperator.identity());
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer,
                                        UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidInfo.builder()
                .bid(bidCustomizer.apply(Bid.builder()).build())
                .correspondingImp(impCustomizer.apply(Imp.builder()).build())
                .bidder("bidder")
                .bidType(BidType.banner)
                .build();
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer,
                                        BidType bidType,
                                        String bidder) {
        return BidInfo.builder()
                .bid(bidCustomizer.apply(Bid.builder()).build())
                .correspondingImp(givenImp(UnaryOperator.identity()))
                .bidder(bidder)
                .bidType(bidType)
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static CacheHttpRequest givenCacheHttpRequest(Bid... bids) throws JsonProcessingException {
        final List<BidPutObject> bidPutObjects;
        if (bids != null) {
            bidPutObjects = new ArrayList<>();
            for (Bid bid : bids) {
                bidPutObjects.add(BidPutObject.builder()
                        .aid("auctionId")
                        .type("json")
                        .value(mapper.valueToTree(bid))
                        .build());
            }
        } else {
            bidPutObjects = null;
        }
        return CacheHttpRequest.of(
                "http://cache-service/cache",
                mapper.writeValueAsString(BidCacheRequest.of(bidPutObjects)));
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, response);
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(anyString(), any(), captor.capture(), anyLong());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }

    private MultiMap captureBidCacheRequestHeaders() {
        final ArgumentCaptor<MultiMap> captor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).post(anyString(), captor.capture(), anyString(), anyLong());
        return captor.getValue();
    }

    private Map<String, List<String>> givenDebugHeaders() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", singletonList("application/json"));
        headers.put("Content-Type", singletonList("application/json;charset=utf-8"));
        return headers;
    }
}
