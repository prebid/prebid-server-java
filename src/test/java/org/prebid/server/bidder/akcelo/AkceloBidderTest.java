package org.prebid.server.bidder.akcelo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.akcelo.ExtImpAkcelo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;

public class AkceloBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private static final String BIDDER_NAME = "akcelo";

    private final AkceloBidder target = new AkceloBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AkceloBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
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
    public void makeHttpRequestsShouldNotReturnErrorWhenSiteIdBeParsedInTheSecondImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1"),
                imp -> imp.id("imp2").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyModifyRequest() {
        // given
        final ObjectNode extImp1 = givenImpExt(1, "1", 1);
        final ObjectNode extImp2 = givenImpExt(2, "2", 2);
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(extImp1),
                imp -> imp.id("imp2").ext(extImp2))
                .toBuilder()
                .site(Site.builder().id("siteId").publisher(Publisher.builder().id("pubId").build()).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExtImp1 = mapper.createObjectNode()
                .set(BIDDER_NAME, mapper.valueToTree(extImp1.get("bidder")));
        final ObjectNode expectedExtImp2 = mapper.createObjectNode()
                .set(BIDDER_NAME, mapper.valueToTree(extImp2.get("bidder")));

        final Publisher expectedPublisher = Publisher.builder()
                .id("pubId")
                .ext(ExtPublisher.of(ExtPublisherPrebid.of("1")))
                .build();

        final Site expectedSite = Site.builder().id("siteId").publisher(expectedPublisher).build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .satisfies(request -> {
                    assertThat(request.getSite()).isEqualTo(expectedSite);
                    assertThat(request.getImp())
                            .extracting(Imp::getExt)
                            .containsExactly(expectedExtImp1, expectedExtImp2);
                });
    }

    @Test
    public void makeHttpRequestsShouldCreateSitePublisherWhenSiteIsAbsent() {
        // given
        final ObjectNode extImp1 = givenImpExt(1, "1", 1);
        final ObjectNode extImp2 = givenImpExt(2, "2", 2);
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(extImp1),
                imp -> imp.id("imp2").ext(extImp2))
                .toBuilder()
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Site expectedSite = Site.builder().publisher(Publisher.builder()
                .ext(ExtPublisher.of(ExtPublisherPrebid.of("1")))
                .build()).build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .isEqualTo(expectedSite);
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
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsBanner() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(1, "video");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMtypeIsVideo() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(2, "banner");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenMtypeIsNative() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(4, "audio");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidsAndErrorsForMixedValidAndInvalidBids() throws JsonProcessingException {
        // given
        final Bid validBid = givenBid(1, null);
        final Bid invalidBid = givenBid(3, "video");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(validBid, invalidBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("unable to get media type 3"));
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(validBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsMissingAndExtTypeIsBanner() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(null, "banner");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeAndExtTypeAreMissing() throws JsonProcessingException {
        // given
        final Bid invalidBid = givenBid(null, null);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(invalidBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("missing media type for bid bidId"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers).map(AkceloBidderTest::givenImp).toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(
                        Imp.builder().id("impId").ext(givenImpExt(1, "3", null)))
                .build();
    }

    private static ObjectNode givenImpExt(Integer adUnitId, String siteId, Integer test) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpAkcelo.of(adUnitId, siteId, test)));
    }

    private static Bid givenBid(Integer mtype, String extType) {
        final ObjectNode ext = extType != null
                ? mapper.valueToTree(ExtPrebid.of(mapper.createObjectNode().put("type", extType), null))
                : null;

        return Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.ONE)
                .mtype(mtype)
                .ext(ext)
                .build();
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
