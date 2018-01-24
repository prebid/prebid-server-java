package org.rtb.vexing.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.exception.InvalidRequestException;
import org.rtb.vexing.model.openrtb.ext.request.ExtBidRequest;
import org.rtb.vexing.model.openrtb.ext.request.ExtImp;
import org.rtb.vexing.model.openrtb.ext.request.ExtImpPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtStoredRequest;
import org.rtb.vexing.settings.StoredRequestFetcher;
import org.rtb.vexing.settings.model.StoredRequestResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;

public class StoredRequestProcessorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestFetcher storedRequestFetcher;

    private StoredRequestProcessor storedRequestProcessor;

    @Before
    public void setUp() {
        storedRequestProcessor = new StoredRequestProcessor(storedRequestFetcher);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new StoredRequestProcessor(null));
    }

    @Test
    public void shouldReturnMergedBidRequestAndImps() throws IOException {

        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        null, ExtStoredRequest.of("bidRequest")))))
                .imp(singletonList(givenImpCustomizable(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("imp")))))))));

        String storedRequestImpJson = mapper.writeValueAsString(Imp.builder().banner(Banner.builder()
                .format(singletonList(Format.builder().w(300).h(250).build())).build()).build());

        String storedRequestBidRequestJson = mapper.writeValueAsString(BidRequest.builder().id("test-request-id")
                .tmax(1000L).imp(singletonList(Imp.builder().build())).build());

        final Map<String, String> storedRequestFetchResult = new HashMap<>();
        storedRequestFetchResult.put("bidRequest", storedRequestBidRequestJson);
        storedRequestFetchResult.put("imp", storedRequestImpJson);
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn(
                Future.succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList())));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result()).isEqualTo(
                BidRequest.builder()
                        .id("test-request-id")
                        .tmax(1000L)
                        .ext(Json.mapper.valueToTree(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null,
                                ExtStoredRequest.of("bidRequest"))))))
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
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, ExtStoredRequest.of("123")))))
                .imp(emptyList()));

        String storedRequestBidRequestJson = mapper.writeValueAsString(BidRequest.builder().id("test-request-id")
                .tmax(1000L).build());
        final Map<String, String> storedRequestFetchResult = singletonMap("123", storedRequestBidRequestJson);
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        //then
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result()).isEqualTo(BidRequest.builder()
                .id("test-request-id")
                .tmax(1000L)
                .imp(emptyList())
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, ExtStoredRequest.of("123")))))
                .build());
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredBidRequestJsonIsNotValid() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, ExtStoredRequest.of("123")))))
                .imp(emptyList()));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", "{{}");
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't parse Json for stored request with id 123");
    }

    @Test
    public void shouldReturnFailedFutureWhenMergedResultCantBeConvertedToBidRequest() throws IOException {
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, ExtStoredRequest.of("123")))))
                .imp(emptyList()));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", mapper.writeValueAsString(
                Json.mapper.createObjectNode().put("tmax", "stringValue")));
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't convert merging result for storedRequestId 123");
    }

    @Test
    public void shouldReturnFailedFutureIfIdWasNotPresentInStoredRequest() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, ExtStoredRequest.of(null))))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("Id is not found in storedRequest");
    }

    @Test
    public void shouldReturnFailedFutureIfBidRequestStoredRequestIdHasIncorrectType() throws IOException {
        //given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
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
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

        String storedRequestImpJson = mapper.writeValueAsString(Imp.builder().banner(Banner.builder()
                .format(singletonList(Format.builder().w(300).h(250).build())).build()).build());

        final Map<String, String> storedRequestFetchResult = singletonMap("123", storedRequestImpJson);
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

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
    public void shouldReturnFailedFutureWhenIdIsMissedInPrebidRequest() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of(null)))))))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("Id is not found in storedRequest");
    }

    @Test
    public void shouldReturnFailedFutureWhenJsonBodyWasNotFoundByFetcher() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder ->
                        impBuilder.ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.
                                of(ExtStoredRequest.of("123")))))))));

        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("No config found for id: 123")))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("No config found for id: 123");
    }

    @Test
    public void shouldReturnImpAndBidRequestWithoutChangesIfStoredRequestIsAbsentInPrebid() throws IOException {
        // given
        final Imp imp = givenImpCustomizable(impBuilder -> impBuilder.ext(Json.mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.of(null)))));
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(imp)));

        //when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        //then
        verifyZeroInteractions(storedRequestFetcher);
        assertThat(bidRequestFuture.succeeded()).isTrue();
        assertThat(bidRequestFuture.result().getImp().get(0)).isSameAs(imp);
        assertThat(bidRequestFuture.result()).isSameAs(bidRequest);
    }

    @Test
    public void shouldReturnChangedImpWithStoredRequestAndNotModifiedImpWithoutStoreRequest() throws IOException {
        // given
        final Imp impWithoutStoredRequest = givenImpCustomizable(impBuilder -> impBuilder
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null)))));
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(asList(impWithoutStoredRequest,
                        givenImpCustomizable(impBuilder -> impBuilder
                                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of(
                                        "123")))))))));

        String storedRequestImpJson = mapper.writeValueAsString(Imp.builder().banner(Banner.builder()
                .format(singletonList(Format.builder().w(300).h(250).build())).build()).build());

        final Map<String, String> storedRequestFetchResult = singletonMap("123", storedRequestImpJson);
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

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
    public void shouldReturnFailedFutureIfOneImpWithValidStoredRequestAndAnotherWithMissedId() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(asList(givenImpCustomizable(impBuilder -> impBuilder
                                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of(null)))))),
                        givenImpCustomizable(impBuilder -> impBuilder
                                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of(
                                        "123")))))))));
        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class).hasMessage("Id is not found in storedRequest");
    }

    @Test
    public void shouldReturnFailedFutureIfImpsStoredRequestIdHasIncorrectType() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder ->
                        impBuilder
                                .ext((ObjectNode) Json.mapper.createObjectNode()
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
    public void shouldReturnFailedFutureIfStoredRequestFetcherReturnsFailedFuture() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .failedFuture(new Exception("Error during file fetching"))));

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
    public void shouldReturnFailedFutureWhenStoredImpJsonIsNotValid() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", "{{}");
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

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
        final BidRequest bidRequest = givenBidRequestCustomizable(builder -> builder
                .imp(singletonList(givenImpCustomizable(impBuilder -> impBuilder
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(ExtStoredRequest.of("123")))))))));

        final Map<String, String> storedRequestFetchResult = singletonMap("123", mapper.writeValueAsString(
                Json.mapper.createObjectNode().put("secure", "stringValue")));
        given(storedRequestFetcher.getStoredRequestsById(any())).willReturn((Future
                .succeededFuture(StoredRequestResult.of(storedRequestFetchResult, emptyList()))));

        // when
        final Future<BidRequest> bidRequestFuture = storedRequestProcessor.processStoredRequests(bidRequest);

        // then
        assertThat(bidRequestFuture.failed()).isTrue();
        assertThat(bidRequestFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Can't convert merging result for storedRequestId 123");
    }

    private static BidRequest givenBidRequestCustomizable(Function<BidRequest.BidRequestBuilder,
            BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        final BidRequest.BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder().imp(emptyList());
        final BidRequest.BidRequestBuilder bidRequestBuilderCustomized = bidRequestBuilderCustomizer
                .apply(bidRequestBuilderMinimal);
        return bidRequestBuilderCustomized.build();
    }

    private static Imp givenImpCustomizable(Function<Imp.ImpBuilder, Imp.ImpBuilder> impBuilderCustomizer) {
        final Imp.ImpBuilder impBuilderMinimal = Imp.builder();
        final Imp.ImpBuilder impBuilderCustomized = impBuilderCustomizer.apply(impBuilderMinimal);
        return impBuilderCustomized.build();
    }
}
