package org.prebid.server.bidder.alliancegravity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.alliancegravity.ExtImpAllianceGravity;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
public class AllianceGravityBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private AllianceGravityBidder target;

    @BeforeEach
    public void before() {
        target = new AllianceGravityBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AllianceGravityBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenAllImpFail() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                        givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    public void makeHttpRequestsShouldSetStoredRequestInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(
                        ExtPrebid.of(
                                ExtImpPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of("test-srid"))
                                        .build(),
                                null)));
    }

    @Test
    public void makeHttpRequestsShouldSendAllImpsInOneRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImp(imp -> imp.id("imp1")), givenImp(imp -> imp.id("imp2"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactlyInAnyOrder("imp1", "imp2");
    }

    @Test
    public void makeHttpRequestsShouldSkipInvalidImpsAndKeepValid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("valid")),
                        givenImp(imp -> imp.id("invalid")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("valid");
    }

    @Test
    public void makeHttpRequestsShouldAddUserAgentAndForwardedForFromDeviceIp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("test-ua").ip("1.2.3.4").ipv6("::1").build())
                .imp(singletonList(givenImp(UnaryOperator.identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .allSatisfy(headers -> {
                    assertThat(headers.get("User-Agent")).isEqualTo("test-ua");
                    assertThat(headers.get("X-Forwarded-For")).isEqualTo("1.2.3.4");
                });
    }

    @Test
    public void makeHttpRequestsShouldFallBackToDeviceIpv6WhenIpIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ipv6("2001:db8::1").build())
                .imp(singletonList(givenImp(UnaryOperator.identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .extracting(headers -> headers.get("X-Forwarded-For"))
                .containsExactly("2001:db8::1");
    }

    @Test
    public void makeHttpRequestsShouldAddRefererFromSitePage() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().page("http://example.com").build())
                .imp(singletonList(givenImp(UnaryOperator.identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .extracting(headers -> headers.get("Referer"))
                .containsExactly("http://example.com");
    }

    @Test
    public void makeHttpRequestsShouldAddCookieWithBuyerUid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().buyeruid("buyer-uid-1").build())
                .imp(singletonList(givenImp(UnaryOperator.identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .extracting(headers -> headers.get("Cookie"))
                .containsExactly("uids=buyer-uid-1");
    }

    @Test
    public void makeHttpRequestsShouldNotAddCookieWhenBuyerUidIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().buyeruid("").build())
                .imp(singletonList(givenImp(UnaryOperator.identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .extracting(headers -> headers.get("Cookie"))
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCannotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response));
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenResponseHasNoSeatbid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenBidExtPrebidTypeIsBanner() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.id("bid1").impid("imp1").ext(bidExtWithPrebidType("banner"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(bidderBid -> {
                    assertThat(bidderBid.getType()).isEqualTo(BidType.banner);
                    assertThat(bidderBid.getBid().getId()).isEqualTo("bid1");
                    assertThat(bidderBid.getBidCurrency()).isEqualTo("USD");
                });
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenBidExtPrebidTypeIsVideo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.id("bid1").impid("imp1").ext(bidExtWithPrebidType("video"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(bidderBid -> assertThat(bidderBid.getType()).isEqualTo(BidType.video));
    }

    @Test
    public void makeBidsShouldReturnAudioBidWhenBidExtPrebidTypeIsAudio() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.id("bid1").impid("imp1").ext(bidExtWithPrebidType("audio"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(bidderBid -> assertThat(bidderBid.getType()).isEqualTo(BidType.audio));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenBidExtPrebidTypeIsNative() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.id("bid1").impid("imp1").ext(bidExtWithPrebidType("native"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(bidderBid -> assertThat(bidderBid.getType()).isEqualTo(BidType.xNative));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtIsMissing() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.id("bid1").impid("imp1")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).contains("Failed to parse impression \"imp1\" mediatype");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtPrebidTypeIsUnknown() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.id("bid1").impid("imp1").ext(bidExtWithPrebidType("unknown"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer)))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp_id")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAllianceGravity.of("test-srid")))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer
                                .apply(Bid.builder().price(BigDecimal.ONE))
                                .build()))
                        .build()))
                .build());
    }

    private ObjectNode bidExtWithPrebidType(String type) {
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", mapper.createObjectNode().put("type", type));
        return ext;
    }
}
