package org.prebid.server.bidder.teal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("account");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .element(0)
                .extracting(ext -> ext.get("prebid"))
                .extracting(prebid -> prebid.get("storedrequest"))
                .extracting(storedRequest -> storedRequest.get("id"))
                .extracting(JsonNode::textValue)
                .isEqualTo("placement1");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .element(1)
                .isNull();
    }

    @Test
    public void makeHttpRequestsShouldAddExtBidsPBSFlag() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("account", "placement1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("bids"))
                .extracting(bids -> bids.get("pbs").toString())
                .containsExactly("1");
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
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").banner(Banner.builder().id("id").build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = givenBid("imp1", 1, 1);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final Bid responseBid = givenBid("imp1", 2, 1);
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").video(Video.builder().pos(1).build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = givenBid("imp1", 2, 1);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final Bid responseBid = givenBid("imp1", 4, 1);
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").xNative(Native.builder().ver("1").build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = givenBid("imp1", 4, 1);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.xNative, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(null, impCustomizer);
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
                .seatbid(singletonList(SeatBid.builder().bid(bids).build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, List<Bid> bids)
            throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(givenBidResponse(bids))),
                null);
    }
}
