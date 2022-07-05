package org.prebid.server.bidder.kargo;

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
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kargo.ExtImpKargo;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class KargoBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private KargoBidder kargoBidder;

    @Before
    public void setUp() {
        kargoBidder = new KargoBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KargoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnSuccessResult() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = kargoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .containsExactly(givenBidRequest(identity()));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInResponseBidExt() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInInResponseBidExt() throws JsonProcessingException {
        // given
        final ObjectNode mediaType = mapper.createObjectNode().put("mediaType", "video");
        final String impid = "123";
        final BidderCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder
                        .impid(impid)
                        .ext(mediaType))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .allSatisfy(bidderBid -> {
                    assertThat(bidderBid.getBid().getImpid()).isEqualTo(impid);
                    assertThat(bidderBid.getType()).isEqualTo(video);
                    assertThat(bidderBid.getBid().getExt()).isEqualTo(mediaType);
                    assertThat(bidderBid.getBidCurrency()).isNull();
                });
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresentInResponseBidExt() throws JsonProcessingException {
        // given
        final ObjectNode mediaType = mapper.createObjectNode().put("mediaType", xNative.getName());
        final String impid = "123";
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder
                        .impid(impid)
                        .ext(mediaType))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .allSatisfy(bidderBid -> {
                    assertThat(bidderBid.getBid().getImpid()).isEqualTo(impid);
                    assertThat(bidderBid.getType()).isEqualTo(xNative);
                    assertThat(bidderBid.getBid().getExt()).isEqualTo(mediaType);
                    assertThat(bidderBid.getBidCurrency()).isNull();
                });
    }

    @Test
    public void makeBidsShouldReturnDefaultBannerBidIfBidUnsupportedType() throws JsonProcessingException {
        // given
        final ObjectNode mediaType = mapper.createObjectNode().put("mediaType", audio.getName());
        final String impid = "123";
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder
                        .impid(impid)
                        .ext(mediaType))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .allSatisfy(bidderBid -> {
                    assertThat(bidderBid.getBid().getImpid()).isEqualTo(impid);
                    assertThat(bidderBid.getType()).isEqualTo(banner);
                    assertThat(bidderBid.getBid().getExt()).isEqualTo(mediaType);
                    assertThat(bidderBid.getBidCurrency()).isNull();
                });
    }

    @Test
    public void makeBidsShouldReturnDefaultBannerBidIfBidExtCannotBeParsed() throws JsonProcessingException {
        // given
        final String impid = "123";
        final ObjectNode mediaType = mapper.createObjectNode().set("mediaType", mapper.createArrayNode());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder
                        .impid(impid)
                        .ext(mediaType))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .allSatisfy(bidderBid -> {
                    assertThat(bidderBid.getBid().getImpid()).isEqualTo(impid);
                    assertThat(bidderBid.getType()).isEqualTo(banner);
                    assertThat(bidderBid.getBid().getExt()).isNotEmpty();
                    assertThat(bidderBid.getBidCurrency()).isNull();
                });
    }

    @Test
    public void makeBidsShouldReturnDefaultBannerBidIfBidExtMediaTypeIsNull() throws JsonProcessingException {
        // given
        final String impid = "123";
        final ObjectNode mediaType = mapper.createObjectNode().set("mediaType", null);
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder
                        .impid(impid)
                        .ext(mediaType))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .allSatisfy(bidderBid -> {
                    assertThat(bidderBid.getBid().getImpid()).isEqualTo(impid);
                    assertThat(bidderBid.getType()).isEqualTo(banner);
                    assertThat(bidderBid.getBid().getExt()).isNotEmpty();
                    assertThat(bidderBid.getBidCurrency()).isNull();
                });
    }

    @Test
    public void makeBidsShouldReturnBidderBidIfBannerAndVideoAndNativeIsAbsentInRequestImp()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(impBuilder -> impBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = kargoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizers) {
        return givenBidRequest(identity(), impCustomizers);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              UnaryOperator<Imp.ImpBuilder> impCustomizers) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizers))))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(23).h(25).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKargo.of("adSlotId")))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
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
