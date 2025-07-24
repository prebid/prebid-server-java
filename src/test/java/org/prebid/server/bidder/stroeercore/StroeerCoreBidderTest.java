package org.prebid.server.bidder.stroeercore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBid;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBidResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.DsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.stroeercore.ExtImpStroeerCore;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class StroeerCoreBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final StroeerCoreBidder target = new StroeerCoreBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void makeHttpRequestsShouldReturnExpectedMethod() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("192848"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod)
                .containsExactly(HttpMethod.POST);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("981287"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().getFirst().getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedURL() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("726292"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnImpsWithExpectedTagIds() {
        // given
        final BidRequest bidRequest = createBidRequest(createBannerImp("827194"), createBannerImp("abc"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("827194", "abc");
    }

    @Test
    public void makeHttpRequestsShouldReturnDSA() {
        // given
        final List<DsaTransparency> transparencies = Arrays.asList(
                DsaTransparency.of("platform-example.com", List.of(1, 2)),
                DsaTransparency.of("ssp-example.com", List.of(1))
        );

        final ExtRegsDsa dsa = ExtRegsDsa.of(3, 1, 2, transparencies);

        final BidRequest bidRequest = createBidRequest(createBannerImp("1")).toBuilder()
                .regs(Regs.builder().ext(ExtRegs.of(null, null, null, dsa)).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getRegs().getExt().getDsa())
                .allSatisfy(actualDsa -> assertThat(actualDsa).isSameAs(dsa));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest invalidBidRequest = createBidRequest(createImpWithNonParsableImpExt("3"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(invalidBidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage())
                    .startsWith("Cannot deserialize")
                    .endsWith(". Ignore imp id = 3.");
        });
    }

    @Test
    public void makeBidsShouldReturnExpectedBannerBid() throws JsonProcessingException {
        // given
        final ObjectNode dsaResponse = createDsaResponse();

        final StroeerCoreBid bannerBid = StroeerCoreBid.builder()
                .id("1")
                .bidId("banner-imp-id")
                .adMarkup("<div></div>")
                .cpm(BigDecimal.valueOf(0.3))
                .creativeId("foo")
                .width(300)
                .height(600)
                .mtype("banner")
                .dsa(dsaResponse.deepCopy())
                .adomain(List.of("domain1.com", "domain2.com"))
                .build();

        final StroeerCoreBidResponse response = StroeerCoreBidResponse.of(List.of(bannerBid));
        final BidderCall<BidRequest> httpCall = createHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBannerBid = Bid.builder()
                .id("1")
                .impid("banner-imp-id")
                .adm("<div></div>")
                .price(BigDecimal.valueOf(0.3))
                .crid("foo")
                .w(300)
                .h(600)
                .adomain(List.of("domain1.com", "domain2.com"))
                .mtype(1)
                .ext(mapper.createObjectNode().set("dsa", dsaResponse))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedBannerBid, BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnExpectedBidderBids() throws JsonProcessingException {
        // given
        final StroeerCoreBid videoBid = StroeerCoreBid.builder()
                .id("27")
                .bidId("video-imp-id")
                .adMarkup("<vast><span></span></vast>")
                .cpm(BigDecimal.valueOf(1.58))
                .creativeId("vid")
                .mtype("video")
                .dsa(null)
                .build();

        final StroeerCoreBidResponse response = StroeerCoreBidResponse.of(List.of(videoBid));
        final BidderCall<BidRequest> httpCall = createHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedVideoBid = Bid.builder()
                .id("27")
                .impid("video-imp-id")
                .adm("<vast><span></span></vast>")
                .price(BigDecimal.valueOf(1.58))
                .crid("vid")
                .mtype(2)
                .ext(null)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedVideoBid, BidType.video, "EUR"));
    }

    @Test
    public void makeBidsShouldFailWhenBidTypeIsNotSupported() throws JsonProcessingException {
        // given
        final StroeerCoreBid audioBid = StroeerCoreBid.builder()
                .id("27")
                .bidId("audio-imp-id")
                .mtype("audio")
                .build();

        final StroeerCoreBidResponse response = StroeerCoreBidResponse.of(List.of(audioBid));
        final BidderCall<BidRequest> httpCall = createHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse(
                "Bid media type error: unable to determine media type for bid with id \"audio-imp-id\""));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = createHttpCallWithNonParsableResponse();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfZeroBids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = createHttpCall(StroeerCoreBidResponse.of(emptyList()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private BidRequest createBidRequest(Imp... imps) {
        return BidRequest.builder().imp(List.of(imps)).build();
    }

    private Imp createImpWithNonParsableImpExt(String impId) {
        final ObjectNode impExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        return createBannerImp("1", imp -> imp.id(impId).ext(impExt));
    }

    private Imp createBannerImp(String slotId, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final UnaryOperator<ImpBuilder> addBanner = imp -> imp.banner(Banner.builder().build());
        return createImp(slotId, addBanner.andThen(impCustomizer));
    }

    private Imp createBannerImp(String slotId) {
        return createBannerImp(slotId, identity());
    }

    private Imp createImp(String slotId, Function<ImpBuilder, ImpBuilder> impCustomizer) {
        final ObjectNode impExtNode = mapper.valueToTree(ExtPrebid.of(null, ExtImpStroeerCore.of(slotId)));

        final UnaryOperator<Imp.ImpBuilder> addImpExt = imp -> imp.ext(impExtNode);
        final ImpBuilder impBuilder = Imp.builder();

        return addImpExt.andThen(impCustomizer).apply(impBuilder).build();
    }

    private BidderCall<BidRequest> createHttpCall(StroeerCoreBidResponse response) throws JsonProcessingException {
        return createHttpCall(HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(response)));
    }

    private BidderCall<BidRequest> createHttpCall(HttpRequest<BidRequest> request, HttpResponse response) {
        return BidderCall.succeededHttp(request, response, null);
    }

    private BidderCall<BidRequest> createHttpCallWithNonParsableResponse() {
        return createHttpCall(HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, "[]"));
    }

    private ObjectNode createDsaResponse() {
        final ObjectNode dsaTransparency = mapper.createObjectNode()
                .put("domain", "example.com")
                .set("dsaparams", mapper.createArrayNode().add(1).add(2));
        return mapper.createObjectNode()
                .put("behalf", "advertiser-a")
                .put("paid", "advertiser-b")
                .set("transparency", mapper.createArrayNode().add(dsaTransparency));
    }
}
