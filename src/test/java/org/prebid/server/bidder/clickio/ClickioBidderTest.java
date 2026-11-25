package org.prebid.server.bidder.clickio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
    public void makeHttpRequestsShouldSendAllImpsInSingleRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1")),
                givenImp(imp -> imp.id("imp-2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp-1", "imp-2");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1")),
                givenImp(imp -> imp.id("imp-2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder("imp-1", "imp-2");
    }

    @Test
    public void makeHttpRequestsShouldUseConfiguredEndpointUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
    }

    @Test
    public void makeHttpRequestsShouldCopyBidderParamsToImpExtParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(givenBidderParams("said-1", "psid-1", "tpl-1")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("params"))
                .containsExactly(givenBidderParams("said-1", "psid-1", "tpl-1"));
    }

    @Test
    public void makeHttpRequestsShouldKeepDistinctParamsForEachImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1").ext(givenImpExt(givenBidderParams("said-1", null, null)))),
                givenImp(imp -> imp.id("imp-2").ext(givenImpExt(givenBidderParams("said-2", "psid-2", null)))));

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
    public void makeHttpRequestsShouldPreserveOtherImpExtFields() {
        // given
        final ObjectNode impExt = givenImpExt(givenBidderParams("said-1", null, null));
        impExt.put("tid", "transaction-id");
        impExt.set("data", mapper.createObjectNode().put("pbadslot", "slot"));
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode()
                .put("tid", "transaction-id")
                .<ObjectNode>set("data", mapper.createObjectNode().put("pbadslot", "slot"))
                .<ObjectNode>set("bidder", givenBidderParams("said-1", null, null))
                .set("params", givenBidderParams("said-1", null, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldOverrideExistingImpExtParams() {
        // given
        final ObjectNode impExt = givenImpExt(givenBidderParams("said-1", null, null));
        impExt.set("params", TextNode.valueOf("stale"));
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("params"))
                .containsExactly(givenBidderParams("said-1", null, null));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingImpExt() {
        // given
        final ObjectNode impExt = givenImpExt(givenBidderParams("said-1", null, null));
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(impExt)));

        // when
        target.makeHttpRequests(bidRequest);

        // then
        assertThat(impExt).isEqualTo(givenImpExt(givenBidderParams("said-1", null, null)));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(givenImp(identity())));

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
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(givenImp(identity())));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(givenImp(identity())));

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
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(givenImp(identity())));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, banner, "EUR"));
    }

    @Test
    public void makeBidsShouldMapBidsToImpsById() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1")),
                givenImp(imp -> imp.id("imp-2")));
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
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(givenImp(identity())));

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(validBid, banner, "USD"));
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Failed to find impression \"imp-unknown\""));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpIsNotBanner() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.banner(null).video(Video.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse("USD", Bid.builder().impid("imp-1").build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Failed to resolve media type for impression \"imp-1\""));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder().imp(asList(imps)).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp-1")
                        .banner(Banner.builder().w(300).h(250).build())
                        .ext(givenImpExt(givenBidderParams("said", null, null))))
                .build();
    }

    private static ObjectNode givenImpExt(ObjectNode bidderParams) {
        return mapper.createObjectNode().set("bidder", bidderParams);
    }

    private static ObjectNode givenBidderParams(String said, String psid, String template) {
        final ObjectNode bidderParams = mapper.createObjectNode().put("said", said);
        if (psid != null) {
            bidderParams.put("psid", psid);
        }
        if (template != null) {
            bidderParams.put("template", template);
        }
        return bidderParams;
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
