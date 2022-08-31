package org.prebid.server.bidder.sovrn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.sovrn.ExtImpSovrn;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.Map.Entry;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class SovrnBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test/auction";

    private SovrnBidder sovrnBidder;

    @Before
    public void setUp() {
        sovrnBidder = new SovrnBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsVideoWithoutProtocols() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .video(Video.builder().protocols(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing required video parameter"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsVideoAndVideoHasMaxAndMinDurationZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .video(Video.builder()
                        .mimes(List.of())
                        .maxduration(0)
                        .minduration(0)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing required video parameter"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsVideoAndVideoHasMaxAndMinDurationIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .video(Video.builder()
                        .mimes(List.of())
                        .maxduration(null)
                        .minduration(null)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing required video parameter"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestContainingExpectedFieldsInBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getBody)
                .extracting(SovrnBidderTest::mappedToBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getTagid)
                .containsExactly(tuple(BigDecimal.TEN, "tagid"));
    }

    @Test
    public void makeHttpRequestsShouldSetBidFloorFromExtIfImpBidFloorIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder().format(singletonList(Format.builder().w(200).h(300).build()))
                        .w(200)
                        .h(300)
                        .build())
                .bidfloor(BigDecimal.ZERO));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getBody)
                .extracting(SovrnBidderTest::mappedToBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.TEN);
    }

    @Test
    public void makeHttpRequestsShouldSetBidFloorFromExtIfImpBidFloorIsMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(SovrnBidderTest::mappedToBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.TEN);
    }

    @Test
    public void makeHttpRequestsShouldNotSetBidFloorFromExtIfImpBidFloorIsValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder().format(singletonList(Format.builder().w(200).h(300).build()))
                        .w(200)
                        .h(300)
                        .build())
                .bidfloor(BigDecimal.ONE));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(SovrnBidderTest::mappedToBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestsContainingExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .device(Device.builder()
                        .ua("userAgent")
                        .language("fr")
                        .dnt(8)
                        .ip("123.123.123.12")
                        .build())
                .user(User.builder()
                        .buyeruid("701")
                        .build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Entry::getKey, Entry::getValue)
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
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(SovrnBidderTest::mappedToBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("tagid");
    }

    @Test
    public void makeHttpRequestShouldReturnImpWithTagidFromLegacyIfTagIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("impId")
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(200).h(300).build()))
                        .w(200)
                        .h(300)
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSovrn.of(null, "legacyTagId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(SovrnBidderTest::mappedToBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("legacyTagId");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("impId")
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(200).h(300).build()))
                        .w(200)
                        .h(300)
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value");
                });
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithoutMemberIdIfItMissedRequestBodyImps() {
        //given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .element(0).returns(ENDPOINT_URL, HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestNotContainsBothTagidAndLegacyTagid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSovrn.of(null, "", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sovrnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing required parameter 'tagid'"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                        && error.getMessage().startsWith("Failed to decode: Unrecognized token 'invalid'"));
    }

    @Test
    public void makeBidsShouldReturnResultWithExpectedFields() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder.id("impid")),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("impid")
                        .w(200)
                        .h(150)
                        .adm("<div>This is an Ad</div>")
                        .dealid("dealid")
                        .price(BigDecimal.ONE))));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, null);

        // then
        final BidderBid expectedBidderBid = BidderBid.of(
                Bid.builder()
                        .impid("impid")
                        .w(200)
                        .h(150)
                        .adm("<div>This is an Ad</div>")
                        .dealid("dealid")
                        .price(BigDecimal.ONE)
                        .build(),
                video, "EUR");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expectedBidderBid);
    }

    @Test
    public void makeBidsShouldReturnDecodedUrlInAdmField() throws JsonProcessingException {
        //given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                        .adm("encoded+url+test"))));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1).element(0)
                .returns("encoded url test", bidderBid -> bidderBid.getBid().getAdm());
    }

    @Test
    public void makeBidsShouldReturnVideoBidderBid() throws JsonProcessingException {
        //given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidImpIdAndBidRequestImpIdDoesntMatch() throws JsonProcessingException {
        //given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("unknownId"))));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(bidderError.getMessage()).startsWith("Imp ID unknownId in bid didn't match with "
                            + "any imp in the original request");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = sovrnBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest mappedToBidRequest(byte[] value) throws IOException {
        return mapper.readValue(value, BidRequest.class);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer)))
                        .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build())
                        .regs(Regs.builder().ext(ExtRegs.of(1, null)).build()))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .video(Video.builder()
                                .mimes(singletonList("mp4"))
                                .maxduration(1)
                                .minduration(1)
                                .protocols(singletonList(1))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSovrn.of("tagid",
                                "legacyTagId", BigDecimal.TEN)))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("EUR")
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
