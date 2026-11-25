package org.prebid.server.bidder.clickio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ClickioBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/bids";

    private final ClickioBidder target = new ClickioBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClickioBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnSingleRequestWithExpectedUriHeadersAndImpIds() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(imp -> imp.id("imp-1")), givenImp(imp -> imp.id("imp-2"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(ENDPOINT_URL))
                .satisfies(request -> assertThat(request.getImpIds()).containsExactlyInAnyOrder("imp-1", "imp-2"))
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
    }

    @Test
    public void makeHttpRequestsShouldCopyBidderParamsToImpExtParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("said-1", "psid-1", "tpl-1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedParams = givenBidderParams("said-1", "psid-1", "tpl-1");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode()
                        .<ObjectNode>set("bidder", expectedParams)
                        .set("params", expectedParams));
    }

    @Test
    public void makeHttpRequestsShouldKeepDistinctParamsForEachImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(imp -> imp.id("imp-1").ext(givenImpExt("said-1", null, null))),
                        givenImp(imp -> imp.id("imp-2").ext(givenImpExt("said-2", "psid-2", null)))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("params"))
                .containsExactly(
                        givenBidderParams("said-1", null, null),
                        givenBidderParams("said-2", "psid-2", null));
    }

    @Test
    public void makeHttpRequestsShouldPreserveOtherImpExtFieldsAndRequestFields() {
        // given
        final ObjectNode impExt = givenImpExt("said-1", null, null);
        impExt.put("tid", "transaction-id");
        impExt.set("prebid", mapper.createObjectNode().put("is_rewarded_inventory", 1));
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(impExt)).toBuilder()
                .id("request-id")
                .site(Site.builder().page("https://example.com/page").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final BidRequest outgoingRequest = result.getValue().getFirst().getPayload();
        assertThat(outgoingRequest.getId()).isEqualTo("request-id");
        assertThat(outgoingRequest.getSite()).isEqualTo(bidRequest.getSite());
        assertThat(outgoingRequest.getImp()).extracting(Imp::getExt)
                .containsExactly(impExt.deepCopy().set("params", givenBidderParams("said-1", null, null)));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingImpExt() {
        // given
        final ObjectNode impExt = givenImpExt("said-1", null, null);
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(impExt));

        // when
        target.makeHttpRequests(bidRequest);

        // then
        assertThat(impExt.has("params")).isFalse();
    }

    @Test
    public void makeHttpRequestsShouldOverrideExistingImpExtParams() {
        // given
        final ObjectNode impExt = givenImpExt("said-1", null, null);
        impExt.set("params", TextNode.valueOf("stale"));
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("params"))
                .containsExactly(givenBidderParams("said-1", null, null));
    }

    @Test
    public void makeHttpRequestsShouldLeaveImpUnchangedWhenBidderParamsAbsent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode().put("tid", "transaction-id");
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(imp -> imp.id("imp-1").ext(impExt)),
                        givenImp(imp -> imp.id("imp-2").ext(null)),
                        givenImp(imp -> imp.id("imp-3").ext(mapper.createObjectNode().putNull("bidder")))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactlyElementsOf(bidRequest.getImp());
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid'");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWithResponseCurrency() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bid-1").impid("imp-1").price(BigDecimal.ONE).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("EUR", bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, banner, "EUR"));
    }

    @Test
    public void makeBidsShouldMapBidsToImpsById() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(imp -> imp.id("imp-1")), givenImp(imp -> imp.id("imp-2"))))
                .build();
        final Bid firstBid = Bid.builder().id("bid-1").impid("imp-2").build();
        final Bid secondBid = Bid.builder().id("bid-2").impid("imp-1").build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", firstBid, secondBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(firstBid, banner, "USD"),
                BidderBid.of(secondBid, banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpNotFoundAndKeepOtherBids() throws JsonProcessingException {
        // given
        final Bid validBid = Bid.builder().id("bid-1").impid("imp-1").build();
        final Bid unknownImpBid = Bid.builder().id("bid-2").impid("imp-unknown").build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", validBid, unknownImpBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(validBid, banner, "USD"));
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Failed to find impression \"imp-unknown\""));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpIsNotBanner() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).video(Video.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse("USD", Bid.builder().impid("imp-1").build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Failed to resolve media type for impression \"imp-1\""));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder().imp(singletonList(givenImp(impCustomizer))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp-1")
                        .banner(Banner.builder().w(300).h(250).build())
                        .ext(givenImpExt("said", null, null)))
                .build();
    }

    private static ObjectNode givenImpExt(String said, String psid, String template) {
        return mapper.createObjectNode().set("bidder", givenBidderParams(said, psid, template));
    }

    private static ObjectNode givenBidderParams(String said, String psid, String template) {
        final ObjectNode params = mapper.createObjectNode().put("said", said);
        if (psid != null) {
            params.put("psid", psid);
        }
        if (template != null) {
            params.put("template", template);
        }
        return params;
    }

    private static String givenBidResponse(String currency, Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur(currency)
                .seatbid(singletonList(SeatBid.builder().bid(asList(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }
}
