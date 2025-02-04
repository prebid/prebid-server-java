package org.prebid.server.bidder.insticator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.insticator.ExtImpInsticator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class InsticatorBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;

    private InsticatorBidder target;

    @BeforeEach
    public void before() {
        target = new InsticatorBidder(currencyConversionService, ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InsticatorBidder(
                currencyConversionService, "invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value");
        });
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerAdUnitId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1").ext(givenImpExt("1")),
                imp -> imp.id("givenImp2").ext(givenImpExt("1")),
                imp -> imp.id("givenImp3").ext(givenImpExt("2")));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getImp().stream().map(Imp::getId).collect(Collectors.toList()))
                .containsExactlyInAnyOrder(List.of("givenImp1", "givenImp2"), List.of("givenImp3"));

        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(Set.of("givenImp1", "givenImp2"), Set.of("givenImp3"));
    }

    @Test
    public void makeHttpRequestsShouldHaveCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp"));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactlyInAnyOrder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWithIpWhenDeviceHasIp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .device(Device.builder().ip("ip").ua("ua").ipv6("ipv6").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER))
                        .isEqualTo("ua"))
                .satisfies(headers -> assertThat(headers.get("IP"))
                        .isEqualTo("ip"))
                .satisfies(headers -> assertThat(headers.get(X_FORWARDED_FOR_HEADER))
                        .isEqualTo("ip"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWithIpv6WhenDeviceHasIpv6AndDoesNotHaveIp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .device(Device.builder().ip(null).ua("ua").ipv6("ipv6").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER))
                        .isEqualTo("ua"))
                .satisfies(headers -> assertThat(headers.get("IP"))
                        .isNull())
                .satisfies(headers -> assertThat(headers.get(X_FORWARDED_FOR_HEADER))
                        .isEqualTo("ipv6"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER)).isNull())
                .satisfies(headers -> assertThat(headers.get("IP")).isNull())
                .satisfies(headers -> assertThat(headers.get(X_FORWARDED_FOR_HEADER)).isNull());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldConvertAndReturnProperBidFloorCur() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.ONE);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .bidfloorcur("EUR")
                .bidfloor(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .first()
                .satisfies(imps -> {
                    assertThat(imps.getBidfloorcur()).isEqualTo("USD");
                    assertThat(imps.getBidfloor()).isEqualTo(BigDecimal.ONE);
                });
    }

    @Test
    public void makeHttpRequestsShouldNotConvertAndReturnUSDBidFloorCurWhenBidFloorNotPositiveNumber() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.ONE);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .bidfloorcur("EUR")
                .bidfloor(BigDecimal.ZERO));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isEqualTo("EUR");
                    assertThat(imp.getBidfloor()).isEqualTo(BigDecimal.ZERO);
                });

        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldThrowErrorWhenCurrencyConvertCannotConvertInAnotherCurrency() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenThrow(
                new PreBidException("Unable to convert from currency UAH to desired ad server currency USD"));

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .bidfloorcur("UAH")
                .bidfloor(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Unable to convert from currency UAH to desired ad server currency USD");
    }

    @Test
    public void makeHttpRequestsShouldReturnImpWithUpdatedExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp").ext(givenImpExt("adUnitId", "publisherId")));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedNode = mapper.createObjectNode();
        expectedNode.set("adUnitId", TextNode.valueOf("adUnitId"));
        expectedNode.set("publisherId", TextNode.valueOf("publisherId"));
        final ObjectNode expectedImpExt = mapper.createObjectNode().set("insticator", expectedNode);

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldReturnExtRequestInsticatorWithDefaultCallerWhenInsticatorIsAbsent() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.empty();
        givenExtRequest.addProperty("insticator",
                mapper.createObjectNode().set("caller",
                        mapper.createArrayNode().add(mapper.createObjectNode()
                                .put("name", "something")
                                .put("version", "1.0"))));
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp"))
                .toBuilder()
                .ext(givenExtRequest)
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();

        final ExtRequest expectedExtRequest = ExtRequest.empty();
        expectedExtRequest.addProperty("insticator",
                mapper.createObjectNode().set("caller",
                        mapper.createArrayNode()
                                .add(mapper.createObjectNode()
                                        .put("name", "something")
                                        .put("version", "1.0"))
                                .add(mapper.createObjectNode()
                                        .put("name", "Prebid-Server")
                                        .put("version", "n/a"))));

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getExt)
                .containsExactly(expectedExtRequest);
    }

    @Test
    public void makeHttpRequestsShouldAddsToExtRequestInsticatorDefaultCaller() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp"))
                .toBuilder()
                .ext(ExtRequest.empty())
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();

        final ExtRequest expectedExtRequest = ExtRequest.empty();
        expectedExtRequest.addProperty("insticator",
                mapper.createObjectNode().set("caller",
                        mapper.createArrayNode().add(mapper.createObjectNode()
                                .put("name", "Prebid-Server")
                                .put("version", "n/a"))));

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getExt)
                .containsExactly(expectedExtRequest);
    }

    @Test
    public void makeHttpRequestsShouldAddsToExtRequestInsticatorDefaultCallerWhenExistingInsticatorCanNotBeParsed() {
        // given
        final ExtRequest givenExtRequest = ExtRequest.empty();
        givenExtRequest.addProperty("insticator", mapper.createArrayNode());
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp"))
                .toBuilder()
                .ext(givenExtRequest)
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value of type "
                    + "`org.prebid.server.bidder.insticator.InsticatorExtRequest`");
        });

        final ExtRequest expectedExtRequest = ExtRequest.empty();
        expectedExtRequest.addProperty("insticator",
                mapper.createObjectNode().set("caller",
                        mapper.createArrayNode().add(mapper.createObjectNode()
                                .put("name", "Prebid-Server")
                                .put("version", "n/a"))));

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getExt)
                .containsExactly(expectedExtRequest);
    }

    @Test
    public void makeHttpRequestsShouldModifyAppWithPublisherIdOfTheFirstImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImpId1").ext(givenImpExt("adUnitId1", "publisherId1")),
                imp -> imp.id("givenImpId2").ext(givenImpExt("adUnitId2", "publisherId2")))
                .toBuilder()
                .app(App.builder().publisher(Publisher.builder().id("id").build()).build())
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("publisherId1", "publisherId1");
    }

    @Test
    public void makeHttpRequestsShouldModifySiteWithPublisherIdOfTheFirstImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImpId1").ext(givenImpExt("adUnitId1", "publisherId1")),
                imp -> imp.id("givenImpId2").ext(givenImpExt("adUnitId2", "publisherId2")))
                .toBuilder()
                .site(Site.builder().publisher(Publisher.builder().id("id").build()).build())
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("publisherId1", "publisherId1");
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestWhenOneImpIsValidAndAnotherAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImpId1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                imp -> imp.id("givenImpId2"),
                imp -> imp.id("givenImpId3").video(givenVideo(video -> video.mimes(null))),
                imp -> imp.id("givenImpId3").video(givenVideo(video -> video.mimes(Collections.emptyList()))),
                imp -> imp.id("givenImpId4").video(givenVideo(video -> video.h(null))),
                imp -> imp.id("givenImpId5").video(givenVideo(video -> video.h(0))),
                imp -> imp.id("givenImpId6").video(givenVideo(video -> video.w(null))),
                imp -> imp.id("givenImpId7").video(givenVideo(video -> video.w(0))));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("givenImpId2");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid':");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyBidsWhenResponseDoesNotHaveSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid.impid("1").mtype(1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(1).impid("1").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid.impid("2").mtype(2)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(2).impid("2").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsUnknown() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid.impid("3").mtype(3)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(3).impid("3").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid.impid("3").mtype(null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(null).impid("3").build(), banner, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(InsticatorBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null,
                                ExtImpInsticator.of("adUnitId", "publisherId")))))
                .build();
    }

    private static Video givenVideo(UnaryOperator<Video.VideoBuilder> videoCustomizer) {
        return videoCustomizer.apply(Video.builder()
                        .mimes(List.of("video/mp4"))
                        .h(100)
                        .w(100))
                .build();
    }

    private static ObjectNode givenImpExt(String adUnitId) {
        return givenImpExt(adUnitId, "publisherId");
    }

    private static ObjectNode givenImpExt(String adUnitId, String publisherId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpInsticator.of(adUnitId, publisherId)));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build());
    }

}
