package org.prebid.server.bidder.admixer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Audio;
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
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.admixer.ExtImpAdmixer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdmixerBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private AdmixerBidder admixerBidder;

    @Before
    public void setUp() {
        admixerBidder = new AdmixerBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdmixerBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void shouldSetBidfloorToZeroIfExtImpFloorValuIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdmixer.of("tentententtentententtentententetetet", null,
                                        givenCustomParams("foo1", singletonList("bar1"))))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = admixerBidder.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = Imp.builder()
                .id("123")
                .tagid("tentententtentententtentententetetet")
                .bidfloor(BigDecimal.ZERO)
                .ext(mapper.valueToTree(ExtImpAdmixer.of(null, null,
                                givenCustomParams("foo1", singletonList("bar1")))))
                .build();
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(expectedImp);
    }

    @Test
    public void shouldSetExtToNullIfCustomParamsAreNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdmixer.of("tentententtentententtentententetetet", null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = admixerBidder.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = Imp.builder()
                .id("123")
                .tagid("tentententtentententtentententetetet")
                .bidfloor(BigDecimal.ZERO)
                .ext(null)
                .build();
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(expectedImp);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = admixerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_input
                && error.getMessage().startsWith("Wrong Admixer bidder ext in imp with id : 123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfZoneIdNotHaveLength() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdmixer.of("zoneId", 36D,
                                givenCustomParams("foo1", singletonList("bar1")))))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = admixerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("ZoneId must be UUID/GUID"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("false");

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("Failed to decode:"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseWhenResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorsWhenSeatBidIsEmptyList() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorsWhenBidsEmptyList()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(
                        BidResponse.builder()
                                .seatbid(singletonList(SeatBid.builder().bid(emptyList()).build()))
                                .build()));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfAudioIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = admixerBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), audio, "USD"));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static Map<String, JsonNode> givenCustomParams(String key, Object values) {
        return singletonMap(key, mapper.valueToTree(values));
    }
}
