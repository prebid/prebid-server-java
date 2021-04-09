package org.prebid.server.bidder.between;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.between.ExtImpBetween;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class BetweenBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{Host}}/test?param={{PublisherId}}";

    private BetweenBidder betweenBidder;

    @Before
    public void setUp() {
        betweenBidder = new BetweenBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BetweenBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainEmptyOrNullHostParam() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("", "pubId", null, null)))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .id("456")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of(null, "pubId", null, null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsExactly(
                        BidderError.badInput("required BetweenSSP parameter host is "
                                + "missing in impression with id: 123"),
                        BidderError.badInput("required BetweenSSP parameter host is "
                                + "missing in impression with id: 456"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfBannerNotPresentOrSizesNotExists() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .banner(null)
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("host", "pubId", null, null)))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("host", "pubId", null, null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsExactlyInAnyOrder(
                        BidderError.badInput("Request needs to include a Banner object"),
                        BidderError.badInput("Need at least one size to build request"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBetween.of("host", "pubId", null, null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").dnt(5).ip("someIp").language("someLanguage").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "someUa"),
                        tuple(HttpUtil.DNT_HEADER.toString(), "5"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "someIp"),
                        tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "someLanguage"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "somePage"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainEmptyOrNullPublisherIdParam() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("host", "", null, null)))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .id("456")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("host", null, null, null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsExactlyInAnyOrder(
                        BidderError.badInput("required BetweenSSP parameter publisher_id "
                                + "is missing in impression with id: 123"),
                        BidderError.badInput("required BetweenSSP parameter publisher_id "
                                + "is missing in impression with id: 456"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing bidder ext in impression with id: 123"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://127.0.0.1/test?param=pubId");
    }

    @Test
    public void makeHttpRequestsShouldEnrichEveryImpWithSecureBidFlorAndBidFloorCurParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().page("https://page.com").build()),
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure, Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(1, BigDecimal.ONE, "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldSetDefaultParamsIfAnotherNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().page("http://page.com").build()),
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("127.0.0.1", "pubId",
                                BigDecimal.valueOf(-1), ""))))
                        .bidfloor(BigDecimal.valueOf(12))
                        .bidfloorcur("USD"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = betweenBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure, Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(0, BigDecimal.valueOf(0.00001), "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = betweenBidder.makeBids(httpCall, null);

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
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = betweenBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = betweenBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = betweenBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = betweenBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = betweenBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().w(23).h(25).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("127.0.0.1", "pubId",
                        BigDecimal.ONE, "EUR")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
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
