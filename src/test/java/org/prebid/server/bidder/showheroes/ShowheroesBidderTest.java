package org.prebid.server.bidder.showheroes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.showheroes.ExtImpShowheroes;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

@ExtendWith(MockitoExtension.class)
public class ShowheroesBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://ads.showheroes.com/";

    private ShowheroesBidder target;

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;

    @Mock(strictness = LENIENT)
    private PrebidVersionProvider prebidVersionProvider;

    @BeforeEach
    public void setUp() {
        // set always 'test_version' as Prebid version for testing
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("test_version");
        target = new ShowheroesBidder(ENDPOINT_URL, currencyConversionService, prebidVersionProvider, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ShowheroesBidder("invalid_url",
                currencyConversionService, prebidVersionProvider, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenUnitIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpShowheroes.of("")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("unitId is required");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSitePageIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .site(Site.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenAppBundleIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .app(App.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnPbsVersion() {
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSource)
                .extracting(Source::getExt)
                .extracting(ext -> ext.getProperty("pbs"))
                .containsExactly(mapper.createObjectNode()
                        .put("pbsv", "test_version")
                        .put("pbsp", "java"));
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyFromUsdToEur() {
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImp(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).bidfloorcur("USD"))))
                .app(App.builder().bundle("test_bundle").build())
                .build();

        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        verify(currencyConversionService).convertCurrency(eq(BigDecimal.ONE), any(), eq("USD"), eq("EUR"));
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldNotConvertCurrencyEur() {
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImp(impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).bidfloorcur("EUR"))))
                .app(App.builder().bundle("test_bundle").build())
                .build();

        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        verify(currencyConversionService, never()).convertCurrency(any(), any(), anyString(), anyString());
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.ONE, "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequestForAllImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().page("https://example.com").build())
                .imp(List.of(
                        givenImp(impBuilder -> impBuilder.id("imp1")),
                        givenImp(impBuilder -> impBuilder.id("imp2"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final BidRequest outgoingRequest = result.getValue().get(0).getPayload();
        assertThat(outgoingRequest.getImp()).hasSize(2)
                .extracting(Imp::getId)
                .containsExactly("imp1", "imp2");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final Map<String, String> headers = result.getValue().get(0).getHeaders().entries().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(headers).containsEntry("Content-Type", "application/json;charset=utf-8")
                .containsEntry("Accept", "application/json");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(List.of()).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(Banner.builder().build())
                                .build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .video(Video.builder().build())
                                .build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(video);
    }

    @Test
    public void makeBidsShouldReturnVideoBidByDefaultWhenMtypeIsUnknown() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .video(Video.builder().build())
                                .build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(99))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(video);
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(Banner.builder().build())
                                .build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(BigDecimal.ONE))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeBidsShouldReturnMultipleBids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(List.of(
                                Imp.builder().id("123").banner(Banner.builder().build()).build(),
                                Imp.builder().id("456").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(List.of(
                                        Bid.builder().impid("123").mtype(1).price(BigDecimal.ONE).build(),
                                        Bid.builder().impid("456").mtype(2).price(BigDecimal.TEN).build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid, BidderBid::getType)
                .containsExactlyInAnyOrder(
                        tuple(Bid.builder().impid("123").mtype(1).price(BigDecimal.ONE).build(), banner),
                        tuple(Bid.builder().impid("456").mtype(2).price(BigDecimal.TEN).build(), video));
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .site(Site.builder().page("https://example.com").build())
                .imp(singletonList(givenImp(impCustomizer)))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpShowheroes.of("unitId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}

