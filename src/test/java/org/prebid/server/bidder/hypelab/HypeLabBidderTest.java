package org.prebid.server.bidder.hypelab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.hypelab.ExtImpHypeLab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class HypeLabBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://api.hypelab.com/v1/rtb_requests";
    private static final String PBS_VERSION = "pbs-java/1.0";

    @Mock(strictness = LENIENT)
    private PrebidVersionProvider prebidVersionProvider;

    private HypeLabBidder target;

    @BeforeEach
    public void setUp() {
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(PBS_VERSION);
        target = new HypeLabBidder(ENDPOINT_URL, jacksonMapper, prebidVersionProvider);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HypeLabBidder("invalid_url", jacksonMapper, prebidVersionProvider));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedRequest() {
        // given
        final ExtRequest requestExt = ExtRequest.empty();
        requestExt.addProperty("existing", mapper.valueToTree("value"));

        final BidRequest bidRequest = givenBidRequestWithExt(requestExt,
                imp -> imp.id("imp1").banner(Banner.builder().w(300).h(250).build())
                        .ext(givenImpExt("property", "placement")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HttpRequest<BidRequest> request = result.getValue().getFirst();
        final BidRequest payload = request.getPayload();
        final Imp imp = payload.getImp().getFirst();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUri()).isEqualTo(ENDPOINT_URL);
        assertThat(request.getImpIds()).containsExactly("imp1");
        assertThat(request.getHeaders().get(HttpUtil.ACCEPT_HEADER)).isEqualTo("application/json");
        assertThat(request.getHeaders().get(HttpUtil.CONTENT_TYPE_HEADER))
                .isEqualTo("application/json;charset=utf-8");
        assertThat(request.getHeaders().get(HttpUtil.X_OPENRTB_VERSION_HEADER)).isEqualTo("2.6");

        assertThat(imp.getTagid()).isEqualTo("placement");
        assertThat(imp.getDisplaymanager()).isEqualTo("HypeLab Prebid Server");
        assertThat(imp.getDisplaymanagerver()).isEqualTo(PBS_VERSION);
        assertThat(imp.getExt()).isEqualTo(mapper.valueToTree(
                ExtPrebid.of(null, ExtImpHypeLab.of("property", "placement"))));

        assertThat(payload.getExt().getProperty("existing")).isEqualTo(mapper.valueToTree("value"));
        assertThat(payload.getExt().getProperty("source")).isEqualTo(mapper.valueToTree("prebid-server"));
        assertThat(payload.getExt().getProperty("provider_version")).isEqualTo(mapper.valueToTree(PBS_VERSION));
        assertThat(jacksonMapper.decodeValue(request.getBody(), BidRequest.class)).isEqualTo(payload);
    }

    @Test
    public void makeHttpRequestsShouldUseUnknownVersionWhenPbsVersionIsBlank() {
        // given
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(" ");

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(givenBidRequest());

        // then
        final BidRequest payload = result.getValue().getFirst().getPayload();

        assertThat(result.getErrors()).isEmpty();
        assertThat(payload.getImp().getFirst().getDisplaymanagerver()).isEqualTo("unknown");
        assertThat(payload.getExt().getProperty("provider_version")).isEqualTo(mapper.valueToTree("unknown"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpWhenExtIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null, imp -> imp.ext(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("imp impId: unable to unmarshal ext"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpWhenBidderExtCanNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null,
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("imp impId: unable to unmarshal ext.bidder"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpWhenRequiredParamsAreBlank() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null,
                imp -> imp.ext(givenImpExt(" ", "placement")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput(
                        "imp impId: property_slug and placement_slug are required"));
    }

    @Test
    public void makeHttpRequestsShouldSendValidImpsAndReturnErrorsForInvalidImps() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null,
                imp -> imp.id("valid"),
                imp -> imp.id("invalid").ext(givenImpExt("property", "")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("valid");
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput(
                        "imp invalid: property_slug and placement_slug are required"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), "invalid_json");

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
    public void makeBidsShouldReturnEmptyBidsWhenResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), "null");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseMtypeForBidType() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(bid -> bid.id("banner").mtype(1));
        final Bid videoBid = givenBid(bid -> bid.id("video").mtype(2));
        final Bid nativeBid = givenBid(bid -> bid.id("native").mtype(4));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(),
                givenBidResponse(bannerBid, videoBid, nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(bannerBid, BidType.banner, "hypelab", "USD"),
                BidderBid.of(videoBid, BidType.video, "hypelab", "USD"),
                BidderBid.of(nativeBid, BidType.xNative, "hypelab", "USD"));
    }

    @Test
    public void makeBidsShouldUseHypeLabBidExtForBidType() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(bid -> bid.ext(givenBidExt("video")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(),
                givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, BidType.video, "hypelab", "USD"));
    }

    @Test
    public void makeBidsShouldUseVastMarkupForBidType() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(bid -> bid.adm("  <VAST version=\"4.0\"></VAST>"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(),
                givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, BidType.video, "hypelab", "USD"));
    }

    @Test
    public void makeBidsShouldInferBidTypeFromImp() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(UnaryOperator.identity());
        final BidRequest bidRequest = givenBidRequestWithExt(null,
                imp -> imp.id("impId").banner(null).xNative(Native.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, BidType.xNative, "hypelab", "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidTypeCanNotBeInferred() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(bidBuilder -> bidBuilder.impid("unknown"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse(
                "unable to determine media type for bid bidId on imp unknown"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequestWithExt(null, impCustomizers);
    }

    private static BidRequest givenBidRequestWithExt(ExtRequest ext, UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = impCustomizers.length == 0
                ? singletonList(givenImp(UnaryOperator.identity()))
                : Arrays.stream(impCustomizers)
                .map(HypeLabBidderTest::givenImp)
                .toList();

        return BidRequest.builder()
                .id("requestId")
                .imp(imps)
                .ext(ext)
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(givenImpExt("property", "placement")))
                .build();
    }

    private static ObjectNode givenImpExt(String propertySlug, String placementSlug) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpHypeLab.of(propertySlug, placementSlug)));
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()
                        .id("bidId")
                        .impid("impId")
                        .price(BigDecimal.ONE))
                .build();
    }

    private static ObjectNode givenBidExt(String creativeType) {
        return mapper.createObjectNode()
                .set("hypelab", mapper.createObjectNode().put("creative_type", creativeType));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().seat("hypelab").bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String responseBody) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, responseBody),
                null);
    }
}
