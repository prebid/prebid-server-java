package org.prebid.server.bidder.adf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.adf.ExtImpAdf;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AdfBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private AdfBidder bidder;

    @Before
    public void setup() {
        bidder = new AdfBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdfBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestShouldCollectErrorIfImpExtInvalid() {
        // given
        final BidRequest request = givenBidRequest(
                givenImp(ExtImpAdf.builder().build()),
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(request);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
                });
    }

    @Test
    public void makeHttpRequestShouldSetImpTagId() {
        // given
        final BidRequest request = givenBidRequest(givenImp(ExtImpAdf.builder().mid("mid").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(request);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("mid");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldUseFirstNonEmptyPriceType() {
        // given
        final BidRequest request = givenBidRequest(
                givenImp(ExtImpAdf.builder().build()),
                givenImp(ExtImpAdf.builder().priceType("priceType1").build()),
                givenImp(ExtImpAdf.builder().priceType("priceType2").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(request);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(extRequest -> extRequest.getProperty("pt"))
                .map(JsonNode::asText)
                .containsExactly("priceType1");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldModifyExtIfAlreadyPresent() {
        // given
        final BidRequest request = givenBidRequest(bidRequest -> bidRequest
                .imp(singletonList(givenImp(ExtImpAdf.builder().priceType("priceType").build())))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().auctiontimestamp(0L).build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(request);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(jacksonMapper.fillExtension(
                        ExtRequest.of(ExtRequestPrebid.builder().auctiontimestamp(0L).build()),
                        mapper.createObjectNode().put("pt", "priceType")));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseInvalid() {
        // given
        final HttpCall<BidRequest> response = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfSeatBidNullOrEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> response = givenHttpCall(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldExtractBidTypeFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final HttpCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getType)
                .containsExactly(BidType.banner);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseBidResponseCur() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final HttpCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getBidCurrency)
                .containsExactly("USD");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldCollectErrorIfBidTypeInvalid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final HttpCall<BidRequest> response = givenHttpCall(givenBidResponse(
                givenBid(identity()),
                givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(response, null);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Failed to parse impression null mediatype"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()).build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(bidRequest -> bidRequest.imp(List.of(imps)));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static Imp givenImp(ExtImpAdf extImpAdf) {
        return givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, extImpAdf))));
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }

    private static HttpCall<BidRequest> givenHttpCall(BidResponse response) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(response));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build();
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }
}
