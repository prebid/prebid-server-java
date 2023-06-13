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
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.function.UnaryOperator.identity;
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
        final BidRequest bidRequest = createBidRequest(createBannerImp("192848"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod)
                .containsExactly(HttpMethod.POST);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("981287"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedURL() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("726292"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnImpsWithExpectedTagIds() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("827194"), createBannerImp("abc"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("827194", "abc");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest invalidBidRequest = createBidRequest(createImpWithNonParsableImpExt("3"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage())
                    .startsWith("Cannot deserialize")
                    .endsWith(". Ignore imp id = 3.");
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoBanner() {
        // given
        final BidRequest invalidBidRequest = createBidRequest(createVideoImp("123", imp -> imp.id("2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Expected banner impression. Ignore imp id = 2."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSlotIdIsEmpty() {
        // given
        final BidRequest invalidBidRequest = createBidRequest(createBannerImp(" ", imp -> imp.id("1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Custom param slot id (sid) is empty. Ignore imp id = 1."));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreInvalidImpressions() {
        // given
        final List<Imp> imps = List.of(
                createImpWithNonParsableImpExt("2"),
                createBannerImp("   "),
                createBannerImp("a"),
                createBannerImp("b", imp -> imp.banner(null)),
                createVideoImp("c", identity()),
                createBannerImp("d"),
                createBannerImp("e", imp -> imp.bidfloor(BigDecimal.ONE).bidfloorcur("GPB")));

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("no"));

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(5);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("a", "d");
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyToEuro() {
        // given
        final BigDecimal usdBidFloor = BigDecimal.valueOf(0.5);
        final Imp usdImp = createBannerImp("200", imp -> imp.bidfloorcur("USD").bidfloor(usdBidFloor));
        final BidRequest bidRequest = createBidRequest(usdImp);

        final BigDecimal eurBidFloor = BigDecimal.valueOf(1.82);
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenReturn(eurBidFloor);

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
                .containsOnly(tuple(eurBidFloor, "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreBidIfCurrencyIfCurrencyServiceThrowsException() {
        // given
        final BigDecimal usdBidFloor = BigDecimal.valueOf(0.5);
        final Imp usdImp = createBannerImp("10", imp -> imp.id("1282").bidfloorcur("USD").bidfloor(usdBidFloor));
        final BidRequest bidRequest = createBidRequest(usdImp);

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("no"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        verify(currencyConversionService).convertCurrency(usdBidFloor, bidRequest, "USD", "EUR");
        verifyNoMoreInteractions(currencyConversionService);

        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("no. Ignore imp id = 1282.");
        });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedBidderBids() throws JsonProcessingException {
        // given
        final StroeerCoreBid bid1 = StroeerCoreBid.builder()
                .id("1")
                .bidId("1929")
                .adMarkup("<div></div>")
                .cpm(BigDecimal.valueOf(0.3))
                .creativeId("foo")
                .width(300)
                .height(600)
                .build();

        final StroeerCoreBid bid2 = StroeerCoreBid.builder()
                .id("27")
                .bidId("2010")
                .adMarkup("<span></span>")
                .cpm(BigDecimal.valueOf(1.58))
                .creativeId("bar")
                .width(800)
                .height(250)
                .build();

        final StroeerCoreBidResponse response = StroeerCoreBidResponse.of(List.of(bid1, bid2));
        final BidderCall<BidRequest> httpCall = createHttpCall(response);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        final Bid expectedBid1 = Bid.builder()
                .id("1")
                .impid("1929")
                .adm("<div></div>")
                .price(BigDecimal.valueOf(0.3))
                .crid("foo")
                .w(300)
                .h(600)
                .build();

        final Bid expectedBid2 = Bid.builder()
                .id("27")
                .impid("2010")
                .adm("<span></span>")
                .price(BigDecimal.valueOf(1.58))
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
        final BidderCall<BidRequest> httpCall = createHttpCall("[]");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfZeroBids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = createHttpCall(StroeerCoreBidResponse.of(emptyList()));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private BidRequest createBidRequest(Imp... imps) {
        return BidRequest.builder()
                .imp(List.of(imps))
                .build();
    }

    private Imp createImpWithNonParsableImpExt(String impId) {
        final ObjectNode impExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        return createBannerImp("1", imp -> imp.id(impId).ext(impExt));
    }

    private Imp createBannerImp(String slotId, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final UnaryOperator<ImpBuilder> addBanner = imp -> imp.banner(Banner.builder().build());
        return createImp(slotId, addBanner.andThen(impCustomizer));
    }

    private Imp createBannerImp(String slotId) {
        return createBannerImp(slotId, identity());
    }

    private Imp createVideoImp(String slotId, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final UnaryOperator<ImpBuilder> addVideo = imp -> imp.video(Video.builder().build());
        return createImp(slotId, addVideo.andThen(impCustomizer));
    }

    private Imp createImp(String slotId, Function<ImpBuilder, ImpBuilder> impCustomizer) {
        final ObjectNode impExtNode = mapper.valueToTree(ExtPrebid.of(null, ExtImpStroeerCore.of(slotId)));

        final UnaryOperator<Imp.ImpBuilder> addImpExt = imp -> imp.ext(impExtNode);
        final ImpBuilder impBuilder = Imp.builder();

        return addImpExt.andThen(impCustomizer).apply(impBuilder).build();
    }

    private BidderCall<BidRequest> createHttpCall(StroeerCoreBidResponse response) throws JsonProcessingException {
        return createHttpCall(mapper.writeValueAsString(response));
    }

    private BidderCall<BidRequest> createHttpCall(String body) {
        return BidderCall.succeededHttp(HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body), null);
    }
}
