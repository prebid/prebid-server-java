package org.prebid.server.bidder.nexx360;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.nexx360.ExtImpNexx360;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class Nexx360BidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private static final String PBS_VERSION = "pbs-java/1.0";

    @Mock(strictness = LENIENT)
    private PrebidVersionProvider prebidVersionProvider;

    private Nexx360Bidder target;

    @BeforeEach
    public void setUp() {
        target = new Nexx360Bidder(ENDPOINT_URL, jacksonMapper, prebidVersionProvider);
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(PBS_VERSION);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Nexx360Bidder("invalid_url", jacksonMapper, prebidVersionProvider));
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
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1"),
                imp -> imp.id("imp2"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("tag", "placement")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(1)
                .first()
                .extracting(HttpRequest::getUri)
                .satisfies(url -> {
                    assertThat(url).startsWith("https://test.endpoint.com");
                    assertThat(url).contains("placement=placement");
                    assertThat(url).contains("tag_id=tag");
                });

    }

    @Test
    public void makeHttpRequestsShouldModifyImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("tag1", "p1")),
                imp -> imp.id("imp2").ext(givenImpExt("tag2", "p2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt1 = mapper.createObjectNode()
                .set("nexx360", mapper.valueToTree(ExtImpNexx360.of("tag1", "p1")));
        final ObjectNode expectedExt2 = mapper.createObjectNode()
                .set("nexx360", mapper.valueToTree(ExtImpNexx360.of("tag2", "p2")));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt1, expectedExt2);
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ExtRequest expectedExtRequest = ExtRequest.empty();
        expectedExtRequest.addProperty("nexx360", mapper.valueToTree(
                Nexx360ExtRequest.of(Nexx360ExtRequestCaller.of("pbs-java/1.0"))));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(req -> req.getPayload().getExt())
                .containsExactly(expectedExtRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

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
    public void makeBidsShouldReturnErrorWhenBidTypeIsUnknown() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder -> builder.ext(givenBidExt("unknown")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("unable to fetch mediaType in multi-format: impId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidTypeIfPresentInBidExt() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(builder -> builder.ext(givenBidExt("banner")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidTypeIfPresentInBidExt() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(builder -> builder.ext(givenBidExt("video")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidTypeIfPresentInBidExt() throws JsonProcessingException {
        // given
        final Bid audioBid = givenBid(builder -> builder.ext(givenBidExt("audio")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(audioBid, BidType.audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidTypeIfPresentInBidExt() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(builder -> builder.ext(givenBidExt("native")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(nativeBid, BidType.xNative, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(Nexx360BidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .ext(givenImpExt("tagId", "placementId")))
                .build();
    }

    private static ObjectNode givenImpExt(String tagId, String placement) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpNexx360.of(tagId, placement)));
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE)).build();
    }

    private static ObjectNode givenBidExt(String bidType) {
        return mapper.valueToTree(Nexx360ExtBid.of(bidType));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
