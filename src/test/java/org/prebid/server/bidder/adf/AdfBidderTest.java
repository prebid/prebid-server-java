package org.prebid.server.bidder.adf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.adf.ExtImpAdf;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdfBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private AdfBidder adfBidder;

    @Before
    public void setup() {
        adfBidder = new AdfBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adfBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedRequest = bidRequest.toBuilder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder.tagid("12345"))))
                .ext(givenExtRequest())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedRequest);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithPriceTypeExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adfBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(givenExtRequest());
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestForAllImpsAndReturnCorrectTagId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.imp(List.of(
                        givenEmptyImp(impBuilder -> impBuilder.ext(givenImpExt("12345", "gross"))),
                        givenEmptyImp(impBuilder -> impBuilder.ext(givenImpExt("1", "gross"))),
                        givenEmptyImp(impBuilder -> impBuilder.ext(givenImpExt("gross"))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adfBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("12345", "1", null);
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestForAllImpsAndReturnCorrectExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.imp(List.of(
                        givenEmptyImp(impBuilder -> impBuilder.ext(givenImpExt("12345", "gross"))),
                        givenEmptyImp(impBuilder -> impBuilder.ext(givenImpExt("1", "net"))),
                        givenEmptyImp(impBuilder -> impBuilder.ext(givenImpExt(321, "placement"))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adfBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(
                        givenImpExt("12345", "gross"),
                        givenImpExt("1", "net"),
                        givenImpExt(321, "placement")
                );
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").banner(Banner.builder()
                        .w(1)
                        .h(1)
                        .build()).build())).build(),
                mapper.writeValueAsString(givenBidResponse(
                        bidBuilder -> bidBuilder.impid("123").ext(createBidExtPrebidWithType("banner")))));

        // when
        final Result<List<BidderBid>> result = adfBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .impid("123").ext(createBidExtPrebidWithType("banner")).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").video(Video.builder()
                        .w(1)
                        .h(1)
                        .build()).build())).build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .ext(createBidExtPrebidWithType("video")))));

        // when
        final Result<List<BidderBid>> result = adfBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .impid("123").ext(createBidExtPrebidWithType("video")).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123")
                        .xNative(new Native())
                        .build())).build(),
                mapper.writeValueAsString(givenBidResponse(
                        bidBuilder -> bidBuilder.impid("123").ext(createBidExtPrebidWithType("native")))));

        // when
        final Result<List<BidderBid>> result = adfBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .impid("123").ext(createBidExtPrebidWithType("native")).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldThrowErrorIfNoMediaType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123")
                        .build())).build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adfBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldThrowErrorIfWrongMediaType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123")
                        .build())).build(),
                mapper.writeValueAsString(givenBidResponse(
                        bidBuilder -> bidBuilder.impid("123").ext(createBidExtPrebidWithType("invalidMedia")))));

        // when
        final Result<List<BidderBid>> result = adfBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impCustomizer,
            UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, UnaryOperator.identity());
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123"))
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdf.of("12345", null, null, "gross"))))
                .build();
    }

    private static Imp givenEmptyImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }

    private static BidResponse givenBidResponse(
            UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static ObjectNode createBidExtPrebidWithType(String type) {
        return mapper.createObjectNode().set("prebid", mapper.createObjectNode().put("type", type));
    }

    private ObjectNode givenImpExt(Integer inv, String mname) {
        return mapper.valueToTree(ExtPrebid.of(null,
                ExtImpAdf.of(null, inv, mname, null)));
    }

    private ObjectNode givenImpExt(String mid, String priceTyp) {
        return mapper.valueToTree(ExtPrebid.of(null,
                ExtImpAdf.of(mid, null, null, priceTyp)));
    }

    private ObjectNode givenImpExt(String priceTyp) {
        return mapper.valueToTree(ExtPrebid.of(null,
                ExtImpAdf.of(null, null, null, priceTyp)));
    }

    private ExtRequest givenExtRequest() {
        return jacksonMapper.fillExtension(ExtRequest.empty(),
                mapper.createObjectNode().put("pt", "gross"));
    }
}
