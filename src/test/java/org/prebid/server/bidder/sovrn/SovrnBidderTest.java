package org.prebid.server.bidder.sovrn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.json.Json;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.sovrn.ExtImpSovrn;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SovrnBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://sovrn.com/openrtb2d";

    private SovrnBidder sovrnBidder;

    @Before
    public void setUp() {
        sovrnBidder = new SovrnBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedAudioMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder().id("impId").audio(Audio.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "Sovrn doesn't support audio, video, or native Imps. Ignoring Imp ID=impId"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedVideoMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder().id("impId").video(Video.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "Sovrn doesn't support audio, video, or native Imps. Ignoring Imp ID=impId"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedNativeMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder().id("23").xNative(Native.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "Sovrn doesn't support audio, video, or native Imps. Ignoring Imp ID=23"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorSayingAboutMissingSovrnParams() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Sovrn parameters section is missing"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestContainingExpectedFieldsInBidRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(
                        Imp.builder().id("impId")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(200).h(300).build()))
                                        .w(200)
                                        .h(300)
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSovrn.of("tagid", null, null))))
                                .build()))
                .user(User.builder().ext(Json.mapper.valueToTree(ExtUser.of(null, "consent", null, null))).build())
                .regs(Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(1))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .containsExactly(BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSovrn.of("tagid", null, null))))
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(200).h(300).build()))
                                        .w(200)
                                        .h(300)
                                        .build())
                                .tagid("tagid")
                                .bidfloor(null)
                                .build()))
                        .user(User.builder().ext(Json.mapper.valueToTree(ExtUser.of(null, "consent", null, null))).build())
                        .regs(Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(1))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestsContainingExpectedHeaders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .ua("userAgent")
                        .language("fr")
                        .dnt(8)
                        .ip("123.123.123.12")
                        .build())
                .user(User.builder()
                        .buyeruid("701")
                        .build())
                .imp(Collections.singletonList(Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("User-Agent", "userAgent"),
                        tuple("X-Forwarded-For", "123.123.123.12"),
                        tuple("DNT", "8"),
                        tuple("Accept-Language", "fr"),
                        tuple("Cookie", "ljt_reader=701"));
    }

    @Test
    public void makeHttpRequestShouldReturnImpWithTagidIfBothTagidAndLegacyTagIdDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(
                        Imp.builder()
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpSovrn.of("tagid", "legacyTagId", null))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("tagid");
    }

    @Test
    public void makeHttpRequestShouldReturnImpWithTagidFromLegacyIfTagIdIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(
                        Imp.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSovrn.of(null, "legacyTagId", null))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("legacyTagId");
    }

    @Test
    public void makeHttpRequestsShouldReturnEmptyResultWhenMissingBidRequestImps() {
        assertThat(sovrnBidder.makeHttpRequests(BidRequest.builder().build()))
                .isEqualTo(Result.of(emptyList(), emptyList()));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithoutMemberIdIfItMissedRequestBodyImps() {

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .build());

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .element(0).returns("http://sovrn.com/openrtb2d", HttpRequest::getUri);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badServerResponse(
                "Failed to decode: Unrecognized token 'invalid': was expecting ('true', 'false' or 'null')\n" +
                        " at [Source: (String)\"invalid\"; line: 1, column: 15]"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnResultWithExpectedFields() throws JsonProcessingException {
        // given
        final HttpCall httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .w(200)
                                .h(150)
                                .price(BigDecimal.ONE)
                                .impid("impid")
                                .dealid("dealid")
                                .adm("<div>This is an Ad</div>")
                                .build()))
                        .build()))
                .build()));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(
                        Bid.builder()
                                .impid("impid")
                                .price(BigDecimal.ONE)
                                .dealid("dealid")
                                .w(200)
                                .h(150)
                                .adm("<div>This is an Ad</div>")
                                .build(),
                        BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnDecodedUrlInAdmField() throws JsonProcessingException {
        // given
        final HttpCall httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .w(200)
                                .h(150)
                                .price(BigDecimal.ONE)
                                .impid("impid")
                                .dealid("dealid")
                                .adm("encoded+url+test")
                                .build()))
                        .build()))
                .build()));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).hasSize(1).element(0)
                .returns("encoded url test", bidderBid -> bidderBid.getBid().getAdm());
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
