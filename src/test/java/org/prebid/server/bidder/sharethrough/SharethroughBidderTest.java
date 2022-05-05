package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.assertj.core.api.AssertionsForClassTypes;
import org.jetbrains.annotations.NotNull;
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
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class SharethroughBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private SharethroughBidder sharethroughBidder;

    @Before
    public void setUp() {
        sharethroughBidder = new SharethroughBidder(ENDPOINT_URL,
                currencyConversionService,
                prebidVersionProvider,
                jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SharethroughBidder("invalid_url",
                        currencyConversionService,
                        prebidVersionProvider,
                        jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestsShouldCreateSourceIfDoesNotExistAndReturnProperExtSourceVersionAndStr() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        when(prebidVersionProvider.getNameVersionRecord()).thenReturn("v2");
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSource)
                .extracting(Source::getExt)
                .extracting(FlexibleExtension::getProperties)
                .containsExactly(expectedResponse());
    }

    @Test
    public void makeHttpRequestsShouldAddExtToTheSourceIfExistAndReturnProperExtSourceVersionAndStr() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.source(Source.builder().build()), identity());

        // when
        when(prebidVersionProvider.getNameVersionRecord()).thenReturn("v2");
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSource)
                .extracting(Source::getExt)
                .extracting(FlexibleExtension::getProperties)
                .containsExactly(expectedResponse());
    }

    @Test
    public void makeHttpRequestsShouldPopulateProperSourceExtAndNotWipeData() {
        // given
        final TextNode givenTextNode = TextNode.valueOf("test");
        final ExtSource givenExtSource = ExtSource.of(null);
        givenExtSource.addProperty("test-field", givenTextNode);
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.source(Source.builder().ext(givenExtSource).build()),
                identity());

        // when
        when(prebidVersionProvider.getNameVersionRecord()).thenReturn("v2");
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, JsonNode> stringJsonNodeMap = expectedResponse();
        stringJsonNodeMap.put("test-field", givenTextNode);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSource)
                .extracting(Source::getExt)
                .extracting(FlexibleExtension::getProperties)
                .containsExactly(stringJsonNodeMap);
    }

    @Test
    public void makeHttpRequestsShouldReturnProperBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getBcat, BidRequest::getBadv)
                .containsExactly(tuple(singletonList("imp.ext.bcat"), singletonList("imp.ext.badv")));
    }

    @Test
    public void makeHttpRequestsShouldProperPopulateBidRequestBcatAndBadvIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .bcat(singletonList("req.bcat"))
                .badv(singletonList("req.badv")), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getBcat, BidRequest::getBadv)
                .containsExactly(tuple(List.of("req.bcat", "imp.ext.bcat"), List.of("req.badv", "imp.ext.badv")));
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyIfRequestCurrencyDoesNotMatchBidderCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).bidfloorcur("EUR"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(AssertionsForClassTypes.tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageOnFailedCurrencyConversion() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willThrow(PreBidException.class);

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer.bidfloor(BigDecimal.ONE).bidfloorcur("EUR"));

        // when
        Result<List<HttpRequest<BidRequest>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .allSatisfy(bidderError -> assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @NotNull
    private static Map<String, JsonNode> expectedResponse() {
        final Map<String, JsonNode> stringToJsonNode = new HashMap<>();
        stringToJsonNode.put("str", TextNode.valueOf("10.0"));
        stringToJsonNode.put("version", TextNode.valueOf("v2"));
        return stringToJsonNode;
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSharethrough.of(
                                "pkey",
                                singletonList("imp.ext.badv"),
                                singletonList("imp.ext.bcat"))))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
