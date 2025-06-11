package org.prebid.server.bidder.mobkoi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
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
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.mobkoi.ExtImpMobkoi;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class MobkoiBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/bid";

    private final MobkoiBidder target = new MobkoiBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MobkoiBidder("invalid_url", jacksonMapper));
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
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasMissingTagIdAndPlacementId() {
        // given
        final ObjectNode mobkoiExt = impExt(null, null);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        badInput("invalid because it comes with neither "
                                + "request.imp[0].tagId nor req.imp[0].ext.Bidder.placementId"));
    }

    @Test
    public void makeHttpRequestsShouldAddPlacementIdOnlyInFirstImpressionTagId() {
        // given
        final ObjectNode mobkoiExt = impExt("pid", null);
        final Imp givenImp1 = givenImp(impBuilder -> impBuilder.ext(mobkoiExt));
        final Imp givenImp2 = givenImp(identity());
        final BidRequest bidRequest = BidRequest.builder().imp(asList(givenImp1, givenImp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getTagid())
                .containsExactly("pid", null);
    }

    @Test
    public void makeHttpRequestsShouldUseConstructorEndpointWhenNoCustomEndpointIsDefinedInMobkoiExtension() {
        // given
        final ObjectNode mobkoiExt = impExt("pid", null);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).extracting(HttpRequest::getUri).containsExactly("https://test.endpoint.com/bid");
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldConstructWithDefaultEndpointWhenTheCustomURLIsInvalidInMobkoiExtension() {
        // given
        final ObjectNode mobkoiExt = impExt("pid", "invalid URI");
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mobkoiExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("https://test.endpoint.com/bid");
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
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("https://custom.endpoint.com/bid");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldOverrideUserExtAndSetConsent() {
        // given
        final ObjectNode originExtUserData = mapper.createObjectNode().put("originAttr", "originValue");
        final ExtUser extUser = ExtUser.builder().data(originExtUserData).consent("consent-to-be-overridden").build();
        final User user = User.builder().consent("consent").ext(extUser).build();
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.user(user),
                impBuilder -> impBuilder);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt()).hasSize(1).element(0)
                .isEqualTo(ExtUser.builder()
                        .consent("consent")
                        .build());
        assertThat(result.getErrors()).isEmpty();
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
    public void makeBidsShouldReturnBannerBidWithMobkoiSeat() throws JsonProcessingException {
        // given
        final BidResponse bannerBidResponse = givenBidResponse(bidBuilder -> bidBuilder.mtype(1).impid("123"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bannerBidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(
                        BidderBid.of(
                                Bid.builder().mtype(1).impid("123").build(), banner, "mobkoi", "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return BidRequest.builder()
                .imp(singletonList(givenImp(impModifier)))
                .build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impModifier) {
        return bidRequestCustomizer.apply(BidRequest.builder())
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

