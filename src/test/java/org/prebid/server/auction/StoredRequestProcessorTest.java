package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
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
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredDataResult;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class StoredRequestProcessorTest extends VertxTest {

    private static final int DEFAULT_TIMEOUT = 500;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    private StoredRequestProcessor storedRequestProcessor;

    @Before
    public void setUp() {
        final TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        storedRequestProcessor = new StoredRequestProcessor(applicationSettings, timeoutFactory, DEFAULT_TIMEOUT);
    }

    @Test
    public void shouldReturnMergedBidRequestAndImps() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, null, null, ExtStoredRequest.of("bidRequest"), null))))
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("imp")))))))));

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
                        .ext(Json.mapper.valueToTree(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null,
                                null, null, ExtStoredRequest.of("bidRequest"), null)))))
                        .imp(singletonList(Imp.builder()
                                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("imp")))))
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
                .ext(Json.mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of("123"), null)))));

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
                .ext(Json.mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of("123"), null))))
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
                .ext(Json.mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of("123"), null)))));

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
                .ext(Json.mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of("123"), null)))));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", mapper.writeValueAsString(
                Json.mapper.createObjectNode().put("tmax", "stringValue")));
        given(applicationSettings.getStoredData(anySet(), anySet(), any())).willReturn((Future
                .succeededFuture(StoredDataResult.of(storedRequestFetchResult, emptyMap(), emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't convert merging result for id 123");
    }

    @Test
    public void shouldReturnFailedFutureIfIdWasNotPresentInStoredRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .ext(Json.mapper.valueToTree(
                        ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of(null), null)))));

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
                .ext((ObjectNode) Json.mapper.createObjectNode()
                        .set("prebid", Json.mapper.createObjectNode()
                                .set("storedrequest", Json.mapper.createObjectNode()
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
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

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
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))
                .build());
    }

    @Test
    public void shouldReturnFailedFutureWhenIdIsMissedInPrebidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of(null)))))))));

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
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

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
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null)))));
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
                Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null)))));
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(asList(impWithoutStoredRequest, givenImp(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

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
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))
                .build());
    }

    @Test
    public void shouldReturnFailedFutureIfOneImpWithValidStoredRequestAndAnotherWithMissedId() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(asList(
                givenImp(impBuilder -> impBuilder.ext(
                        Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of(null)))))),
                givenImp(impBuilder -> impBuilder.ext(
                        Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

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
                impBuilder -> impBuilder.ext((ObjectNode) Json.mapper.createObjectNode()
                        .set("prebid", Json.mapper.createObjectNode()
                                .set("storedrequest", Json.mapper.createObjectNode()
                                        .set("id", mapper.createObjectNode().putArray("id")
                                                .add("id"))))).id("imp-test")))));
        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // when
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incorrect Imp extension format for Imp with id imp-test");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredRequestFetcherReturnsFailedFuture() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(givenImp(
                impBuilder -> impBuilder.ext(
                        Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn((Future.failedFuture(new Exception("Error during file fetching"))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stored request fetching failed with exception: java.lang.Exception:"
                        + " Error during file fetching");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredImpJsonIsNotValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(singletonList(givenImp(
                impBuilder -> impBuilder.ext(
                        Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

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
                        Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

        final Map<String, String> storedImpFetchResult = singletonMap("123", mapper.writeValueAsString(
                Json.mapper.createObjectNode().put("secure", "stringValue")));
        given(applicationSettings.getStoredData(anySet(), anySet(), any())).willReturn((Future
                .succeededFuture(StoredDataResult.of(emptyMap(), storedImpFetchResult, emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't convert merging result for id 123");
    }

    @Test
    public void shouldUseTimeoutFromRequest() {
        // given
        given(applicationSettings.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.failedFuture((String) null));

        // when
        storedRequestProcessor.processStoredRequests(givenBidRequest(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of("bidRequest"), null))))
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
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.of(null, null, null, ExtStoredRequest.of("bidRequest"), null))))));

        // then
        final ArgumentCaptor<Timeout> timeoutCaptor = ArgumentCaptor.forClass(Timeout.class);
        verify(applicationSettings).getStoredData(anySet(), anySet(), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue().remaining()).isEqualTo(DEFAULT_TIMEOUT);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder()).build();
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()).build();
    }
}
