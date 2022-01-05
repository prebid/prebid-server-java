package org.prebid.server.bidder.amx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.amx.ExtImpAmx;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class AmxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/prebid/bid";

    private AmxBidder amxBidder;

    @Before
    public void setUp() {
        amxBidder = new AmxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AmxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = amxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_input
                && error.getMessage().startsWith("Cannot deserialize value"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = amxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.com/prebid/bid?v=pbs1.1");
    }

    @Test
    public void makeHttpRequestsShouldUpdateRequestAndImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().build()).site(Site.builder().build()),
                impBuilder -> impBuilder.banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = amxBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedBidRequest = BidRequest.builder()
                .app(App.builder().publisher(Publisher.builder().id("testTagId").build()).build())
                .site(Site.builder().publisher(Publisher.builder().id("testTagId").build()).build())
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .tagid("testAdUnitId")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAmx.of("testTagId", "testAdUnitId"))))
                        .build())).build();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedBidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("Failed to decode: Unrecognized token"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsBidExtNotPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(), mapper.writeValueAsString(givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfStartDelayIsPresentInBidExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.set("himp", mapper.convertValue(Arrays.asList("someHintVAlue1", "someHintValue2"), JsonNode.class));
        bidExt.put("startdelay", "2");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .adm("<Impression>ExistingAdm</Impression>")
                                .nurl("nurlValue")
                                .ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final String expectedAdm = "<Impression>ExistingAdm</Impression>"
                + "<Impression><![CDATA[nurlValue]]></Impression>"
                + "<Impression><![CDATA[someHintVAlue1]]></Impression>"
                + "<Impression><![CDATA[someHintValue2]]></Impression>";
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .nurl("")
                        .ext(bidExt)
                        .adm(expectedAdm)
                        .build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfAdmNotContainSearchPoint() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("startdelay", "2");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .id("bidId")
                                .adm("no_point")
                                .ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("Adm should contain vast search point in bidder: bidId"));
    }

    @Test
    public void makeBidsShouldSkipBidAndAddErrorIfFailedToParseBidExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("startdelay", "2");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .id("bidId")
                                .adm("</Impression>")
                                .ext(mapper.createObjectNode().set("startdelay", mapper.createObjectNode())))));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .satisfies(error -> {
                    assertThat(error).extracting(BidderError::getType).containsExactly(BidderError.Type.bad_input);
                    assertThat(error).extracting(BidderError::getMessage)
                            .element(0).asString().startsWith("Cannot deserialize value");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfAdmIsNotPresent() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("startdelay", "2");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .id("bidId")
                                .ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = amxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Adm should not be blank in bidder: bidId"));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAmx.of("testTagId", "testAdUnitId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}

