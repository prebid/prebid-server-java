package org.prebid.server.bidder.dmx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.dmx.ExtImpDmx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class DmxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private DmxBidder dmxBidder;

    @Before
    public void setUp() {
        dmxBidder = new DmxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DmxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfUserOrAppIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No user id or app id found. Could not send request to DMX."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfUserIdIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpDmx.builder()
                                        .tagId("tagId")
                                        .dmxId("dmxId")
                                        .memberId("memberId")
                                        .publisherId("publisherId")
                                        .sellerId("sellerId")
                                        .build())))
                        .build()))
                .user(User.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("This request contained no identifier");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpIfBannerFormatIsNotEmpty() {
        // given
        final Imp givenImp = Imp.builder()
                .id("id")
                .bidfloor(BigDecimal.ONE)
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("tagId")
                                .dmxId("dmxId")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .sellerId("sellerId")
                                .build())))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp))
                .user(User.builder().id("userId").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .tagid("dmxId")
                .secure(1)
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .containsExactly(expectedImp);
    }

    @Test
    public void makeHttpRequestsShouldWriteTagIdToImpIfItIsPresentAndDmxIsMissing() {
        // given
        final Imp givenImp = Imp.builder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("tagId")
                                .dmxId("")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .build())))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp))
                .user(User.builder().id("userId").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("tagId");
    }

    @Test
    public void makeHttpRequestsShouldSkipImpIfTagIdAndDmxIdAreBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .id("id")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(300).h(500).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                        ExtImpDmx.builder()
                                                .tagId("")
                                                .dmxId("")
                                                .memberId("memberId")
                                                .publisherId("publisherId")
                                                .sellerId("sellerId")
                                                .build())))
                                .build()))
                .user(User.builder().id("userId").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .user(User.builder().id("userId").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .id("123")
                                .banner(Banner.builder().id("banner_id").build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                        ExtImpDmx.builder()
                                                .tagId("tagId")
                                                .dmxId("dmxId")
                                                .memberId("memberId")
                                                .publisherId("publisherId")
                                                .sellerId("sellerId")
                                                .build())))
                                .build()))
                .user(User.builder().id("userId").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://test.endpoint.com?sellerid=sellerId");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123")
                                .adm("<Impression><![CDATA[https://gce-sc]]></Impression>"
                                        + "<Impression><![CDATA[https://us-east]]></Impression>")
                                .nurl("nurl"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall, bidRequest);

        // then
        final String adm = "<Impression><![CDATA[https://gce-sc]]></Impression><Impression><![CDATA[nurl]]>"
                + "</Impression><Impression><![CDATA[https://us-east]]></Impression>";
        final BidderBid expected = BidderBid.of(Bid.builder().impid("123").adm(adm).nurl("nurl").build(), BidType.video,
                "USD");

        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo(adm);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0).isEqualTo(expected);
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").adm("adm"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").adm("adm").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnResponseWithErrorWhenIdIsNotFound() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("12"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall, BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build());

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("Failed to find impression 12"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().id("banner_id").build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("tagId")
                                .dmxId("dmxId")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .sellerId("sellerId")
                                .build()))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
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
