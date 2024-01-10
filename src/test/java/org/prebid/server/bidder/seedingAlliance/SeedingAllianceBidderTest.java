package org.prebid.server.bidder.seedingAlliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.seedingalliance.ExtImpSeedingAlliance;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class SeedingAllianceBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com/{{AccountId}}";

    private final SeedingAllianceBidder target = new SeedingAllianceBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SeedingAllianceBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateUrlWithPbsAccountIdWhenSeatIdIsNotReceived() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(givenImpExt("accountId", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com/pbs");
    }

    @Test
    public void makeHttpRequestsShouldCreateUrlWithSeatIdWhenSeatIdIsReceived() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(
                givenImpExt("accountId", "customSeatId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com/customSeatId");
    }

    @Test
    public void makeHttpRequestsShouldAddAdUnitUdAsTagIdToImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("impId1").tagid("tagId1").ext(givenImpExt("adUnit1", null)),
                impBuilder -> impBuilder.id("impId2").ext(givenImpExt("adUnit2", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId, Imp::getTagid)
                .containsExactly(tuple("impId1", "adUnit1"), tuple("impId2", "adUnit2"));
    }

    @Test
    public void makeHttpRequestsShouldEnrichRequestWithEurCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(Collections.emptyList());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getCur)
                .containsExactly("EUR");
    }

    @Test
    public void makeHttpRequestsShouldNotAddAdditionalEurCurrencyEntry() {
        // given
        final BidRequest bidRequest = givenBidRequest(List.of("EUR"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getCur)
                .containsExactly("EUR");
    }

    @Test
    public void makeHttpRequestsShouldNotRemoveOtherCurrencies() {
        // given
        final BidRequest bidRequest = givenBidRequest(List.of("USD", "YEN"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getCur)
                .containsExactlyInAnyOrder("USD", "YEN", "EUR");
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseInvalid() {
        // given
        final BidderCall<BidRequest> response = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

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
        final BidderCall<BidRequest> response = givenHttpCall(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldExtractBidTypeFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getType)
                .containsExactly(BidType.banner);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReplaceAdmPriceMacro() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(
                givenBid(bid -> bid.adm("someBidAdmWithPrice `${AUCTION_PRICE}`")
                        .price(BigDecimal.TEN)
                        .ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("someBidAdmWithPrice `10`");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseBidResponseCur() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

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
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(
                givenBid(bidBuilder -> bidBuilder.id("someBidId")),
                givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("Failed to parse bid.ext.prebid.type for bid.id: 'someBidId'"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(SeedingAllianceBidderTest::givenImp).toList())
                .build();
    }

    private static BidRequest givenBidRequest(List<String> currencies) {
        return BidRequest.builder()
                .imp(List.of(SeedingAllianceBidderTest.givenImp(identity())))
                .cur(currencies)
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt("accountId", null))).build();
    }

    private static ObjectNode givenImpExt(String adUnitId, String seatId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpSeedingAlliance.of(adUnitId, seatId)));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse response) throws JsonProcessingException {
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
