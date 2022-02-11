package org.prebid.server.bidder.tappx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.tappx.model.TappxBidderExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.tappx.ExtImpTappx;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class TappxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{subdomain}}.domain";

    private TappxBidder tappxBidder;

    @Before
    public void setUp() {
        tappxBidder = new TappxBidder(ENDPOINT_URL, Clock.systemDefaultZone(), jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequestImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTappx.of(
                                "host", "tappxkey", "endpoint", null,
                                "mktag", singletonList("bcid"), singletonList("bcrid"))))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        final ExtRequest extRequest = ExtRequest.empty();
        final TappxBidderExt tappxBidderExt = TappxBidderExt.builder()
                .tappxkey("tappxkey")
                .mktag("mktag")
                .bcid(singletonList("bcid"))
                .bcrid(singletonList("bcrid"))
                .build();
        extRequest.addProperty("bidder", mapper.valueToTree(tappxBidderExt));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(bidRequest.toBuilder().ext(extRequest).build());
    }

    @Test
    public void makeHttpRequestsShouldModifyBidRequestExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(givenImp(identity()))).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        final ExtRequest extRequest = ExtRequest.empty();
        final TappxBidderExt tappxBidderExt = TappxBidderExt.builder()
                .tappxkey("tappxkey")
                .mktag("mktag")
                .bcid(singletonList("bcid"))
                .bcrid(singletonList("bcrid"))
                .build();
        extRequest.addProperty("bidder", mapper.valueToTree(tappxBidderExt));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(extRequest);
    }

    @Test
    public void makeHttpRequestsShouldSetFirstImpBidFloorFromItsExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldMakeRequestWithUrl() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final String expectedUri = "https://ssp.api.domain/rtb/v2/endpoint?tappxkey=tappxkey&v=1.4&type_cnn=prebid";
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(httpRequest -> {
                    assertThat(httpRequest.getUri()).isEqualTo(expectedUri);
                    assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
                });
    }

    @Test
    public void makeHttpRequestShouldBuildCorrectUriWithPathInHostParameterButWithoutTrailingForwardSlash() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpTappx.of(null, "tappxkey", "endpoint", BigDecimal.ONE,
                                        null, null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final String expectedUri = "https://ssp.api.domain/rtb/v2/endpoint?tappxkey=tappxkey&v=1.4&type_cnn=prebid";
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(httpRequest -> {
                    assertThat(httpRequest.getUri()).isEqualTo(expectedUri);
                    assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
                });
    }

    @Test
    public void makeHttpRequestShouldBuildCorrectUriWithEndPointParameterIfMatched() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpTappx.of(null, "tappxkey", "zz855226test", BigDecimal.ONE,
                                        null, null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final String expectedUri =
                "https://zz855226test.pub.domain/rtb/?tappxkey=tappxkey&v=1.4&type_cnn=prebid";
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(httpRequest -> {
                    assertThat(httpRequest.getUri()).isEqualTo(expectedUri);
                    assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
                });
    }

    @Test
    public void makeHttpRequestsShouldModifyUrl() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpTappx.of(null, "tappxkey", "endpoint", BigDecimal.ONE,
                                        null, null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tappxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final String expectedUri = "https://ssp.api.domain/rtb/v2/endpoint?tappxkey=tappxkey&v=1.4&type_cnn=prebid";
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(httpRequest -> {
                    assertThat(httpRequest.getUri()).isEqualTo(expectedUri);
                    assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = tappxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = tappxBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = tappxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = tappxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnDefaultBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = tappxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTappx.of(
                        "host", "tappxkey", "endpoint", BigDecimal.ONE,
                        "mktag", singletonList("bcid"), singletonList("bcrid")))))
        ).build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
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

