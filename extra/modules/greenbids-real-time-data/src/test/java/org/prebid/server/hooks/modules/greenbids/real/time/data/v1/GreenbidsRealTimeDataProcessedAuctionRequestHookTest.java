package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ExplorationResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.Ortb2ImpExtResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ThresholdCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.TagsImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GreenbidsRealTimeDataProcessedAuctionRequestHookTest {

    private static final long CACHE_EXPIRATION_MINUTES = 15;

    private static final String GEO_LITE_COUNTRY_PATH =
            "src/test/resources/GeoLite2-Country.mmdb";

    private static final String MODEL_PATH = "src/test/resources/models_pbuid=lelp-pbuid.onnx";

    private static final String JSON_PATH = "src/test/resources/thresholds_pbuid=lelp-pbuid.json";

    private static final String GOOGLE_CLOUD_PROJECT = "test_project";

    private static final String GCS_BUCKET_NAME = "test_bucket";

    private GreenbidsRealTimeDataProcessedAuctionRequestHook hook;

    private JacksonMapper jacksonMapper;

    private Cache<String, OnnxModelRunner> modelCacheWithExpiration;

    private Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;

    private ModelCache modelCache;

    private ThresholdCache thresholdCache;

    @BeforeEach
    public void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);
        modelCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build();
        thresholdsCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build();
        hook = new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                mapper,
                modelCacheWithExpiration,
                thresholdsCacheWithExpiration,
                GEO_LITE_COUNTRY_PATH,
                GOOGLE_CLOUD_PROJECT,
                GCS_BUCKET_NAME);
        modelCache = new ModelCache(
                null, null, null, modelCacheWithExpiration);
        thresholdCache = new ThresholdCache(
                null, null, null,
                jacksonMapper.mapper(), thresholdsCacheWithExpiration);

    }

    @Test
    public void shouldExitEarlyIfPartnerNotActivatedInBidRequest() throws IOException, OrtException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, null);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        modelCache.getCache().cleanUp();
        thresholdCache.getCache().cleanUp();
        modelCache.getCache().put("onnxModelRunner_lelp-pbuid", givenOnnxModelRunner());
        thresholdCache.getCache().put("throttlingThresholds_lelp-pbuid", givenThrottlingThresholds());

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = hook
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);

        assertThat(result.analyticsTags()).isNull();
        assertThat(resultBidRequest).usingRecursiveComparison()
                .ignoringFields("imp.ext._children.tid")
                .isEqualTo(bidRequest);
    }

    @Test
    public void shouldExitEarlyIfThresholdIsNotAvailable() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 0.0001;
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        modelCache.getCache().cleanUp();
        thresholdCache.getCache().cleanUp();
        modelCache.getCache().put("onnxModelRunner_lelp-pbuid", givenOnnxModelRunner());

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = hook
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);

        assertThat(result.analyticsTags()).isNull();
        assertThat(resultBidRequest).usingRecursiveComparison()
                .ignoringFields("imp.ext._children.tid")
                .isEqualTo(bidRequest);
    }

    @Test
    public void shouldExitEarlyIfModelIsNotAvailable() throws IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 0.0001;
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        modelCache.getCache().cleanUp();
        thresholdCache.getCache().cleanUp();
        thresholdCache.getCache().put("throttlingThresholds_lelp-pbuid", givenThrottlingThresholds());

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = hook
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);

        assertThat(result.analyticsTags()).isNull();
        assertThat(resultBidRequest).usingRecursiveComparison()
                .ignoringFields("imp.ext._children.tid")
                .isEqualTo(bidRequest);
    }

    @Test
    public void shouldNotFilterBiddersAndReturnAnalyticsTagWhenExploration() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 1.0;
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        modelCache.getCache().cleanUp();
        thresholdCache.getCache().cleanUp();
        modelCache.getCache().put("onnxModelRunner_lelp-pbuid", givenOnnxModelRunner());
        thresholdCache.getCache().put("throttlingThresholds_lelp-pbuid", givenThrottlingThresholds());

        final AnalyticsResult expectedAnalyticsResult = expectedAnalyticsResult(true, true);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = hook
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);

        assertThat(result.analyticsTags()).isNotNull();
        assertThat(result.analyticsTags()).usingRecursiveComparison()
                .ignoringFields(
                        "activities.results"
                                + ".values._children"
                                + ".adunitcodevalue._children"
                                + ".greenbids._children.fingerprint",
                        "activities.results.values._children.adunitcodevalue._children.tid")
                .isEqualTo(toAnalyticsTags(List.of(expectedAnalyticsResult)));
        assertThat(resultBidRequest).usingRecursiveComparison()
                .ignoringFields("imp.ext._children.tid")
                .isEqualTo(bidRequest);
    }

    @Test
    public void shouldNotFilterBiddersIfAnyFeatureNotAvailable() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 0.0001;
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), null, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        modelCache.getCache().cleanUp();
        thresholdCache.getCache().cleanUp();
        modelCache.getCache().put("onnxModelRunner_lelp-pbuid", givenOnnxModelRunner());
        thresholdCache.getCache().put("throttlingThresholds_lelp-pbuid", givenThrottlingThresholds());

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = hook
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);

        assertThat(result.analyticsTags()).isNull();
        assertThat(resultBidRequest).usingRecursiveComparison()
                .ignoringFields("imp.ext._children.tid")
                .isEqualTo(bidRequest);
    }

    @Test
    public void shouldFilterBiddersBasedOnModelResults() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 0.0001;
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final BidRequest expectedBidRequest = expectedUpdatedBidRequest(
                request -> request, jacksonMapper, explorationRate);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        modelCache.getCache().cleanUp();
        thresholdCache.getCache().cleanUp();
        modelCache.getCache().put("onnxModelRunner_lelp-pbuid", givenOnnxModelRunner());
        thresholdCache.getCache().put("throttlingThresholds_lelp-pbuid", givenThrottlingThresholds());

        final AnalyticsResult expectedAnalyticsResult = expectedAnalyticsResult(false, false);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = hook
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);

        assertThat(result.analyticsTags()).isNotNull();
        assertThat(result.analyticsTags()).usingRecursiveComparison()
                .ignoringFields(
                        "activities.results"
                                + ".values._children"
                                + ".adunitcodevalue._children"
                                + ".greenbids._children.fingerprint",
                        "activities.results.values._children.adunitcodevalue._children.tid")
                .isEqualTo(toAnalyticsTags(List.of(expectedAnalyticsResult)));
        assertThat(resultBidRequest).usingRecursiveComparison()
                .ignoringFields("imp.ext._children.tid")
                .isEqualTo(expectedBidRequest);
    }

    private OnnxModelRunner givenOnnxModelRunner() throws OrtException, IOException {
        final byte[] onnxModelBytes = Files.readAllBytes(Paths.get(MODEL_PATH));
        return new OnnxModelRunner(onnxModelBytes);
    }

    private ThrottlingThresholds givenThrottlingThresholds() throws IOException {
        final JsonNode thresholdsJsonNode = jacksonMapper.mapper().readTree(
                Files.newInputStream(Paths.get(JSON_PATH)));
        return jacksonMapper.mapper()
                .treeToValue(thresholdsJsonNode, ThrottlingThresholds.class);
    }

    private ObjectNode givenImpExt() {
        final ObjectNode bidderNode = jacksonMapper.mapper().createObjectNode();

        final ObjectNode rubiconNode = jacksonMapper.mapper().createObjectNode();
        rubiconNode.put("accountId", 1001);
        rubiconNode.put("siteId", 267318);
        rubiconNode.put("zoneId", 1861698);
        bidderNode.set("rubicon", rubiconNode);

        final ObjectNode appnexusNode = jacksonMapper.mapper().createObjectNode();
        appnexusNode.put("placementId", 123456);
        bidderNode.set("appnexus", appnexusNode);

        final ObjectNode pubmaticNode = jacksonMapper.mapper().createObjectNode();
        pubmaticNode.put("publisherId", "156209");
        pubmaticNode.put("adSlot", "slot1@300x250");
        bidderNode.set("pubmatic", pubmaticNode);

        final ObjectNode prebidNode = jacksonMapper.mapper().createObjectNode();
        prebidNode.set("bidder", bidderNode);

        final ObjectNode extNode = jacksonMapper.mapper().createObjectNode();
        extNode.set("prebid", prebidNode);
        extNode.set("tid", null);

        return extNode;
    }

    private AuctionInvocationContext givenAuctionInvocationContext(AuctionContext auctionContext) {
        final AuctionInvocationContext invocationContext = mock(AuctionInvocationContext.class);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);
        return invocationContext;
    }

    private static AuctionContext givenAuctionContext(
            BidRequest bidRequest,
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {
        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest);

        return auctionContextCustomizer.apply(auctionContextBuilder).build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Imp> imps,
            Device device,
            ExtRequest extRequest) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request")
                .imp(imps)
                .site(givenSite(site -> site))
                .device(device)
                .ext(extRequest)).build();
    }

    private static BidRequest expectedUpdatedBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            JacksonMapper jacksonMapper,
            Double explorationRate) {
        final Banner banner = givenBanner();

        final ObjectNode bidderNode = jacksonMapper.mapper().createObjectNode();
        final ObjectNode prebidNode = jacksonMapper.mapper().createObjectNode();
        prebidNode.set("bidder", bidderNode);

        final ObjectNode extNode = jacksonMapper.mapper().createObjectNode();
        extNode.set("prebid", prebidNode);
        extNode.set("tid", null);

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(extNode)
                .banner(banner)
                .build();

        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request")
                .imp(List.of(imp))
                .site(givenSite(site -> site))
                .device(givenDevice(device -> device))
                .ext(givenExtRequest(explorationRate))).build();
    }

    private static Device givenDevice(UnaryOperator<Device.DeviceBuilder> deviceCustomizer) {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        return deviceCustomizer.apply(Device.builder().ua(userAgent).ip("151.101.194.216")).build();
    }

    private static Site givenSite(UnaryOperator<Site.SiteBuilder> siteCustomizer) {
        return siteCustomizer.apply(Site.builder().domain("www.leparisien.fr")).build();
    }

    private static ExtRequest givenExtRequest(Double explorationRate) {
        final ObjectNode greenbidsNode = new ObjectMapper().createObjectNode();
        greenbidsNode.put("pbuid", "lelp-pbuid");
        greenbidsNode.put("targetTpr", 0.60);
        greenbidsNode.put("explorationRate", explorationRate);

        final ObjectNode analyticsNode = new ObjectMapper().createObjectNode();
        analyticsNode.set("greenbids-rtd", greenbidsNode);

        return ExtRequest.of(ExtRequestPrebid
                .builder()
                .analytics(analyticsNode)
                .build());
    }

    private static Banner givenBanner() {
        final Format format = Format.builder()
                .w(320)
                .h(50)
                .build();

        return Banner.builder()
                .format(Collections.singletonList(format))
                .w(240)
                .h(400)
                .build();
    }

    private Tags toAnalyticsTags(List<AnalyticsResult> analyticsResults) {
        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                "greenbids-filter",
                "success",
                toResults(analyticsResults))));
    }

    private List<Result> toResults(List<AnalyticsResult> analyticsResults) {
        return analyticsResults.stream()
                .map(this::toResult)
                .toList();
    }

    private Result toResult(AnalyticsResult analyticsResult) {
        return ResultImpl.of(
                analyticsResult.getStatus(),
                toObjectNode(analyticsResult.getValues()),
                AppliedToImpl.builder()
                        .bidders(Collections.singletonList(analyticsResult.getBidder()))
                        .impIds(Collections.singletonList(analyticsResult.getImpId()))
                        .build());
    }

    private ObjectNode toObjectNode(Map<String, Ortb2ImpExtResult> values) {
        return values != null ? jacksonMapper.mapper().valueToTree(values) : null;
    }

    private static AnalyticsResult expectedAnalyticsResult(Boolean isExploration, Boolean isKeptInAuction) {
        return AnalyticsResult.of(
                "success",
                Map.of("adunitcodevalue", expectedOrtb2ImpExtResult(isExploration, isKeptInAuction)),
                null,
                null);
    }

    private static Ortb2ImpExtResult expectedOrtb2ImpExtResult(Boolean isExploration, Boolean isKeptInAuction) {
        return Ortb2ImpExtResult.of(expectedExplorationResult(isExploration, isKeptInAuction), null);
    }

    private static ExplorationResult expectedExplorationResult(Boolean isExploration, Boolean isKeptInAuction) {
        final Map<String, Boolean> keptInAuction = new HashMap<>();
        keptInAuction.put("appnexus", isKeptInAuction);
        keptInAuction.put("pubmatic", isKeptInAuction);
        keptInAuction.put("rubicon", isKeptInAuction);
        return ExplorationResult.of(null, keptInAuction, isExploration);
    }
}
