package org.prebid.server.bidder.stroeercore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBid;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBidResponse;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.stroeercore.ExtImpStroeerCore;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class StroeerCoreBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    private StroeerCoreBidder bidder;

    @Before
    public void setUp() {
        bidder = new StroeerCoreBidder(ENDPOINT_URL, jacksonMapper, currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedMethod() {
        // given
        final BidRequest bidRequest = createBidRequest("192848");

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = createBidRequest("192848");

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedURL() {
        // given
        final BidRequest bidRequest = createBidRequest("192848");

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns(ENDPOINT_URL, HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBody() {
        // given
        final BidRequest bidRequest = createBidRequest("192848", "abc");

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        final List<Imp> imps = bidRequest.getImp();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("192848", "abc");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest validBidRequest = createBidRequest("123");
        final Imp validImp = validBidRequest.getImp().stream().findFirst().orElseThrow();
        final Imp invalidImp = createNonParsableImpFrom(validImp);
        final BidRequest invalidBidRequest = validBidRequest.toBuilder().imp(singletonList(invalidImp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .first()
                .satisfies(bidderError -> assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input))
                .satisfies(bidderError -> assertThat(bidderError.getMessage())
                        .startsWith("Cannot deserialize").endsWith(". Ignore imp id = 1."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoBanner() {
        // given
        final BidRequest validBidRequest = createBidRequest("123");
        final Imp validImp = validBidRequest.getImp().stream().findFirst().orElseThrow();
        final Imp invalidImp = createNullBannerImpFrom(validImp);
        final BidRequest invalidBidRequest = validBidRequest.toBuilder().imp(singletonList(invalidImp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .first()
                .satisfies(bidderError -> assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input))
                .satisfies(bidderError -> assertThat(bidderError.getMessage())
                        .isEqualTo("Expected banner impression. Ignore imp id = 1."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSlotIdIsEmpty() {
        // given
        final BidRequest invalidBidRequest = createBidRequest(" ");

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .first()
                .satisfies(bidderError -> assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input))
                .satisfies(bidderError -> assertThat(bidderError.getMessage())
                        .isEqualTo("Custom param slot id (sid) is empty. Ignore imp id = 1."));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreInvalidImpressions() {
        // given
        final List<Imp> imps = List.of(
                createNonParsableImpFrom(createImp("10", "a")),
                createImp("11", "b"),
                createEmptySlotIdImpFrom(createImp("12", "c")),
                createNullBannerImpFrom(createImp("13", "d")),
                createImpWithFloorFrom(createImp("14", "e"), "GPB", BigDecimal.ONE),
                createImp("15", "f")
        );
        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("no"));

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(4);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("b", "f");
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyToEuro() {
        // given
        final BigDecimal usdBidFloor = new BigDecimal("0.5");
        final Imp imp = createImp("1", "200");
        final Imp usdImp = createImpWithFloorFrom(imp, "USD", new BigDecimal("0.5"));
        final BidRequest bidRequest = createBidRequest(usdImp);

        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenReturn(new BigDecimal("1.82"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        verify(currencyConversionService).convertCurrency(usdBidFloor, bidRequest, "USD", "EUR");
        verifyNoMoreInteractions(currencyConversionService);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(new BigDecimal("1.82"), "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreBidIfCurrencyIfCurrencyServiceThrowsException() {
        // given
        final BigDecimal usdBidFloor = new BigDecimal("0.5");
        final Imp imp = createImp("1282", "200");
        final Imp usdImp = createImpWithFloorFrom(imp, "USD", new BigDecimal("0.5"));
        final BidRequest bidRequest = createBidRequest(usdImp);

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("no"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        verify(currencyConversionService).convertCurrency(usdBidFloor, bidRequest, "USD", "EUR");
        verifyNoMoreInteractions(currencyConversionService);

        assertThat(result.getErrors())
                .hasSize(1)
                .first()
                .satisfies(bidderError -> assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input))
                .satisfies(bidderError -> assertThat(bidderError.getMessage()).isEqualTo("no. Ignore imp id = 1282."));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedBidderBids() throws JsonProcessingException {
        // given
        final StroeerCoreBid bid1 = StroeerCoreBid.builder()
                .id("1")
                .bidId("1929")
                .adMarkup("<div></div>")
                .cpm(new BigDecimal("0.30"))
                .creativeId("foo")
                .width(300)
                .height(600)
                .build();

        final StroeerCoreBid bid2 = StroeerCoreBid.builder()
                .id("27")
                .bidId("2010")
                .adMarkup("<span></span>")
                .cpm(new BigDecimal("1.58"))
                .creativeId("bar")
                .width(800)
                .height(250)
                .build();

        final StroeerCoreBidResponse response = StroeerCoreBidResponse.of(List.of(bid1, bid2));
        final HttpCall<BidRequest> httpCall = createHttpCall(response);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        final Bid expectedBid1 = Bid.builder()
                .id("1")
                .impid("1929")
                .adm("<div></div>")
                .price(new BigDecimal("0.30"))
                .crid("foo")
                .w(300)
                .h(600)
                .build();

        final Bid expectedBid2 = Bid.builder()
                .id("27")
                .impid("2010")
                .adm("<span></span>")
                .price(new BigDecimal("1.58"))
                .crid("bar")
                .w(800)
                .h(250)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedBid1, BidType.banner, "EUR"),
                BidderBid.of(expectedBid2, BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = createHttpCall("[]");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .first()
                .satisfies(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response))
                .satisfies(error -> assertThat(error.getMessage()).startsWith("Failed to decode"));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfZeroBids() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = createHttpCall(StroeerCoreBidResponse.of(emptyList()));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private BidRequest createBidRequest(final String... slotIds) {
        final ArrayList<Imp> imps = new ArrayList<>();

        long impId = 1;
        for (String slotId : slotIds) {
            imps.add(createImp(String.valueOf(impId++), slotId));
        }

        return createBidRequest(imps.toArray(new Imp[]{}));
    }

    private BidRequest createBidRequest(final Imp... imps) {
        return BidRequest.builder().imp(List.of(imps)).build();
    }

    private Imp createImp(final String id, final String slotId) {
        final ObjectNode impExtNode = mapper.valueToTree(ExtPrebid.of(null, ExtImpStroeerCore.of(slotId)));
        ImpBuilder impBuilder = Imp.builder();
        impBuilder.id(String.valueOf(id));
        impBuilder.banner(Banner.builder().build());
        impBuilder.ext(impExtNode);
        return impBuilder.build();
    }

    private Imp createNonParsableImpFrom(final Imp validImp) {
        return validImp.toBuilder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))).build();
    }

    private Imp createNullBannerImpFrom(final Imp validImp) {
        return validImp.toBuilder()
                .banner(null).video(Video.builder().build()).build();
    }

    private Imp createEmptySlotIdImpFrom(final Imp validImp) {
        return validImp.toBuilder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpStroeerCore.of(" ")))).build();
    }

    private Imp createImpWithFloorFrom(final Imp imp, final String bidFloorCurrency, final BigDecimal bidFloor) {
        return imp.toBuilder().bidfloorcur(bidFloorCurrency).bidfloor(bidFloor).build();
    }

    private HttpCall<BidRequest> createHttpCall(final StroeerCoreBidResponse response) throws JsonProcessingException {
        return createHttpCall(mapper.writeValueAsString(response));
    }

    private HttpCall<BidRequest> createHttpCall(final String body) {
        return HttpCall.success(HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body), null);
    }
}
