package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.grid.model.request.ExtImp;
import org.prebid.server.bidder.grid.model.request.ExtImpGridData;
import org.prebid.server.bidder.grid.model.request.ExtImpGridDataAdServer;
import org.prebid.server.bidder.grid.model.request.GridNative;
import org.prebid.server.bidder.grid.model.request.Keywords;
import org.prebid.server.bidder.grid.model.response.GridBidResponse;
import org.prebid.server.bidder.grid.model.response.GridSeatBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.grid.ExtImpGrid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;

import java.io.IOException;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class GridBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final GridBidder target = new GridBidder(ENDPOINT_URL, jacksonMapper);

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
                                ExtPrebid.of(null, ExtImpGrid.of(10, Keywords.empty()))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyModifyImpExt() {
        // given
        final ObjectNode objectNode = jacksonMapper.mapper().createObjectNode().put("version", "2.1");

        final ExtImpGridData extImpGridData = ExtImpGridData.of("pbadslot",
                ExtImpGridDataAdServer.of("name", "adslot"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtImp.builder()
                                .data(extImpGridData)
                                .skadn(objectNode)
                                .bidder(ExtImpGrid.of(1, null))
                                .build()))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ExtImp expectedExtImp = ExtImp.builder()
                .data(extImpGridData)
                .skadn(objectNode)
                .bidder(ExtImpGrid.of(1, null))
                .gpid("adslot")
                .build();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(expectedExtImp));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyModifyNative() {
        // given
        final String nativeRequest = "{\"eventtrackers\":[],\"context\":1,\"plcmttype\":2}";
        final Imp nativeImp = Imp.builder()
                .xNative(Native.builder().request(nativeRequest).ver("1.2").build())
                .ext(mapper.valueToTree(ExtImp.builder().bidder(ExtImpGrid.of(1, null)).build()))
                .build();
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(nativeImp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode expectedNativeRequest = mapper.createObjectNode();
        expectedNativeRequest.set("eventtrackers", mapper.createArrayNode());
        expectedNativeRequest.set("context", new IntNode(1));
        expectedNativeRequest.set("plcmttype", new IntNode(2));
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .containsExactly(GridNative.builder()
                        .requestNative(expectedNativeRequest)
                        .request(null)
                        .ver("1.2")
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyModifyRequestExt() throws IOException {
        // given
        final Keywords impExtKeywords = mapper.convertValue(jsonNodeFrom("imp-ext-keywords.json"), Keywords.class);

        final ExtImp impExt = ExtImp.builder()
                .data(ExtImpGridData.of("pbadslot", ExtImpGridDataAdServer.of("name", "adslot")))
                .bidder(ExtImpGrid.of(1, impExtKeywords))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.valueToTree(impExt)).build()))
                .id("request_id")
                .ext(mapper.convertValue(jsonNodeFrom("request-ext.json"), ExtRequest.class))
                .site(Site.builder().keywords("siteKeyword1,siteKeyword2").build())
                .user(User.builder().keywords("userKeyword1,userKeyword2").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(mapper.convertValue(jsonNodeFrom("expected-request-ext.json"), ExtRequest.class));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpGridUidNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpGrid.of(null, Keywords.empty()))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).contains(BidderError.badInput("Empty uid in imp with id: 123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpGridZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpGrid.of(0, Keywords.empty()))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).contains(BidderError.badInput("Empty uid in imp with id: 123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnZeroValidImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No valid impressions for grid"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .banner(Banner.builder().build())
                                .video(Video.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(givenBidNode())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfNoBannerAndVideoIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .video(Video.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(givenBidNode())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNoBannerAndNoVideoAndNativeIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .xNative(Native.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(givenBidNode())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpHadNoBannerOrVideoOrNative() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(givenBidNode())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Unknown impression type for ID: 123"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpIdsFromBidAndRequestWereNotMatchedAndImpWasNotFound()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("321").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(givenBidNode())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Failed to find impression for ID: 123"));
    }

    @Test
    public void makeBidsShouldReturnBidWithTypeFromGridBidContentTypeIfPresent() throws JsonProcessingException {
        // given
        final ObjectNode bidNode = givenBidNode()
                .put("content_type", "banner");

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidNode)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldModifyBidExtWithMetaIfDemandSourceIsPresentInBidExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("bidder", mapper.createObjectNode()
                        .set("grid", mapper.createObjectNode()
                                .set("demandSource", TextNode.valueOf("demandSource"))));
        final ObjectNode bidNode = givenBidNode().set("ext", bidExt);

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidNode)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ExtBidPrebidMeta expectedBidMeta = ExtBidPrebidMeta.builder().demandSource("demandSource").build();
        final ObjectNode expectedBidExt = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().meta(expectedBidMeta).build(), null));

        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(expectedBidExt);
    }

    @Test
    public void makeBidsShouldNotReplacePresentAdmWithAdmNative() throws JsonProcessingException {
        // given
        final ObjectNode admNative = mapper.createObjectNode();
        admNative.put("admNativeProperty", "admNativeValue");

        final ObjectNode bidNode = givenBidNode()
                .put("adm", "someAdm")
                .set("adm_native", admNative);

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidNode)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("someAdm");
    }

    @Test
    public void makeBidsShouldReplaceNotPresentAdmWithAdmNative() throws JsonProcessingException {
        // given
        final ObjectNode admNative = mapper.createObjectNode();
        admNative.put("admNativeProperty", "admNativeValue");

        final ObjectNode bidNode = givenBidNode()
                .set("adm_native", admNative);

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidNode)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("{\"admNativeProperty\":\"admNativeValue\"}");
    }

    private static GridBidResponse givenBidResponse(
            UnaryOperator<GridBidResponse.GridBidResponseBuilder> gridBidResponse,
            ObjectNode objectNode) {

        final GridSeatBid gridSeatBid = GridSeatBid.builder()
                .bid(singletonList(objectNode))
                .build();

        return gridBidResponse
                .apply(GridBidResponse.builder().cur("USD").seatbid(singletonList(gridSeatBid)))
                .build();
    }

    private static GridBidResponse givenBidResponse(ObjectNode objectNode) {
        return givenBidResponse(identity(), objectNode);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static JsonNode jsonNodeFrom(String path) throws IOException {
        return mapper.readTree(GridBidderTest.class.getResourceAsStream(path));
    }

    private ObjectNode givenBidNode() {
        return mapper.createObjectNode().put("impid", "123");
    }
}
