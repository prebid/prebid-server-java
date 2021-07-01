package org.prebid.server.bidder.madvertise;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.madvertise.ExtImpMadvertise;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class MadvertiseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://mobile.mng-ads.com/bidrequest{{ZoneID}}";

    private MadvertiseBidder madvertiseBidder;

    @Before
    public void setUp() {
        madvertiseBidder = new MadvertiseBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MadvertiseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainEmptyZoneIdParam() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMadvertise.of("")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = madvertiseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("The minLength of zone ID is 7; ImpID=123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainNullZoneIdParam() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMadvertise.of(null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = madvertiseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("The minLength of zone ID is 7; ImpID=123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfMultipleImpExtHaveDifferentZoneIdParam() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMadvertise.of("someZoneIdLongerThan7")))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .id("124")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMadvertise.of("anotherZoneIdLongerThan7")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = madvertiseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("There must be only one zone ID"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMadvertise.of("someZoneIdLongerThan7")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").dnt(5).ip("someIp").language("someLanguage").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = madvertiseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "someUa"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "someIp"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = madvertiseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing bidder ext in impression with id: 123"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(
                                Format.builder()
                                        .w(300).h(500)
                                        .build()))
                        .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMadvertise.of("someZoneIdLongerThan7")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = madvertiseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://mobile.mng-ads.com/bidrequestsomeZoneIdLongerThan7");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = madvertiseBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = madvertiseBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = madvertiseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfSixAndSevenAndSixteenIsAbsentInRequestImp()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = madvertiseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfImpAttrContainsSixOrSevenOrSixteen() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponses(asList(
                                bidBuilder -> bidBuilder.impid("123").attr(singletonList(6)),
                                bidBuilder -> bidBuilder.impid("124").attr(singletonList(16)),
                                bidBuilder -> bidBuilder.impid("125").attr(singletonList(7))))));

        // when
        final Result<List<BidderBid>> result = madvertiseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactlyInAnyOrder(
                        BidderBid.of(Bid.builder().impid("123").attr(singletonList(6)).build(), video, null),
                        BidderBid.of(Bid.builder().impid("124").attr(singletonList(16)).build(), video, null),
                        BidderBid.of(Bid.builder().impid("125").attr(singletonList(7)).build(), video, null));
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
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of("127.0.0.1", "pubId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidResponse givenBidResponses(List<Function<Bid.BidBuilder, Bid.BidBuilder>> bidCustomizers) {
        return BidResponse.builder()
                .seatbid(bidCustomizers.stream().map(customizer -> SeatBid.builder()
                        .bid(singletonList(customizer.apply(Bid.builder()).build()))
                        .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
