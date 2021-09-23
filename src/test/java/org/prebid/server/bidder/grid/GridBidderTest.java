package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.proto.openrtb.ext.request.grid.ExtImpGridBidder;
import org.prebid.server.bidder.grid.model.ExtImpGridData;
import org.prebid.server.bidder.grid.model.ExtImpGridDataAdServer;
import org.prebid.server.bidder.grid.model.Keywords;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class GridBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private GridBidder gridBidder;

    @Before
    public void setUp() {
        gridBidder = new GridBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GridBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpGridBidder.of(10, Keywords.empty()))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gridBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyModifyImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtImpGrid.builder()
                                .data(ExtImpGridData.of("pbadslot",
                                        ExtImpGridDataAdServer.of("name", "adslot")))
                                .bidder(ExtImpGridBidder.of(1, null))
                                .build()))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gridBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(
                        mapper.valueToTree(ExtImpGrid.builder()
                                .data(ExtImpGridData.of("pbadslot",
                                        ExtImpGridDataAdServer.of("name", "adslot")))
                                .bidder(ExtImpGridBidder.of(1, null))
                                .gpid("adslot")
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyModifyRequestExt() throws JsonProcessingException {
        // given
        final ObjectNode impUserKeywordsNode = (ObjectNode) mapper.readTree(
                "{\"firstPublisher\":[{\"name\":\"firstKeywordsUserSection\","
                        + "\"segments\":[{\"name\":\"segment1\",\"value\":\"value1\"}]}]}");
        final ObjectNode impSiteKeywordsNode = (ObjectNode) mapper.readTree(
                "{\"firstPublisher\":[{\"name\":\"firstKeywordsSiteSection\","
                        + "\"segments\":[{\"name\":\"segment1\",\"value\":\"value1\"}]}]}");
        final ExtImpGrid impExt = ExtImpGrid.builder()
                .data(ExtImpGridData.of("pbadslot",
                        ExtImpGridDataAdServer.of("name", "adslot")))
                .bidder(ExtImpGridBidder.of(1, Keywords.of(impUserKeywordsNode, impSiteKeywordsNode)))
                .build();

        final ExtRequest extRequest = ExtRequest.of(null);
        extRequest.addProperty("keywords", mapper.readTree(
                "{\"user\": {\"secondPublisher\":[{\"name\":\"secondKeywordsUserSection\","
                        + "\"segments\":[{\"name\":\"segment2\",\"value\":\"value2\"}]}]}, "
                        + "\"site\": {\"secondPublisher\":[{\"name\":\"secondKeywordsSiteSection\","
                        + "\"segments\":[{\"name\":\"segment2\",\"value\":\"value2\"}]}]}}"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.valueToTree(impExt)).build()))
                .id("request_id")
                .ext(extRequest)
                .site(Site.builder().keywords("siteKeyword1,siteKeyword2").build())
                .user(User.builder().keywords("userKeyword1,userKeyword2").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gridBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedRequestExtJson =
                "{\"user\":{\"ortb2\":[{\"name\":\"keywords\",\"segments\":[{\"name\":\"keywords\","
                        + "\"value\":\"userKeyword1\"},{\"name\":\"keywords\",\"value\":\"userKeyword2\"}]}],"
                        + "\"firstPublisher\":[{\"name\":\"firstKeywordsUserSection\","
                        + "\"segments\":[{\"name\":\"segment1\",\"value\":\"value1\"}]}],"
                        + "\"secondPublisher\":[{\"name\":\"secondKeywordsUserSection\","
                        + "\"segments\":[{\"name\":\"segment2\",\"value\":\"value2\"}]}]},"
                        + "\"site\":{\"ortb2\":[{\"name\":\"keywords\",\"segments\":[{\"name\":\"keywords\","
                        + "\"value\":\"siteKeyword1\"},{\"name\":\"keywords\",\"value\":\"siteKeyword2\"}]}],"
                        + "\"firstPublisher\":[{\"name\":\"firstKeywordsSiteSection\","
                        + "\"segments\":[{\"name\":\"segment1\",\"value\":\"value1\"}]}],"
                        + "\"secondPublisher\":[{\"name\":\"secondKeywordsSiteSection\","
                        + "\"segments\":[{\"name\":\"segment2\",\"value\":\"value2\"}]}]}}";
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(resultExtRequest -> resultExtRequest.getProperty("keywords"))
                .extracting(JsonNode::toString)
                .containsExactly(expectedRequestExtJson);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpGridUidNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpGridBidder.of(null, Keywords.empty()))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gridBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getType)
                .containsExactly(BidderError.Type.bad_input, BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpGridZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpGridBidder.of(0, Keywords.empty()))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gridBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getType)
                .containsExactly(BidderError.Type.bad_input, BidderError.Type.bad_input);
        assertThat(result.getErrors()).contains(BidderError.badInput("uid is empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .banner(Banner.builder().build())
                                .video(Video.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfNoBannerAndVideoIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .video(Video.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpHadNoBannerOrVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Unknown impression type for ID: 123"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpIdsFromBidAndRequestWereNotMatchedAndImpWasNotFound()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("321").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = gridBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Failed to find impression for ID: 123"));
    }

    private static BidResponse givenBidResponse(UnaryOperator<BidResponse.BidResponseBuilder> bidResponseCustomizer,
                                                UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidResponseCustomizer.apply(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                                .build())))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return givenBidResponse(identity(), bidCustomizer);
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
