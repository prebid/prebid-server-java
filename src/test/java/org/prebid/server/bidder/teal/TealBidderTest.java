package org.prebid.server.bidder.teal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
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
import org.prebid.server.proto.openrtb.ext.request.teal.ExtImpTeal;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TealBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private final TealBidder target = new TealBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TealBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Error parsing imp.ext for impression impId");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAccountParamFailsValidation() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("", "placement")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("account parameter failed validation");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPlacementParamFailsValidation() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("account", "")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("placement parameter failed validation");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldMapParametersCorrectly() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Site.builder().publisher(Publisher.builder().domain("mydomain.com").build()).build(),
                        imp -> imp.id("imp1").ext(givenImpExt("account", "placement1")),
                        imp -> imp.id("imp2").ext(givenImpExt("account", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        final BidRequest outgoingPayload = result.getValue().getFirst().getPayload();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(httpRequest -> {
                    assertThat(outgoingPayload.getSite().getPublisher().getId()).isEqualTo("account");
                    assertThat(outgoingPayload.getImp().getFirst().getExt()).isNotNull();
                    assertThat(outgoingPayload.getImp().getFirst().getExt().get("prebid")).isNotNull();
                    assertThat(outgoingPayload.getImp().getFirst().getExt().get("prebid").get("storedrequest"))
                            .isNotNull();
                    assertThat(outgoingPayload.getImp().getFirst().getExt().get("prebid").get("storedrequest")
                            .get("id")).isNotNull();
                    assertThat(outgoingPayload.getImp().getFirst().getExt()
                            .get("prebid").get("storedrequest").get("id").asText()).isEqualTo("placement1");
                    assertThat(outgoingPayload.getImp().getLast()).satisfiesAnyOf(
                            imp -> assertThat(imp.getExt()).isNull(),
                            imp -> assertThat(imp.getExt().get("prebid")).isNull(),
                            imp -> assertThat(imp.getExt().get("prebid").get("storedrequest")).isNull()
                    );
                });
    }

    @Test
    public void makeHttpRequestsShouldAddExtBidsPBSFlag() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("account", "placement1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        final BidRequest outgoingPayload = result.getValue().getFirst().getPayload();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(httpRequest -> {
                    assertThat(outgoingPayload.getExt()).isNotNull();
                    assertThat(outgoingPayload.getExt().getProperty("bids")).isNotNull();
                    assertThat(outgoingPayload.getExt().getProperty("bids").get("pbs")).isNotNull();
                    assertThat(outgoingPayload.getExt().getProperty("bids").get("pbs").toString()).isEqualTo("1");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, "invalid_json"),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final Bid responseBid = givenBid("imp1", 1, 1);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("imp1").banner(Banner.builder().id("id").build()).build())).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("teal-imp1")
                .impid("imp1")
                .mtype(1)
                .price(BigDecimal.ONE)
                .adm("adm")
                .w(300)
                .h(250)
                .adomain(singletonList("adomain.com"))
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final Bid responseBid = givenBid("imp1", 2, 1);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("imp1").video(Video.builder().pos(1).build()).build())).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("teal-imp1")
                .impid("imp1")
                .mtype(2)
                .price(BigDecimal.ONE)
                .adm("adm")
                .w(300)
                .h(250)
                .adomain(singletonList("adomain.com"))
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final Bid responseBid = givenBid("imp1", 4, 1);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("imp1").xNative(Native.builder().ver("1").build()).build())).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("teal-imp1")
                .impid("imp1")
                .mtype(4)
                .price(BigDecimal.ONE)
                .adm("adm")
                .w(300)
                .h(250)
                .adomain(singletonList("adomain.com"))
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.xNative, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(null, impCustomizers);
    }

    private static BidRequest givenBidRequest(Site site, UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Stream.of(impCustomizers)
                .map(customizer -> customizer.apply(Imp.builder().id("impId")).build())
                .toList();

        return BidRequest.builder().imp(imps).site(site).build();
    }

    private static ObjectNode givenImpExt(String account, String placement) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpTeal.of(account, placement)));
    }

    private static Bid givenBid(String id, Integer mType, int price) {
        return Bid.builder()
                .id("teal-" + id)
                .impid(id)
                .mtype(mType)
                .price(BigDecimal.valueOf(price))
                .adm("adm")
                .w(300)
                .h(250)
                .adomain(singletonList("adomain.com"))
                .ext(mapper.createObjectNode())
                .build();
    }

    private static BidResponse givenBidResponse(List<Bid> bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(bids)
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, List<Bid> bids)
            throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null,
                        mapper.writeValueAsString(givenBidResponse(bids))
                ), null);
    }
}
