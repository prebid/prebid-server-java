package org.prebid.server.bidder.contxtful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.contxtful.request.ContxtfulBidRequest;
import org.prebid.server.bidder.contxtful.request.ContxtfulBidRequestParams;
import org.prebid.server.bidder.contxtful.request.ContxtfulBidderRequest;
import org.prebid.server.bidder.contxtful.request.ContxtfulCompositeRequest;
import org.prebid.server.bidder.contxtful.request.ContxtfulConfig;
import org.prebid.server.bidder.contxtful.request.ContxtfulConfigDetails;
import org.prebid.server.bidder.contxtful.response.ContxtfulBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.contxtful.ExtImpContxtful;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ContxtfulBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/?customer={{AccountId}}";
    private static final String BIDDER_NAME = "contxtful";

    private final ContxtfulBidder target = new ContxtfulBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ContxtfulBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<ContxtfulCompositeRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Error parsing imp.ext for impression impId");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("customer1", "placement1")),
                imp -> imp.ext(givenImpExt("customer2", "placement2")));

        // when
        final Result<List<HttpRequest<ContxtfulCompositeRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.endpoint.com/?customer=customer1");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("customer1", "placement1")));

        // when
        final Result<List<HttpRequest<ContxtfulCompositeRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                });
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectPayload() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("customer1", "placement1")),
                imp -> imp.id("imp2").ext(givenImpExt("customer2", "placement2")));

        // when
        final Result<List<HttpRequest<ContxtfulCompositeRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ContxtfulCompositeRequest expectedPayload = ContxtfulCompositeRequest.builder()
                .ortb2Request(bidRequest)
                .bidRequests(List.of(
                        ContxtfulBidRequest.of(BIDDER_NAME, ContxtfulBidRequestParams.of("placement1"), "imp1"),
                        ContxtfulBidRequest.of(BIDDER_NAME, ContxtfulBidRequestParams.of("placement2"), "imp2")))
                .bidderRequest(ContxtfulBidderRequest.of(BIDDER_NAME))
                .config(ContxtfulConfig.of(ContxtfulConfigDetails.of("v1", "customer1")))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(httpRequest -> {
                    assertThat(httpRequest.getPayload()).isEqualTo(expectedPayload);
                    assertThat(httpRequest.getBody()).isEqualTo(jacksonMapper.encodeToBytes(expectedPayload));
                });
    }

    @Test
    public void makeHttpRequestsShouldSendCorrectSetOfImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1").ext(givenImpExt("customer1", "placement1")),
                imp -> imp.id("imp2").ext(givenImpExt("customer2", "placement2")));

        // when
        final Result<List<HttpRequest<ContxtfulCompositeRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ContxtfulCompositeRequest expectedPayload = ContxtfulCompositeRequest.builder()
                .ortb2Request(bidRequest)
                .bidRequests(List.of(
                        ContxtfulBidRequest.of(BIDDER_NAME, ContxtfulBidRequestParams.of("placement1"), "imp1"),
                        ContxtfulBidRequest.of(BIDDER_NAME, ContxtfulBidRequestParams.of("placement2"), "imp2")))
                .bidderRequest(ContxtfulBidderRequest.of(BIDDER_NAME))
                .config(ContxtfulConfig.of(ContxtfulConfigDetails.of("v1", "customer1")))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getImpIds)
                .isEqualTo(Set.of("imp1", "imp2"));
    }

    @Test
    public void makeHttpRequestsShouldModifyUserBuyerUidFromExt() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .prebid(ExtUserPrebid.of(Map.of(BIDDER_NAME, "user-id-from-ext")))
                .build();
        final User user = User.builder().ext(extUser).build();
        final BidRequest bidRequest = givenBidRequest(user, imp -> imp.ext(givenImpExt("customer", "placement")));

        // when
        final Result<List<HttpRequest<ContxtfulCompositeRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(ContxtfulCompositeRequest::getOrtb2Request)
                .extracting(BidRequest::getUser)
                .isEqualTo(user.toBuilder().buyeruid("user-id-from-ext").build());
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<ContxtfulCompositeRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<ContxtfulCompositeRequest>builder().build(),
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
        final ContxtfulBid responseBid = givenContxtfulBid("imp1", "banner", 1);
        final BidderCall<ContxtfulCompositeRequest> httpCall = givenHttpCall(singletonList(responseBid));
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("imp1").build())).build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("contxtful-imp1")
                .impid("imp1")
                .price(BigDecimal.ONE)
                .adm("adm")
                .w(300)
                .h(250)
                .crid("crid")
                .nurl("nurl")
                .burl("burl")
                .lurl("lurl")
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final ContxtfulBid responseBid = givenContxtfulBid("imp1", "video", 2);
        final BidderCall<ContxtfulCompositeRequest> httpCall = givenHttpCall(singletonList(responseBid));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").video(Video.builder().build()).build())).build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("contxtful-imp1")
                .impid("imp1")
                .price(BigDecimal.valueOf(2))
                .adm("adm")
                .w(300)
                .h(250)
                .crid("crid")
                .nurl("nurl")
                .burl("burl")
                .lurl("lurl")
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final ContxtfulBid responseBid = givenContxtfulBid("imp1", "native", 2);
        final BidderCall<ContxtfulCompositeRequest> httpCall = givenHttpCall(singletonList(responseBid));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").xNative(Native.builder().build()).build()))
                .build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("contxtful-imp1")
                .impid("imp1")
                .price(BigDecimal.valueOf(2))
                .adm("adm")
                .w(300)
                .h(250)
                .crid("crid")
                .nurl("nurl")
                .burl("burl")
                .lurl("lurl")
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorsForInvalidBids() throws JsonProcessingException {
        // given
        final ContxtfulBid validBid = givenContxtfulBid("imp1", "anyType", 1);
        final ContxtfulBid invalidBidNoAdm = ContxtfulBid.builder()
                .requestId("imp2")
                .mediaType("banner")
                .cpm(BigDecimal.ONE)
                .adm("")
                .build();
        final ContxtfulBid invalidBidNoMediaType = ContxtfulBid.builder()
                .requestId("imp3")
                .mediaType("")
                .cpm(BigDecimal.ONE)
                .adm("adm")
                .build();

        final BidderCall<ContxtfulCompositeRequest> httpCall = givenHttpCall(
                List.of(validBid, invalidBidNoAdm, invalidBidNoMediaType));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        Imp.builder().id("imp1").build(),
                        Imp.builder().id("imp2").build(),
                        Imp.builder().id("imp3").build()))
                .build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("contxtful-imp1")
                .impid("imp1")
                .price(BigDecimal.ONE)
                .adm("adm")
                .w(300)
                .h(250)
                .crid("crid")
                .nurl("nurl")
                .burl("burl")
                .lurl("lurl")
                .ext(mapper.createObjectNode())
                .build();

        assertThat(result.getErrors()).containsExactlyInAnyOrder(
                badServerResponse("bid imp2 has no ad markup"),
                badServerResponse("bid imp3 has no ad media type"));

        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(null, impCustomizers);
    }

    private static BidRequest givenBidRequest(User user, UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Stream.of(impCustomizers)
                .map(customizer -> customizer.apply(Imp.builder().id("impId")).build())
                .toList();

        return BidRequest.builder().imp(imps).user(user).build();
    }

    private static ObjectNode givenImpExt(String customerId, String placementId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpContxtful.of(placementId, customerId)));
    }

    private static ContxtfulBid givenContxtfulBid(String requestId, String mediaType, int price) {
        return ContxtfulBid.builder()
                .requestId(requestId)
                .mediaType(mediaType)
                .cpm(BigDecimal.valueOf(price))
                .adm("adm")
                .width(300)
                .height(250)
                .creativeId("crid")
                .nurl("nurl")
                .burl("burl")
                .lurl("lurl")
                .ext(mapper.createObjectNode())
                .build();
    }

    private static BidderCall<ContxtfulCompositeRequest> givenHttpCall(List<ContxtfulBid> bids)
            throws JsonProcessingException {

        return BidderCall.succeededHttp(
                HttpRequest.<ContxtfulCompositeRequest>builder().build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bids)),
                null);
    }
}
