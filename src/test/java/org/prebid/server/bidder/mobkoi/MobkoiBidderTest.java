package org.prebid.server.bidder.mobkoi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.mobkoi.ExtImpMobkoi;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class MobkoiBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final MobkoiBidder target = new MobkoiBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MobkoiBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasNoImpression() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("No impression provided"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasInvalidExtImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first()
                .satisfies(error -> {
                    assertThat(error.getMessage())
                            .startsWith("Invalid imp.ext for impression id imp_id. Error Information:");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasMissingPlacementId() {
        // given
        final ObjectNode mobkoiExt = impExt(null, null);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("placementId should not be null"));
    }

    @Test
    public void makeHttpRequestsShouldUseConstructorEndpointWhenNoCustomEndpointIsDefinedInMobkoiExtension() {
        // given
        final ObjectNode mobkoiExt = impExt("pid", null);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).extracting(HttpRequest::getUri).containsExactly("https://test.endpoint.com");
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldConstructorEndpointWhenTheCustomIsInvalidInMobkoiExtension() {
        // given
        final ObjectNode mobkoiExt = impExt("pid", "invalid-URI");
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("https://test.endpoint.com");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseCustomEndpointWhenDefinedInMobkoiExtension() {
        // given
        final ObjectNode mobkoiExt = impExt("pid", "https://custom.endpoint.com");
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("https://custom.endpoint.com");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateTheMobkoiExtensionCorrectly() throws Exception {
        // given
        final ObjectNode mobkoiExt = impExt("pid", null);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("bidder"))
                .containsExactly(mapper.valueToTree(ExtImpMobkoi.of("pid", null)));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid':");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidResponse bannerBidResponse = givenBidResponse(bidBuilder -> bidBuilder.mtype(1).impid("123"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bannerBidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(1).impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldThrowErrorWhenMediaTypeIsMissing() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("Missing mediaType for bid: null"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldThrowErrorWhenMediaTypeIsUnsupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.mtype(2).impid("imp_id"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("Unsupported bid mediaType: 2 for impression: imp_id"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return BidRequest.builder()
                .imp(singletonList(givenImp(impModifier)))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(impExt("placementIdValue", null))).build();
    }

    private static ObjectNode impExt(String placementId, String adServerBaseUrl) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpMobkoi.of(placementId, adServerBaseUrl)));
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}

