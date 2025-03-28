package org.prebid.server.bidder.connatix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.connatix.ExtImpConnatix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class ConnatixBidderTest extends VertxTest {

    private static final String CONNATIX_ENDPOINT = "https://test-url.com/";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private ConnatixBidder target;

    @BeforeEach
    public void setUp() {
        target = new ConnatixBidder(CONNATIX_ENDPOINT, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpoint() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ConnatixBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldErrorOnMissingDeviceIp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().build())
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Device IP is required"));
    }

    @Test
    public void makeHttpRequestsShouldErrorOnInvalidImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(
                        ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(1)
                .satisfies(errors -> assertThat(errors.getFirst()).startsWith("Cannot deserialize value of type"));
    }

    @Test
    public void makeHttpRequestsShouldUpdateDisplayManagerVer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder().ext(ExtApp.of(
                        ExtAppPrebid.of("source", "version"), null))
                        .build()),
                givenImp(ExtImpConnatix.of("placementId", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("source-version");
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateDisplayManagerVerIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder()
                        .ext(ExtApp.of(ExtAppPrebid.of("source", "version"), null))
                        .build()),
                givenImp(impBuilder -> impBuilder.displaymanagerver("displayManagerVer")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpConnatix.of("placementId", null))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("displayManagerVer");
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateBannerIfFormatsIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                                .w(100)
                                .h(200)
                                .format(Collections.emptyList())
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpConnatix.of("placementId", null))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(Banner.builder().w(100).h(200).format(Collections.emptyList()).build());
    }

    @Test
    public void makeHttpRequestsShouldUpdateBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder().format(List.of(
                        Format.builder().w(300).h(250).build(),
                                Format.builder().w(1).h(1).build())).build())
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpConnatix.of("placementId", null))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(Banner.builder()
                        .w(300)
                        .h(250)
                        .format(List.of(
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(1).h(1).build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorWhenNotInBidderCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpConnatix.of("placementId", null))))
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("EUR")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldSplitRequestIntoMultipleRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpConnatix.of("placement1", null))))),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpConnatix.of("placement2", null))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
    }

    @Test
    public void makeHttpRequestsShouldIncludeResolvedHttpHeadersFromDevice() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder().ip("deviceIp").ipv6("deviceIpv6").ua("userAgent").build()),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtImpConnatix.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "userAgent"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "deviceIp"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "deviceIpv6"),
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestsShouldUseDataCenterUsEast2WhenUserIdStartsWith1() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder().buyeruid("1-UserId").build()),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtImpConnatix.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        httpRequests.forEach(request -> {
            assertThat(request.getUri().contains("dc=us-east-2"));
        });
    }

    @Test
    public void makeHttpRequestsShouldUseDataCenterUsWest2WhenUserIdStartsWith2() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder().buyeruid("2-UserId").build()),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtImpConnatix.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        httpRequests.forEach(request -> {
            assertThat(request.getUri().contains("dc=us-west-2"));
        });
    }

    @Test
    public void makeHttpRequestsShouldUseDataCenterEuWest1WhenUserIdStartsWith3() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder().buyeruid("3-UserId").build()),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtImpConnatix.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        httpRequests.forEach(request -> {
            assertThat(request.getUri().contains("dc=eu-west-1"));
        });
    }

    @Test
    public void makeHttpRequestsShouldExcludeDataCenterWhenUserIdPrefixDoesNotMatch() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder().buyeruid("4-UserId").build()),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtImpConnatix.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        httpRequests.forEach(request -> {
            assertThat(request.getUri() == CONNATIX_ENDPOINT);
        });
    }

    @Test
    public void makeHttpRequestsShouldExcludeDataCenterWhenUserIdIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtImpConnatix.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        httpRequests.forEach(request -> {
            assertThat(request.getUri() == CONNATIX_ENDPOINT);
        });
    }

    @Test
    public void makeBidsShouldErrorIfResponseBodyCannotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode:");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("impId").build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final ObjectNode mediaType = mapper.createObjectNode().put("mediaType", "video");
        final ObjectNode cnxWrapper = mapper.createObjectNode().set("connatix", mediaType);
        final Bid bid = Bid.builder().impid("impId").ext(cnxWrapper).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bid, BidType.video, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .device(Device.builder().ip("deviceIp").build())
                .imp(asList(imps)))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static Imp givenImp(ExtImpConnatix extImpConnatix) {
        return givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, extImpConnatix))));
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse response) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(response));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest request,
                                                        String response) throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(request).build(),
                HttpResponse.of(200, null, response),
                null);
    }

    private static String givenBidResponse(Bid... bid) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bid))
                        .build()))
                .build());
    }
}
