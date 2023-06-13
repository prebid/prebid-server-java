package org.prebid.server.bidder.yeahmobi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
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
import org.prebid.server.proto.openrtb.ext.request.yeahmobi.ExtImpYeahmobi;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class YeahmobiBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{Host}}/prebid/bid";

    private YeahmobiBidder yeahmobiBidder;

    @Before
    public void setUp() {
        yeahmobiBidder = new YeahmobiBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new YeahmobiBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = yeahmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid ExtImpYeahmobi value"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOfEveryNotValidImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(impBuilder -> impBuilder
                                .id("123")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                        givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yeahmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Impression id=123, has invalid Ext"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yeahmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://gw-zoneId-bid.yeahtargeter.com/prebid/bid");
    }

    @Test
    public void makeHttpRequestsShouldAddNativeRequestIfEmpty() {
        // given
        String nativeRequest = "{\"ver\":\"1.2\",\"context\":1,\"plcmttype\":4,\"plcmtcnt\":1,\"assets\":[{\"id\":2,"
                + "\"required\":1,\"title\":{\"len\":90}},{\"id\":6,\"required\":1,\"img\":{\"type\":3,\"wmin\""
                + ":128,\"hmin\":128,\"mimes\":[\"image/jpg\",\"image/jpeg\",\"image/png\"]}},{\"id\":7,"
                + "\"required\":1,\"data\":{\"type\":2,\"len\":120}}]}";

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .xNative(Native.builder().request(nativeRequest).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yeahmobiBidder.makeHttpRequests(bidRequest);

        // then
        String expectedNativeRequest = "{\"native\":{\"ver\":\"1.2\",\"context\":1,\"plcmttype\":4,\"plcmtcnt\":1,"
                + "\"assets\":[{\"id\":2,\"required\":1,\"title\":{\"len\":90}},{\"id\":6,\"required\":1,\"img\":"
                + "{\"type\":3,\"wmin\":128,\"hmin\":128,\"mimes\":[\"image/jpg\",\"image/jpeg\",\"image/png\"]}},"
                + "{\"id\":7,\"required\":1,\"data\":{\"type\":2,\"len\":120}}]}}";

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .extracting(Native::getRequest)
                .containsExactly(expectedNativeRequest);
    }

    @Test
    public void makeHttpRequestsShouldAddEmptyNativeRequestIfRequestNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .xNative(Native.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yeahmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .extracting(Native::getRequest)
                .containsExactly("{\"native\":{}}");
    }

    @Test
    public void makeHttpRequestsShouldNotNativeRequestIfAlreadyExists() {
        // given
        String nativeRequest = "{\"native\":{\"ver\":\"1.2\",\"context\":1,\"plcmttype\":4,\"plcmtcnt\":1,"
                + "\"assets\":[{\"id\":2,\"required\":1,\"title\":{\"len\":90}},{\"id\":6,\"required\":1,"
                + "\"img\":{\"type\":3,\"wmin\":128,\"hmin\":128,\"mimes\":[\"image/jpg\",\"image/jpeg\","
                + "\"image/png\"]}},{\"id\":7,\"required\":1,\"data\":{\"type\":2,\"len\":120}}]}}";

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .xNative(Native.builder().request(nativeRequest).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yeahmobiBidder.makeHttpRequests(bidRequest);

        // then
        String expectedNativeRequest = "{\"native\":{\"ver\":\"1.2\",\"context\":1,\"plcmttype\":4,\"plcmtcnt\":1,"
                + "\"assets\":[{\"id\":2,\"required\":1,\"title\":{\"len\":90}},{\"id\":6,\"required\":1,\"img\":"
                + "{\"type\":3,\"wmin\":128,\"hmin\":128,\"mimes\":[\"image/jpg\",\"image/jpeg\",\"image/png\"]}},"
                + "{\"id\":7,\"required\":1,\"data\":{\"type\":2,\"len\":120}}]}}";

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .extracting(Native::getRequest)
                .containsExactly(expectedNativeRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yeahmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "EUR"));
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
                        .banner(Banner.builder().id("banner_id").build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYeahmobi.of("pubId", "zoneId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }
}
