package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.analytics.reporter.greenbids.model.ExplorationResult;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.config.DatabaseReaderFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInvocationService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunnerFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunnerWithThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThresholdCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholdsFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.filter.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenDevice;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenDeviceWithoutUserAgent;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenImpExt;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenSite;

@ExtendWith(MockitoExtension.class)
public class GreenbidsRealTimeDataProcessedAuctionRequestHookTest {

    @Mock
    private Cache<String, OnnxModelRunner> modelCacheWithExpiration;

    @Mock
    private Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;

    @Mock(strictness = LENIENT)
    private DatabaseReaderFactory databaseReaderFactory;

    @Mock
    private DatabaseReader dbReader;

    private GreenbidsRealTimeDataProcessedAuctionRequestHook target;

    @BeforeEach
    public void setUp() throws IOException {
        final Storage storage = StorageOptions.newBuilder()
                .setProjectId("test_project").build().getService();
        final FilterService filterService = new FilterService();
        final OnnxModelRunnerFactory onnxModelRunnerFactory = new OnnxModelRunnerFactory();
        final ThrottlingThresholdsFactory throttlingThresholdsFactory = new ThrottlingThresholdsFactory();
        final ModelCache modelCache = new ModelCache(
                storage,
                "test_bucket",
                modelCacheWithExpiration,
                "onnxModelRunner_",
                Vertx.vertx(),
                onnxModelRunnerFactory);
        final ThresholdCache thresholdCache = new ThresholdCache(
                storage,
                "test_bucket",
                TestBidRequestProvider.MAPPER,
                thresholdsCacheWithExpiration,
                "throttlingThresholds_",
                Vertx.vertx(),
                throttlingThresholdsFactory);
        final OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds = new OnnxModelRunnerWithThresholds(
                modelCache,
                thresholdCache);
        when(databaseReaderFactory.getDatabaseReader()).thenReturn(dbReader);
        final GreenbidsInferenceDataService greenbidsInferenceDataService = new GreenbidsInferenceDataService(
                databaseReaderFactory,
                TestBidRequestProvider.MAPPER);
        final GreenbidsInvocationService greenbidsInvocationService = new GreenbidsInvocationService();
        target = new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                TestBidRequestProvider.MAPPER,
                filterService,
                onnxModelRunnerWithThresholds,
                greenbidsInferenceDataService,
                greenbidsInvocationService);
    }

    @Test
    public void callShouldExitEarlyWhenPartnerNotActivatedInBidRequest() {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Device device = givenDevice(identity());
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, null);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.analyticsTags()).isNull();
    }

    @Disabled("Broken until dbReader is mocked")
    @Test
    public void callShouldNotFilterBiddersAndReturnAnalyticsTagWhenExploration() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 1.0;
        final Device device = givenDevice(identity());
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);
        when(modelCacheWithExpiration.getIfPresent("onnxModelRunner_test-pbuid"))
                .thenReturn(givenOnnxModelRunner());
        when(thresholdsCacheWithExpiration.getIfPresent("throttlingThresholds_test-pbuid"))
                .thenReturn(givenThrottlingThresholds());

        final AnalyticsResult expectedAnalyticsResult = expectedAnalyticsResult(true, true);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();

        // then
        final ActivityImpl activity = (ActivityImpl) result.analyticsTags().activities().getFirst();
        final ResultImpl resultImpl = (ResultImpl) activity.results().getFirst();
        final String fingerprint = resultImpl.values()
                .get("adunitcodevalue")
                .get("greenbids")
                .get("fingerprint").asText();

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
                                + ".greenbids._children.fingerprint")
                .isEqualTo(toAnalyticsTags(List.of(expectedAnalyticsResult)));
        assertThat(fingerprint).isNotNull();
    }

    @Disabled("Broken until dbReader is mocked")
    @Test
    public void callShouldFilterBiddersBasedOnModelWhenAnyFeatureNotAvailable() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 0.0001;
        final Device device = givenDeviceWithoutUserAgent(identity());
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);
        when(modelCacheWithExpiration.getIfPresent("onnxModelRunner_test-pbuid"))
                .thenReturn(givenOnnxModelRunner());
        when(thresholdsCacheWithExpiration.getIfPresent("throttlingThresholds_test-pbuid"))
                .thenReturn(givenThrottlingThresholds());

        final BidRequest expectedBidRequest = expectedUpdatedBidRequest(request -> request, explorationRate, device);
        final AnalyticsResult expectedAnalyticsResult = expectedAnalyticsResult(false, false);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        final ActivityImpl activity = (ActivityImpl) result.analyticsTags().activities().getFirst();
        final ResultImpl resultImpl = (ResultImpl) activity.results().getFirst();
        final String fingerprint = resultImpl.values()
                .get("adunitcodevalue")
                .get("greenbids")
                .get("fingerprint").asText();

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
                                + ".greenbids._children.fingerprint")
                .isEqualTo(toAnalyticsTags(List.of(expectedAnalyticsResult)));
        assertThat(fingerprint).isNotNull();
        assertThat(resultBidRequest).usingRecursiveComparison().isEqualTo(expectedBidRequest);
    }

    @Disabled("Broken until dbReader is mocked")
    @Test
    public void callShouldFilterBiddersBasedOnModelResults() throws OrtException, IOException {
        // given
        final Banner banner = givenBanner();

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();

        final Double explorationRate = 0.0001;
        final Device device = givenDevice(identity());
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, extRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, context -> context);
        final AuctionInvocationContext invocationContext = givenAuctionInvocationContext(auctionContext);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);
        when(modelCacheWithExpiration.getIfPresent("onnxModelRunner_test-pbuid"))
                .thenReturn(givenOnnxModelRunner());
        when(thresholdsCacheWithExpiration.getIfPresent("throttlingThresholds_test-pbuid"))
                .thenReturn(givenThrottlingThresholds());

        final BidRequest expectedBidRequest = expectedUpdatedBidRequest(
                request -> request, explorationRate, device);
        final AnalyticsResult expectedAnalyticsResult = expectedAnalyticsResult(false, false);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> future = target
                .call(null, invocationContext);
        final InvocationResult<AuctionRequestPayload> result = future.result();
        final BidRequest resultBidRequest = result
                .payloadUpdate()
                .apply(AuctionRequestPayloadImpl.of(bidRequest))
                .bidRequest();

        // then
        final ActivityImpl activityImpl = (ActivityImpl) result.analyticsTags().activities().getFirst();
        final ResultImpl resultImpl = (ResultImpl) activityImpl.results().getFirst();
        final String fingerprint = resultImpl.values()
                .get("adunitcodevalue")
                .get("greenbids")
                .get("fingerprint").asText();

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
                                + ".greenbids._children.fingerprint")
                .isEqualTo(toAnalyticsTags(List.of(expectedAnalyticsResult)));
        assertThat(fingerprint).isNotNull();
        assertThat(resultBidRequest).usingRecursiveComparison()
                .isEqualTo(expectedBidRequest);
    }

    static ExtRequest givenExtRequest(Double explorationRate) {
        final ObjectNode greenbidsNode = TestBidRequestProvider.MAPPER.createObjectNode();
        greenbidsNode.put("pbuid", "test-pbuid");
        greenbidsNode.put("targetTpr", 0.60);
        greenbidsNode.put("explorationRate", explorationRate);

        final ObjectNode analyticsNode = TestBidRequestProvider.MAPPER.createObjectNode();
        analyticsNode.set("greenbids-rtd", greenbidsNode);

        return ExtRequest.of(ExtRequestPrebid
                .builder()
                .analytics(analyticsNode)
                .build());
    }

    private AuctionContext givenAuctionContext(
            BidRequest bidRequest,
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {

        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest);

        return auctionContextCustomizer.apply(auctionContextBuilder).build();
    }

    private AuctionInvocationContext givenAuctionInvocationContext(AuctionContext auctionContext) {
        final AuctionInvocationContext invocationContext = mock(AuctionInvocationContext.class);
        when(invocationContext.auctionContext()).thenReturn(auctionContext);
        return invocationContext;
    }

    private OnnxModelRunner givenOnnxModelRunner() throws OrtException, IOException {
        final byte[] onnxModelBytes = Files.readAllBytes(Paths.get(
                "src/test/resources/models_pbuid=test-pbuid.onnx"));
        return new OnnxModelRunner(onnxModelBytes);
    }

    private ThrottlingThresholds givenThrottlingThresholds() throws IOException {
        final JsonNode thresholdsJsonNode = TestBidRequestProvider.MAPPER.readTree(
                Files.newInputStream(Paths.get(
                        "src/test/resources/thresholds_pbuid=test-pbuid.json")));
        return TestBidRequestProvider.MAPPER
                .treeToValue(thresholdsJsonNode, ThrottlingThresholds.class);
    }

    private BidRequest expectedUpdatedBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Double explorationRate,
            Device device) {

        final Banner banner = givenBanner();

        final ObjectNode bidderNode = TestBidRequestProvider.MAPPER.createObjectNode();
        final ObjectNode prebidNode = TestBidRequestProvider.MAPPER.createObjectNode();
        prebidNode.set("bidder", bidderNode);

        final ObjectNode extNode = TestBidRequestProvider.MAPPER.createObjectNode();
        extNode.set("prebid", prebidNode);
        extNode.set("tid", TextNode.valueOf("67eaab5f-27a6-4689-93f7-bd8f024576e3"));

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(extNode)
                .banner(banner)
                .build();

        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request")
                .imp(List.of(imp))
                .site(givenSite(site -> site))
                .device(device)
                .ext(givenExtRequest(explorationRate))).build();
    }

    private AnalyticsResult expectedAnalyticsResult(Boolean isExploration, Boolean isKeptInAuction) {
        return AnalyticsResult.of(
                "success",
                Map.of("adunitcodevalue", expectedOrtb2ImpExtResult(isExploration, isKeptInAuction)),
                null,
                null);
    }

    private Ortb2ImpExtResult expectedOrtb2ImpExtResult(Boolean isExploration, Boolean isKeptInAuction) {
        return Ortb2ImpExtResult.of(
                expectedExplorationResult(isExploration, isKeptInAuction), "67eaab5f-27a6-4689-93f7-bd8f024576e3");
    }

    private ExplorationResult expectedExplorationResult(Boolean isExploration, Boolean isKeptInAuction) {
        final Map<String, Boolean> keptInAuction = Map.of(
                "appnexus", isKeptInAuction,
                "pubmatic", isKeptInAuction,
                "rubicon", isKeptInAuction);
        return ExplorationResult.of("60a7c66c-c542-48c6-a319-ea7b9f97947f", keptInAuction, isExploration);
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
        return values != null ? TestBidRequestProvider.MAPPER.valueToTree(values) : null;
    }
}
