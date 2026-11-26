package org.prebid.server.bidder.optout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.optout.model.OptoutBid;
import org.prebid.server.bidder.optout.model.OptoutRequest;
import org.prebid.server.bidder.optout.model.OptoutResponse;
import org.prebid.server.bidder.viant.ViantBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.optout.ExtImpOptout;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class OptoutBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private OptoutBidder target;

    @BeforeEach
    public void setUp() {
        target = new OptoutBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ViantBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorWhenAllImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
    }

    public void makeHttpRequestsShouldReturnOptoutRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(
                        givenImpExt("publisher", "adslot"))));

        // when

        // then
    }

    @Test
    public void makeHttpRequestsShouldAddErrorsOnInvalidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                givenImp(imp -> imp.ext(givenImpExt("", null))),
                givenImp(identity()),
                givenImp(imp -> imp.ext(givenImpExt(null, null))),
                givenImp(imp -> imp.ext(givenImpExt("", null))),
                givenImp(imp -> imp.ext(givenImpExt("publisherId", null))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getSlots)
                .hasSize(2);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(4)
                .satisfies(errors ->
                        assertThat(errors.getFirst()).startsWith("invalid impression extension:"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              Imp... imps) {
        return requestCustomizer.apply(BidRequest.builder()
                        .cur(List.of("USD"))
                        .site(Site.builder().build())
                        .app(App.builder().build())
                        .user(User.builder().ext(ExtUser.builder()
                                .build()).build())
                        .imp(List.of(imps)))
                .build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .banner(Banner.builder().w(100).h(200).build())
                        .ext(givenImpExt(ExtImpOptout.of("placementId", "placementId", null, false))))
                .build();
    }

    private static ObjectNode givenImpExt(String publisher, String adSlot) {
        return givenImpExt(ExtImpOptout.of(publisher, adSlot, null, false));
    }

    private static ObjectNode givenImpExt(ExtImpOptout extImpOptout) {
        return mapper.valueToTree(ExtPrebid.of(null, extImpOptout));
    }

    private String givenBidResponse(ObjectNode... objectNode) throws JsonProcessingException {
        return mapper.writeValueAsString(
                OptoutResponse.of(singletonList(OptoutBid.builder().build())));
    }

    private ObjectNode givenBidNode(String id) {
        return givenBidNode(id, null, 1);
    }

    private ObjectNode givenBidNode(String id, String impId, Integer mType) {
        final ObjectNode bidNode = mapper.createObjectNode();
        return bidNode;
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
