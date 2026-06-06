package org.prebid.server.bidder.floxis;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.floxis.ExtImpFloxis;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class FloxisBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{Host}}/pbs?seat={{SeatId}}";

    private final FloxisBidder target = new FloxisBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloxisBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenNoImpressions() {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("req-1").imp(null).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("no impressions in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("imp-1")
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("invalid imp.ext.bidder for imp imp-1:");
                });
    }

    @Test
    public void makeHttpRequestsShouldUrlEscapeSeatAndUseEuHost() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("a b&c", "eu")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eu.floxis.tech/pbs?seat=a+b%26c");
    }

    @Test
    public void makeHttpRequestsShouldUseApacHost() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("seat-apac", "apac")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://apac.floxis.tech/pbs?seat=seat-apac");
    }

    @Test
    public void makeHttpRequestsShouldUseUseHostForExplicitUsE() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", "us-e")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://us-e.floxis.tech/pbs?seat=abc");
    }

    @Test
    public void makeHttpRequestsShouldDefaultToUseHostWhenRegionMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://us-e.floxis.tech/pbs?seat=abc");
    }

    @Test
    public void makeHttpRequestsShouldUseArbitraryValidRegionLabelAsSubdomain() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", "mars")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://mars.floxis.tech/pbs?seat=abc");
    }

    @Test
    public void makeHttpRequestsShouldReturnBadInputWhenRegionIsNotAValidHostLabel() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", "evil.com/x?")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input));
    }

    @Test
    public void makeHttpRequestsShouldUsePartnerPrefixedHostWhenPartnerProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", "us-e", "acme")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://acme-us-e.floxis.tech/pbs?seat=abc");
    }

    @Test
    public void makeHttpRequestsShouldReturnBadInputWhenPartnerIsNotAValidHostLabel() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", "us-e", "evil.com/x?")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input));
    }

    @Test
    public void makeHttpRequestsShouldRouteOnceAndForwardAllImpsWhenSeatAndRegionMatch() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("req-1")
                .imp(asList(
                        givenImp(imp -> imp.id("imp-1").ext(givenImpExt("seat-eu", "eu"))),
                        givenImp(imp -> imp.id("imp-2").ext(givenImpExt("seat-eu", "eu")))))
                .site(Site.builder().id("271").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eu.floxis.tech/pbs?seat=seat-eu");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp-1", "imp-2");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpsTargetDifferentSeat() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("req-1")
                .imp(asList(
                        givenImp(imp -> imp.id("imp-1").ext(givenImpExt("seat-eu", "eu"))),
                        givenImp(imp -> imp.id("imp-2").ext(givenImpExt("seat-other", "eu")))))
                .site(Site.builder().id("271").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "all impressions must target the same Floxis seat, region and partner; "
                                + "imp imp-2 differs from imp imp-1"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpsTargetDifferentRegion() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("req-1")
                .imp(asList(
                        givenImp(imp -> imp.id("imp-1").ext(givenImpExt("seat-eu", "eu"))),
                        givenImp(imp -> imp.id("imp-2").ext(givenImpExt("seat-eu", "apac")))))
                .site(Site.builder().id("271").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "all impressions must target the same Floxis seat, region and partner; "
                                + "imp imp-2 differs from imp imp-1"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpsTargetDifferentPartner() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("req-1")
                .imp(asList(
                        givenImp(imp -> imp.id("imp-1").ext(givenImpExt("seat-eu", "eu", "acme"))),
                        givenImp(imp -> imp.id("imp-2").ext(givenImpExt("seat-eu", "eu", "other")))))
                .site(Site.builder().id("271").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "all impressions must target the same Floxis seat, region and partner; "
                                + "imp imp-2 differs from imp imp-1"));
    }

    @Test
    public void makeHttpRequestsShouldForwardRequestBodyUnchanged() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("abc", "eu")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCannotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response));
    }

    @Test
    public void makeBidsShouldReturnEmptyWhenResponseHasNoSeatBid() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipNullSeatBidAndEmptyBids() throws Exception {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(asList(
                        null,
                        SeatBid.builder().bid(null).build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().impid("imp-1").mtype(1).build()))
                                .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldResolveBidTypeFromMTypeAndPassCurrency() throws Exception {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(asList(
                                Bid.builder().impid("imp-1").mtype(1).build(),
                                Bid.builder().impid("imp-1").mtype(2).build(),
                                Bid.builder().impid("imp-1").mtype(3).build(),
                                Bid.builder().impid("imp-1").mtype(4).build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, BidderBid::getBidCurrency)
                .containsExactly(
                        tuple(BidType.banner, "EUR"),
                        tuple(BidType.video, "EUR"),
                        tuple(BidType.audio, "EUR"),
                        tuple(BidType.xNative, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeUnsupported() throws Exception {
        // given
        final BidResponse bidResponse = givenBidResponse(Bid.builder().impid("imp-1").mtype(7).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "unsupported bid.mtype 7 for impression imp-1"));
    }

    @Test
    public void makeBidsShouldResolveBidTypeFromSingleFormatImpWhenMTypeAbsent() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("b").banner(Banner.builder().build()).build(),
                        Imp.builder().id("v").video(Video.builder().build()).build(),
                        Imp.builder().id("a").audio(Audio.builder().build()).build(),
                        Imp.builder().id("n").xNative(Native.builder().build()).build()))
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(asList(
                                Bid.builder().impid("b").build(),
                                Bid.builder().impid("v").build(),
                                Bid.builder().impid("a").build(),
                                Bid.builder().impid("n").build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner, BidType.video, BidType.audio, BidType.xNative);
    }

    @Test
    public void makeBidsShouldReturnErrorForMultiFormatImpWithoutMType() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("imp-1")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .build()))
                .build();
        final BidResponse bidResponse = givenBidResponse(Bid.builder().impid("imp-1").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "bid for multi-format imp imp-1 requires bid.mtype to disambiguate"));
    }

    @Test
    public void makeBidsShouldReturnErrorForImpWithoutFormatAndWithoutMType() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp-1").build()))
                .build();
        final BidResponse bidResponse = givenBidResponse(Bid.builder().impid("imp-1").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "unable to resolve media type for impression imp-1"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidImpIdNotFound() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp-1").banner(Banner.builder().build()).build()))
                .build();
        final BidResponse bidResponse = givenBidResponse(Bid.builder().impid("no-such-imp").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "unable to find impression no-such-imp for bid"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .id("req-1")
                .imp(singletonList(givenImp(impCustomizer)))
                .site(Site.builder().id("271").build())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp-1")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .ext(givenImpExt("pub-seat-1", null)))
                .build();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode givenImpExt(String seat, String region) {
        return givenImpExt(seat, region, null);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode givenImpExt(String seat, String region,
                                                                             String partner) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpFloxis.of(seat, region, partner)));
    }

    private static BidResponse givenBidResponse(Bid bid) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
