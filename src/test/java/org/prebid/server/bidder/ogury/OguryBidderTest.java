package org.prebid.server.bidder.ogury;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OguryBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;

    private OguryBidder target;

    @BeforeEach
    public void setUp() {
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenReturn(BigDecimal.ONE);
        target = new OguryBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void shouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OguryBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldPassUserAgentHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(HttpUtil.USER_AGENT_HEADER))
                        .isEqualTo("ua"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldPassAcceptLanguageHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(HttpUtil.ACCEPT_LANGUAGE_HEADER))
                        .isEqualTo("en-US"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldPassXForwardedForHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.getAll(HttpUtil.X_FORWARDED_FOR_HEADER))
                        .containsExactlyInAnyOrder("0.0.0.0", "ip6"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestDoesNotHaveOguryKeys() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.ext(givenEmptyImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);
        // then
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Invalid request. assetKey/adUnitId or request.site/app.publisher.id required"));
    }

    @Test
    public void makeHttpRequestsShouldSendOnlyImpsWithOguryParamsIfPresent() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("without_ogury_keys").ext(givenEmptyImpExt())),
                givenImp(imp -> imp.id("with_ogury_keys").ext(givenImpExtWithOguryKeys())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("with_ogury_keys");

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSendAllImpsWhenHasPublisherIdAndImpsWithOguryIsEmpty() {
        // given
        final ObjectNode emptyImpExt = givenEmptyImpExt();
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1").ext(emptyImpExt)),
                givenImp(imp -> imp.id("id2").ext(emptyImpExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotSendImpsWhenHasNotPublisherIdAndImpsWithOguryIsEmpty() {
        // given
        final ObjectNode emptyImpExt = givenEmptyImpExt();

        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(Site.builder().build()),
                givenImp(imp -> imp.id("id1").ext(emptyImpExt)),
                givenImp(imp -> imp.id("id2").ext(emptyImpExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).isEmpty();

        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Invalid request. assetKey/adUnitId or request.site/app.publisher.id required"));
    }

    @Test
    public void makeHttpRequestsAppShouldSendOnlyImpsWithOguryParamsIfPresent() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.app(givenApp()),
                givenImp(imp -> imp.id("without_ogury_keys").ext(givenEmptyImpExt())),
                givenImp(imp -> imp.id("with_ogury_keys").ext(givenImpExtWithOguryKeys())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("with_ogury_keys");

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsAppShouldSendAllImpsWhenHasPublisherIdAndImpsWithOguryIsEmpty() {
        // given
        final ObjectNode emptyImpExt = givenEmptyImpExt();

        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.app(givenApp()),
                givenImp(imp -> imp.id("id1").ext(emptyImpExt)),
                givenImp(imp -> imp.id("id2").ext(emptyImpExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsAppShouldNotSendImpsWhenHasNotPublisherIdAndImpsWithOguryIsEmpty() {
        // given
        final ObjectNode emptyImpExt = givenEmptyImpExt();

        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.app(App.builder().bundle("app_bundle").build()),
                givenImp(imp -> imp.id("id1").ext(emptyImpExt)),
                givenImp(imp -> imp.id("id2").ext(emptyImpExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).isEmpty();

        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Invalid request. assetKey/adUnitId or request.site/app.publisher.id required"));
    }

    @Test
    public void makeHttpRequestsShouldCopyImpIdToTagId() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1").ext(givenEmptyImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .flatExtracting(Imp::getTagid)
                .containsExactly("id1");

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCleanImpExtWithoutLostExtraFields() {
        // given
        final ObjectNode extWithOguryKeys = givenImpExtWithOguryKeys();
        extWithOguryKeys.put("extra_field", "extra_value");

        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1").ext(extWithOguryKeys)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .hasSize(1)
                .first()
                .isEqualTo(mapper.valueToTree(Map.of(
                        "extra_field", "extra_value",
                        "adUnitId", "1",
                        "assetKey", "key")));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldConvertPriceIfCurrencyIsDifferentFromUSD() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1")
                        .bidfloorcur("CA")
                        .bidfloor(BigDecimal.valueOf(1.5))
                        .ext(givenEmptyImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(1)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isEqualTo("USD");
                    assertThat(imp.getBidfloor()).isEqualTo(BigDecimal.valueOf(1));
                });

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotConvertPriceIfCurrencyIsUSD() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1")
                        .bidfloorcur("USD")
                        .bidfloor(BigDecimal.valueOf(1.5))
                        .ext(givenEmptyImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(1)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isEqualTo("USD");
                    assertThat(imp.getBidfloor()).isEqualTo(BigDecimal.valueOf(1.5));
                });

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotConvertPriceIfCurrencyIsAbsent() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1")
                        .bidfloor(BigDecimal.valueOf(1.5))
                        .ext(givenEmptyImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(1)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isNull();
                    assertThat(imp.getBidfloor()).isEqualTo(BigDecimal.valueOf(1.5));
                });

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotConvertPriceIfFloorIsAbsent() {
        // given
        final BidRequest bidrequest = givenBidRequest(
                bidRequest -> bidRequest.site(givenSite()),
                givenImp(imp -> imp.id("id1").ext(givenEmptyImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(1)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isNull();
                    assertThat(imp.getBidfloor()).isNull();
                });
        verifyNoInteractions(currencyConversionService);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldNotReturnErrorWhenResponseBodyIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorWhenBidMTypeIsNotPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.impid("123")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Missing MType for impression: `123`"));
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorWhenBidMTypeIsNotSupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.impid("123").mtype(10)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unsupported MType '10', for impression '123'"));
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(1)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidderResponseShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(2)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(3)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
    }

    @Test
    public void makeBidderResponseShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(4)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithCurFromResponse() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(1)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("CUR");
    }

    private static String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer)
            throws JsonProcessingException {

        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("CUR")
                .build());
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        final BidRequest.BidRequestBuilder bidRequestBuilder = BidRequest.builder()
                .id("id");

        if (imps != null) {
            final List<Imp> impressions = Arrays.stream(imps).toList();
            bidRequestBuilder.imp(impressions);
        }

        return bidRequestCustomizer.apply(bidRequestBuilder).build();
    }

    private BidRequest givenBidRequest() {
        return givenBidRequest(
                bidRequest -> bidRequest.device(Device.builder()
                        .ua("ua")
                        .ip("0.0.0.0")
                        .ipv6("ip6")
                        .language("en-US")
                        .build()),
                givenImp(imp -> imp
                        .id("imp_id")
                        .bidfloor(BigDecimal.TWO)
                        .bidfloorcur("CAD")
                        .ext(givenImpExtWithOguryKeys())));

    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer
                .apply(Imp.builder())
                .build();
    }

    private Site givenSite() {
        return Site.builder()
                .publisher(Publisher.builder()
                        .id("publiser_id")
                        .build())
                .build();
    }

    private App givenApp() {
        return App.builder()
                .bundle("app_bundle")
                .publisher(Publisher.builder()
                        .id("publiser_id")
                        .build())
                .build();
    }

    private ObjectNode givenEmptyImpExt() {
        final ObjectNode ext = mapper.createObjectNode();
        ext.putIfAbsent("bidder", mapper.createObjectNode());

        return ext;
    }

    private ObjectNode givenImpExtWithOguryKeys() {
        final ObjectNode ext = mapper.createObjectNode();
        final ObjectNode ogury = ext.putObject("bidder");

        ogury.put("adUnitId", "1");
        ogury.put("assetKey", "key");

        return ext;
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(HttpResponseStatus.OK.code(), null, body),
                null);
    }
}
