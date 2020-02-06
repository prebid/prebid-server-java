package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.VideoStoredDataResult;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class StoredRequestProcessorTest extends VertxTest {

    private static final int DEFAULT_TIMEOUT = 500;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private Metrics metrics;

    private StoredRequestProcessor storedRequestProcessor;

    @Before
    public void setUp() {
        final TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        storedRequestProcessor = new StoredRequestProcessor(
                DEFAULT_TIMEOUT,
                applicationSettings,
                metrics,
                timeoutFactory,
                jacksonMapper);
    }

    @Test
    public void shouldReturnMergedBidRequestAndImps() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("bidRequest")).build())))
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("imp")).build(),
                                        null)))))));

        final String storedRequestImpJson = mapper.writeValueAsString(Imp.builder().banner(Banner.builder()
                .format(singletonList(Format.builder().w(300).h(250).build())).build()).build());

        final String storedRequestBidRequestJson = mapper.writeValueAsString(BidRequest.builder().id("test-request-id")
                .tmax(1000L).imp(singletonList(Imp.builder().build())).build());

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("bidRequest", storedRequestBidRequestJson),
                                singletonMap("imp", storedRequestImpJson), emptyList())));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result()).isEqualTo(
                BidRequest.builder()
                        .id("test-request-id")
                        .tmax(1000L)
                        .ext(mapper.valueToTree(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("bidRequest")).build()))))
                        .imp(singletonList(Imp.builder()
                                .ext(mapper.valueToTree(
                                        ExtImp.of(ExtImpPrebid.builder().storedrequest(
                                                ExtStoredRequest.of("imp")).build(), null)))
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(300).h(250).build()))
                                        .build())
                                .build()))
                        .build());
    }

    @Test
    public void shouldReturnMergedBidRequest() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("123")).build()))));

        final String storedRequestBidRequestJson = mapper.writeValueAsString(BidRequest.builder()
                .id("test-request-id")
                .tmax(1000L)
                .imp(singletonList(Imp.builder().build()))
                .build());

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("123", storedRequestBidRequestJson), emptyMap(),
                                emptyList())));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result()).isEqualTo(BidRequest.builder()
                .id("test-request-id")
                .tmax(1000L)
                .imp(singletonList(Imp.builder().build()))
                .ext(mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build())))
                .build());
    }

    @Test
    public void shouldReturnAmpRequest() throws IOException {
        // given
        given(applicationSettings.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn((Future.succeededFuture(StoredDataResult.of(
                        singletonMap("123", mapper.writeValueAsString(
                                BidRequest.builder().id("test-request-id").build())), emptyMap(), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processAmpRequest("123");

        // then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result()).isEqualTo(BidRequest.builder()
                .id("test-request-id")
                .build());
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredBidRequestJsonIsNotValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("123")).build()))));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", "{{}");
        given(applicationSettings.getStoredData(anySet(), anySet(), any())).willReturn((Future
                .succeededFuture(StoredDataResult.of(storedRequestFetchResult, emptyMap(), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't parse Json for stored request with id 123");
    }

    @Test
    public void shouldReturnFailedFutureWhenMergedResultCouldNotBeConvertedToBidRequest() throws IOException {
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("123")).build()))));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", mapper.writeValueAsString(
                mapper.createObjectNode().put("tmax", "stringValue")));
        given(applicationSettings.getStoredData(anySet(), anySet(), any())).willReturn((Future
                .succeededFuture(StoredDataResult.of(storedRequestFetchResult, emptyMap(), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Can't convert merging result for id 123: Cannot deserialize");
    }

    @Test
    public void shouldReturnFailedFutureIfIdWasNotPresentInStoredRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.builder()
                                .storedrequest(ExtStoredRequest.of(null)).build()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("Id is not found in storedRequest");
    }

    @Test
    public void shouldReturnFailedFutureIfBidRequestStoredRequestIdHasIncorrectType() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("storedrequest", mapper.createObjectNode()
                                        .set("id", mapper.createObjectNode().putArray("id").add("id")))))
                .id("test-id"));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incorrect bid request extension format for bidRequest with id test-id");
    }

    @Test
    public void shouldReturnBidRequestWithMergedImp() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        final String storedRequestImpJson = mapper.writeValueAsString(
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .build())
                        .build());

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn((Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), singletonMap("123", storedRequestImpJson), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result().getImp().get(0)).isEqualTo(Imp.builder()
                .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                .ext(mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(), null)))
                .build());
    }

    @Test
    public void shouldReturnFailedFutureWhenIdIsMissedInPrebidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of(null)).build(),
                                        null)))))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("Id is not found in storedRequest");
    }

    @Test
    public void shouldReturnFailedFutureWhenJsonBodyWasNotFoundByFetcher() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn((Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("No config found for id: 123")))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("No config found for id: 123");
    }

    @Test
    public void shouldReturnImpAndBidRequestWithoutChangesIfStoredRequestIsAbsentInPrebid() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtImp.of(ExtImpPrebid.builder().storedrequest(null).build(), null))));
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(imp)));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        verifyZeroInteractions(applicationSettings);
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result().getImp().get(0)).isSameAs(imp);
        assertThat(bidRequestFuture.result()).isSameAs(bidRequest);
    }

    @Test
    public void shouldReturnChangedImpWithStoredRequestAndNotModifiedImpWithoutStoreRequest() throws IOException {
        // given
        final Imp impWithoutStoredRequest = givenImp(impBuilder -> impBuilder.ext(
                mapper.valueToTree(ExtImp.of(ExtImpPrebid.builder().storedrequest(null).build(), null))));
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(asList(impWithoutStoredRequest, givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        final String storedRequestImpJson = mapper.writeValueAsString(Imp.builder().banner(Banner.builder()
                .format(singletonList(Format.builder().w(300).h(250).build())).build()).build());

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn((Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), singletonMap("123", storedRequestImpJson), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result().getImp().get(0)).isSameAs(impWithoutStoredRequest);
        assertThat(bidRequestFuture.result().getImp().get(1)).isEqualTo(Imp.builder()
                .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                .ext(mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(), null)))
                .build());
    }

    @Test
    public void shouldReturnFailedFutureIfOneImpWithValidStoredRequestAndAnotherWithMissedId() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(asList(
                givenImp(impBuilder -> impBuilder.ext(
                        mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of(null)).build(),
                                        null)))),
                givenImp(impBuilder -> impBuilder.ext(
                        mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("Id is not found in storedRequest");
    }

    @Test
    public void shouldReturnFailedFutureIfImpsStoredRequestIdHasIncorrectType() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(givenImp(
                impBuilder -> impBuilder.ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("storedrequest", mapper.createObjectNode()
                                        .set("id", mapper.createObjectNode().putArray("id")
                                                .add("id"))))).id("imp-test")))));
        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // when
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Incorrect Imp extension format for Imp with id imp-test: Cannot deserialize");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredRequestFetcherReturnsFailedFuture() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(givenImp(
                impBuilder -> impBuilder.ext(
                        mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn((Future.failedFuture(new Exception("Error during file fetching"))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stored request fetching failed: Error during file fetching");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredImpJsonIsNotValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(givenImp(
                impBuilder -> impBuilder.ext(
                        mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn((Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), singletonMap("123", "{{}"), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't parse Json for stored request with id 123");
    }

    @Test
    public void shouldReturnFailedFutureWhenMergedResultCantBeConvertedToImp() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(givenImp(
                impBuilder -> impBuilder.ext(
                        mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("123")).build(),
                                        null)))))));

        final Map<String, String> storedImpFetchResult = singletonMap("123", mapper.writeValueAsString(
                mapper.createObjectNode().put("secure", "stringValue")));
        given(applicationSettings.getStoredData(anySet(), anySet(), any())).willReturn((Future
                .succeededFuture(StoredDataResult.of(emptyMap(), storedImpFetchResult, emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Can't convert merging result for id 123: Cannot deserialize");
    }

    @Test
    public void shouldUseTimeoutFromRequest() {
        // given
        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.failedFuture((String) null));

        // when
        storedRequestProcessor.processStoredRequests(givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder().storedrequest(ExtStoredRequest.of("bidRequest")).build())))
                .tmax(1000L)));

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(applicationSettings).getStoredData(anySet(), anySet(), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(1000L);
    }

    @Test
    public void shouldUseDefaultTimeoutIfMissingInRequest() {
        // given
        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.failedFuture((String) null));

        // when
        storedRequestProcessor.processStoredRequests(givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder().storedrequest(ExtStoredRequest.of("bidRequest")).build())))));

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(applicationSettings).getStoredData(anySet(), anySet(), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(DEFAULT_TIMEOUT);
    }

    @Test
    public void processStoredRequestsShouldNotUpdateMetrics() {
        // given
        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.failedFuture("failed"));

        // when
        storedRequestProcessor.processStoredRequests(givenBidRequest(Function.identity()));

        // then
        verifyZeroInteractions(metrics);
    }

    @Test
    public void processAmpRequestShouldNotUpdateMetrics() {
        // given
        given(applicationSettings.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.failedFuture("failed"));

        // when
        storedRequestProcessor.processAmpRequest("123");

        // then
        verifyZeroInteractions(metrics);
    }

    @Test
    public void processStoredRequestsShouldUpdateRequestAndImpMetricsAsExpected() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("123")).build())))
                .imp(asList(givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("321")).build(), null)))),
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(
                                ExtImp.of(
                                        ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("not_found")).build(),
                                        null)))))));

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        singletonMap("123", "stored_request"), singletonMap("321", "stored_imp"), emptyList())));

        // when
        storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        verify(metrics).updateStoredRequestMetric(eq(true));
        verify(metrics).updateStoredImpsMetric(eq(true));
        verify(metrics).updateStoredImpsMetric(eq(false));
    }

    @Test
    public void processStoredRequestsShouldUpdateRequestMissingMetrics() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("123")).build()))));

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(emptyMap(), emptyMap(), emptyList())));

        // when
        storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        verify(metrics).updateStoredRequestMetric(eq(false));
    }

    @Test
    public void processAmpRequestShouldUpdateRequestFoundMetric() {
        // given
        given(applicationSettings.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("123", "amp"), emptyMap(), emptyList())));

        // when
        storedRequestProcessor.processAmpRequest("123");

        // then
        verify(metrics).updateStoredRequestMetric(true);
    }

    @Test
    public void processAmpRequestShouldUpdateRequestMissingMetrics() {
        // given
        given(applicationSettings.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), emptyList())));

        // when
        storedRequestProcessor.processAmpRequest("123");

        // then
        verify(metrics).updateStoredRequestMetric(false);
    }

    @Test
    public void impToStoredVideoJsonShouldReturnExpectedVideoStoredDataResult() throws JsonProcessingException {
        // given
        final Imp imp1 = givenImp(impBuilder -> impBuilder.id("id1").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("st1")).build(), null))));
        final Imp imp2 = givenImp(impBuilder -> impBuilder.id("id2").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("st2")).build(), null))));

        final Video storedVideo = Video.builder().maxduration(100).h(2).w(2).build();
        final Imp storedImp1 = Imp.builder().video(storedVideo).build();
        final Imp storedImp2 = Imp.builder().video(storedVideo).build();

        final Map<String, String> storedIdToJson = new HashMap<>();
        storedIdToJson.put("st1", mapper.writeValueAsString(storedImp1));
        storedIdToJson.put("st2", mapper.writeValueAsString(storedImp2));

        given(applicationSettings.getStoredData(any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(emptyMap(), storedIdToJson, emptyList())));

        // when
        final Future<VideoStoredDataResult> result = storedRequestProcessor.videoStoredDataResult(
                Arrays.asList(imp1, imp2), emptyList(), null);

        // then
        verify(applicationSettings).getStoredData(any(), eq(new HashSet<>(Arrays.asList("st1", "st2"))), any());

        assertThat(result.result().getImpIdToStoredVideo()).containsOnly(entry("id2", storedVideo),
                entry("id1", storedVideo));
        assertThat(result.result().getErrors()).isEmpty();
    }

    @Test
    public void impToStoredVideoJsonShouldReturnExpectedVideoStoredDataResultErrors() throws JsonProcessingException {
        // given
        final Imp imp1 = givenImp(impBuilder -> impBuilder.id("id1").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("st1")).build(), null))));
        final Imp imp2 = givenImp(impBuilder -> impBuilder.id("id2").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder().storedrequest(ExtStoredRequest.of("st2")).build(), null))));

        final Imp storedImp1 = Imp.builder().build();

        final Map<String, String> storedIdToJson = new HashMap<>();
        storedIdToJson.put("st1", mapper.writeValueAsString(storedImp1));

        given(applicationSettings.getStoredData(any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(emptyMap(), storedIdToJson, emptyList())));

        // when
        final Future<VideoStoredDataResult> result = storedRequestProcessor.videoStoredDataResult(
                Arrays.asList(imp1, imp2), new ArrayList<>(), null);

        // then
        verify(applicationSettings).getStoredData(any(), eq(new HashSet<>(Arrays.asList("st1", "st2"))), any());

        assertThat(result.result().getErrors()).containsOnly(
                "No stored Imp for stored id st2",
                "No stored video found for Imp with id id1");
    }

    private static BidRequest givenBidRequest(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder()).build();
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()).build();
    }
}
