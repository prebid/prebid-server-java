package org.prebid.server.bidder.goadserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.goadserver.ExtImpGoadserver;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;

public class GoadserverBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://pbs.goadserver.com/openrtb2/auction";

    private final GoadserverBidder target = new GoadserverBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GoadserverBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSiteIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.site(null), givenImp("imp1", identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("goadserver supports site requests only"));
    }

    @Test
    public void makeHttpRequestsShouldGroupImpsByToken() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", identity()),
                givenImp("imp2", ext -> ExtImpGoadserver.of("tokB", null, null)),
                givenImp("imp3", identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri, HttpRequest::getImpIds,
                        request -> request.getPayload().getSite().getPublisher().getId())
                .containsExactly(
                        tuple(ENDPOINT_URL, Set.of("imp1", "imp3"), "tok123"),
                        tuple(ENDPOINT_URL, Set.of("imp2"), "tokB"));
    }

    @Test
    public void makeHttpRequestsShouldReplacePublisherIdAndKeepOtherPublisherFields() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher, Site::getPage)
                .containsExactly(tuple(Publisher.builder().id("tok123").name("Publisher").build(),
                        "https://publisher.example/article"));
    }

    @Test
    public void makeHttpRequestsShouldCreatePublisherWhenAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().page("https://publisher.example").build()),
                givenImp("imp1", identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(request -> request.getPayload().getSite().getPublisher())
                .containsExactly(Publisher.builder().id("tok123").build());
    }

    @Test
    public void makeHttpRequestsShouldApplyFloorOnlyWhenImpHasNone() {
        // given
        final Imp withoutFloor = givenImp("imp1",
                ext -> ExtImpGoadserver.of("tok123", new BigDecimal("0.5"), null));
        final Imp withFloor = givenImp("imp2",
                ext -> ExtImpGoadserver.of("tok123", new BigDecimal("0.5"), null))
                .toBuilder().bidfloor(new BigDecimal("1.2")).bidfloorcur("EUR").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result =
                target.makeHttpRequests(givenBidRequest(withoutFloor, withFloor));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .flatExtracting(request -> request.getPayload().getImp())
                .extracting(Imp::getId, Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(
                        tuple("imp1", new BigDecimal("0.5"), "USD"),
                        tuple("imp2", new BigDecimal("1.2"), "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldReplaceImpExtWithSubid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", ext -> ExtImpGoadserver.of("tok123", null,
                        TextNode.valueOf("sports"))),
                givenImp("imp2", ext -> ExtImpGoadserver.of("tok123", null,
                        IntNode.valueOf(42))),
                givenImp("imp3", identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .flatExtracting(request -> request.getPayload().getImp())
                .extracting(Imp::getExt)
                .containsExactly(givenSubidExt("sports"), givenSubidExt("42"), null);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForMissingTokenAndKeepValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", ext -> ExtImpGoadserver.of(" ", null, null)),
                givenImp("imp2", identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("imp imp1: missing token"));
        assertThat(result.getValue()).flatExtracting(HttpRequest::getImpIds).containsExactly("imp2");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForInvalidImpExt() {
        // given
        final Imp imp = Imp.builder().id("imp1").banner(Banner.builder().build())
                .ext(mapper.createObjectNode().put("bidder", "invalid")).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(givenBidRequest(imp));

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("imp imp1: invalid ext.bidder"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForMissingBidderExt() {
        // given
        final Imp imp = Imp.builder().id("imp1").banner(Banner.builder().build())
                .ext(mapper.createObjectNode()).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(givenBidRequest(imp));

        // then
        assertThat(result.getErrors()).containsExactly(badInput("imp imp1: missing ext.bidder"));
    }

    @Test
    public void makeHttpRequestsShouldSetOpenRtbVersionHeader() {
        // when
        final Result<List<HttpRequest<BidRequest>>> result =
                target.makeHttpRequests(givenBidRequest(givenImp("imp1", identity())));

        // then
        assertThat(result.getValue()).singleElement()
                .extracting(request -> request.getHeaders().get(HttpUtil.X_OPENRTB_VERSION_HEADER))
                .isEqualTo("2.5");
    }

    @Test
    public void makeBidsShouldReturnErrorForInvalidResponse() {
        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall("invalid"), null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).singleElement()
                .extracting(BidderError::getType)
                .isEqualTo(BidderError.Type.bad_server_response);
    }

    @Test
    public void makeBidsShouldReturnEmptyListForEmptySeatbid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().cur("USD").build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldResolveTypeFromMtype() throws JsonProcessingException {
        // given
        final String response = givenBidResponse("USD",
                givenBid("b1", 1, null), givenBid("b2", 2, null), givenBid("b3", 4, null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner, BidType.video, BidType.xNative);
    }

    @Test
    public void makeBidsShouldFallBackToExtPrebidTypeAndStripTargeting() throws JsonProcessingException {
        // given
        final String response = givenBidResponse("USD",
                givenBid("b1", null, givenPrebidExt("banner")),
                givenBid("b2", null, givenPrebidExt("video")),
                givenBid("b3", null, givenPrebidExt("native")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bidderBid -> bidderBid.getBid().getExt())
                .containsExactly(
                        tuple(BidType.banner, null),
                        tuple(BidType.video, null),
                        tuple(BidType.xNative, null));
    }

    @Test
    public void makeBidsShouldKeepOnlyDsaInBidExt() throws JsonProcessingException {
        // given
        final ObjectNode ext = givenPrebidExt("banner");
        final ObjectNode dsa = mapper.createObjectNode().put("behalf", "Advertiser").put("paid", "Advertiser");
        ext.set("dsa", dsa);
        final String response = givenBidResponse("USD", givenBid("b1", null, ext));

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), null);

        // then
        final ObjectNode expectedExt = mapper.createObjectNode();
        expectedExt.set("dsa", dsa);
        assertThat(result.getValue()).singleElement()
                .extracting(bidderBid -> bidderBid.getBid().getExt())
                .isEqualTo(expectedExt);
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedTypeAndKeepValidBids() throws JsonProcessingException {
        // given
        final Bid validBid = givenBid("b2", 1, null);
        final String response = givenBidResponse("EUR",
                givenBid("b1", 3, givenPrebidExt("audio")), validBid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), null);

        // then
        assertThat(result.getErrors())
                .containsExactly(badServerResponse("unsupported media type for bid b1 on imp imp1"));
        assertThat(result.getValue()).containsExactly(BidderBid.of(validBid, BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldDefaultCurrencyToUsd() throws JsonProcessingException {
        // given
        final String response = givenBidResponse(null, givenBid("b1", 1, null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), null);

        // then
        assertThat(result.getValue()).extracting(BidderBid::getBidCurrency).containsExactly("USD");
    }

    private static UnaryOperator<ExtImpGoadserver> identity() {
        return ext -> ext;
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request, imps);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              Imp... imps) {

        final Site site = Site.builder()
                .page("https://publisher.example/article")
                .publisher(Publisher.builder().id("pbs-account").name("Publisher").build())
                .build();
        return requestCustomizer.apply(BidRequest.builder().id("request-id").site(site).imp(List.of(imps)))
                .build();
    }

    private static Imp givenImp(String impId, UnaryOperator<ExtImpGoadserver> extCustomizer) {
        final ExtImpGoadserver ext = extCustomizer.apply(ExtImpGoadserver.of("tok123", null, null));
        return Imp.builder()
                .id(impId)
                .banner(Banner.builder().w(300).h(250).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ext)))
                .build();
    }

    private static ObjectNode givenSubidExt(String subid) {
        final ObjectNode ext = mapper.createObjectNode();
        ext.putObject("goadserver").put("subid", subid);
        return ext;
    }

    private static ObjectNode givenPrebidExt(String type) {
        final ObjectNode ext = mapper.createObjectNode();
        final ObjectNode prebid = ext.putObject("prebid").put("type", type);
        prebid.putObject("targeting").put("hb_pb", "1.25").put("hb_bidder", "goadserver");
        return ext;
    }

    private static Bid givenBid(String id, Integer mtype, ObjectNode ext) {
        return Bid.builder().id(id).impid("imp1").price(BigDecimal.ONE).mtype(mtype).ext(ext).build();
    }

    private static String givenBidResponse(String currency, Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur(currency)
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
