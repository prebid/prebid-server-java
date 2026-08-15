package org.prebid.server.bidder.hypelab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
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
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod, HttpRequest::getUri, HttpRequest::getImpIds)
                .containsExactly(tuple(HttpMethod.POST, ENDPOINT_URL, Set.of("impId")));
    }

    @Test
    public void makeHttpRequestsShouldSendExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(HttpUtil.X_OPENRTB_VERSION_HEADER))
                        .isEqualTo("2.6"));
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagidFromPlacementSlug() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("placement");
    }

    @Test
    public void makeHttpRequestsShouldSetDisplayManagerAndVersion() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanager, Imp::getDisplaymanagerver)
                .containsExactly(tuple("HypeLab Prebid Server", PBS_VERSION));
    }

    @Test
    public void makeHttpRequestsShouldForwardBidderParamsInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpHypeLab.of("property", "placement"))));
    }

    @Test
    public void makeHttpRequestsShouldAddSourceAndProviderVersionToRequestExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext.getProperty("source")).isEqualTo(mapper.valueToTree("prebid-server"));
                    assertThat(ext.getProperty("provider_version"))
                            .isEqualTo(mapper.valueToTree("prebid-server@" + PBS_VERSION));
                });
    }

    @Test
    public void makeHttpRequestsShouldCopyRequestExtWithoutMutatingOriginal() {
        // given
        final ExtRequest requestExt = ExtRequest.empty();
        final ObjectNode existingProperty = mapper.createObjectNode().put("nested", "value");
        requestExt.addProperty("existing", existingProperty);

        final BidRequest bidRequest = givenBidRequestWithExt(requestExt, givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext).isNotSameAs(requestExt);
                    assertThat(ext.getProperty("existing")).isEqualTo(existingProperty).isNotSameAs(existingProperty);
                });
        assertThat(requestExt.getProperties()).containsOnlyKeys("existing");
    }

    @Test
    public void makeHttpRequestsShouldUseUnknownVersionWhenPbsVersionIsBlank() {
        // given
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(" ");

        // when
        final Result<List<HttpRequest<BidRequest>>> result =
                target.makeHttpRequests(givenBidRequest(givenImp(identity())));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("unknown");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getExt().getProperty("provider_version"))
                .containsExactly(mapper.valueToTree("prebid-server@unknown"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpWhenBidderExtCanNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

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
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(givenImpExt(" ", "placement"))));

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
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("valid")),
                givenImp(imp -> imp.id("invalid").ext(givenImpExt("property", ""))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
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
    public void makeBidsShouldReturnEmptyBidsWhenResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("null");

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid, videoBid, nativeBid));

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
    public void makeBidsShouldReturnErrorWhenMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final Bid audioBid = givenBid(bid -> bid.mtype(3));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse(
                "bid bidId uses unsupported mtype 3"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsMissing() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse(
                "bid bidId uses unsupported mtype null"));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequestWithExt(null, imps);
    }

    private static BidRequest givenBidRequestWithExt(ExtRequest ext, Imp... imps) {
        return BidRequest.builder()
                .id("requestId")
                .imp(List.of(imps))
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

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().seat("hypelab").bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String responseBody) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(givenBidRequest(givenImp(identity()))).build(),
                HttpResponse.of(200, null, responseBody),
                null);
    }
}
