package org.prebid.server.bidder.sspbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sspbc.ExtImpSspbc;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class SspbcBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com";

    private SspbcBidder sspbcBidder;

    @Before
    public void setUp() {
        sspbcBidder = new SspbcBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SspbcBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com?bdver=5.8&inver=4");
    }

    @Test
    public void makeHttpRequestsShouldThrowErrorWhenSiteNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.site(null), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("BidRequest.site not provided");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidExtImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
                });
    }

    @Test
    public void makeHttpRequestsShouldUpdateBidRequestTestTo1WhenBidRequestTest3AndImpExtTestIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.test(3),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTest)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldUpdateSiteIdToEmptyStringWhenImpExtTestZeroAndImpExtSiteIdNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder()
                                .id("AnySiteId")
                                .page("https://test.page/")
                                .build()),
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSspbc
                        .of("extSiteId", null, 0)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsExactly(StringUtils.EMPTY);
    }

    @Test
    public void makeHttpRequestsShouldUpdateSiteIdToEmptyStringWhenImpExtTestZeroAndImpExtIdNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder()
                                .id("AnySiteId")
                                .page("https://test.page/")
                                .build()),
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSspbc
                        .of(null, "ExtImpId", 0)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsExactly(StringUtils.EMPTY);
    }

    @Test
    public void makeHttpRequestsShouldReplaceSiteIdFromImpExtSiteIdWhenImpExtTestZeroAndImpExtIdAndSiteIdNotEmpty() {
        // given
        final String expectedSiteId = "siteId";
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSspbc
                        .of(expectedSiteId, "ExtImpId", 0)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsExactly(expectedSiteId);
    }

    @Test
    public void makeHttpRequestsShouldReplaceImpIdOnImpExtIdWhenImpExtSiteAndIdIsNotEmpty() {
        // given
        final String expectedId = "AnyId";
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("randomImpId")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpSspbc.of("SiteId", expectedId, 1)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly(expectedId);
    }

    @Test
    public void makeHttpRequestsShouldReplaceImpTagIdOnImpIdWhenImpExtIdNull() {
        // given
        final String expectedId = "impId";
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id(expectedId)
                .tagid("randomTagId")
                .ext(mapper.valueToTree(ExtPrebid
                        .of(null, ExtImpSspbc.of("siteId", null, 123)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly(expectedId);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithData() {
        // given
        final String expectedPbSlot = "randomTagId";
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .tagid(expectedPbSlot));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("pbslot", expectedPbSlot)
                                .put("pbsize", "1x1")));
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtDataPbSizeWithBannerFormatWandH() {
        // given
        final String expectedPbSlot = "randomTagId";
        final Integer bannerFormatW = 25;
        final Integer bannerFormatH = 45;
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder()
                        .format(singletonList(Format.builder()
                                .w(bannerFormatW)
                                .h(bannerFormatH).build())).build())
                .tagid(expectedPbSlot));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("pbslot", expectedPbSlot)
                                .put("pbsize", String.format("%dx%d", bannerFormatW, bannerFormatH))));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSitePageIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .site(Site.builder().page("invalid_url////[' and ']").build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sspbcBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Malformed URL: invalid_url////[' and ']."));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = sspbcBidder.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = sspbcBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpIdNotEqualsBidImpId() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.adm("Any adm"))));

        // when
        final Result<List<BidderBid>> result = sspbcBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("imp not found"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidAmdIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(),
                        impBuilder -> impBuilder.id("id").tagid("tagId")),
                mapper.writeValueAsString(givenBidResponse(bidBuilder ->
                        bidBuilder
                                .impid("id")
                                .adm(null)
                                .ext(mapper.createObjectNode()
                                        .put("adlabel", "anyAdLabel")
                                        .put("pubid", "anyPubId")
                                        .put("siteid", "anySiteId")
                                        .put("slotid", "anySlotId")))));

        // when
        final Result<List<BidderBid>> result = sspbcBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Bid format is not supported"));
    }

    @Test
    public void makeBidsShouldNotUpdateBidAdmWhenBidAdmContainPreformattedValue() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(
                        bidRequestBuilder -> bidRequestBuilder.site(
                                Site.builder()
                                        .page("AnyPage")
                                        .ref("anyRef")
                                        .build()),
                        impBuilder -> impBuilder.id("id").tagid("tagId")),
                mapper.writeValueAsString(givenBidResponse(bidBuilder ->
                        bidBuilder
                                .impid("id")
                                .adm("any<!--preformatted-->")
                                .ext(mapper.createObjectNode()
                                        .put("adlabel", "anyAdLabel")
                                        .put("pubid", "anyPubId")
                                        .put("siteid", "anySiteId")
                                        .put("slotid", "anySlotId")))));

        // when
        final Result<List<BidderBid>> result = sspbcBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("any<!--preformatted-->");
    }

    @Test
    public void makeBidsShouldNotUpdateBidAdmWhenBidAdmNotContainPreformattedValue() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(
                        bidRequestBuilder -> bidRequestBuilder
                                .id("bidRequestId")
                                .site(Site.builder()
                                        .page("AnyPage")
                                        .ref("anyRef")
                                        .build()),
                        impBuilder -> impBuilder.id("id").tagid("tagId")),
                mapper.writeValueAsString(givenBidResponse(bidBuilder ->
                        bidBuilder
                                .impid("id")
                                .adm("anyAdm")
                                .ext(mapper.createObjectNode()
                                        .put("adlabel", "anyAdLabel")
                                        .put("pubid", "anyPubId")
                                        .put("siteid", "anySiteId")
                                        .put("slotid", "anySlotId")))));

        // when
        final Result<List<BidderBid>> result = sspbcBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("<html><head><title></title><meta charset=\"UTF-8\"><meta name=\"viewport\" "
                        + "content=\"width=device-width, initial-scale=1.0\"><style> body { background-color: "
                        + "transparent; margin: 0; padding: 0; }</style><script> window.rekid = anySiteId; window.slot "
                        + "= anySlotId; window.adlabel = 'anyAdLabel'; window.pubid = 'anyPubId'; window.wp_sn = "
                        + "'sspbc_go'; window.page = 'AnyPage'; window.ref = 'anyRef'; window.mcad = "
                        + "{\"id\":\"bidRequestId\",\"seat\":\"anySeat\",\"seatbid\":{\"bid\":[{\"impid\":\"id\""
                        + ",\"adm\":\"anyAdm\",\"ext\":{\"adlabel\":\"anyAdLabel\",\"pubid\":\"anyPubId\",\"siteid\""
                        + ":\"anySiteId\",\"slotid\":\"anySlotId\"}}]}}; window.inver = '4'; </script></head><body>"
                        + "<div id=\"c\"></div><script async crossorigin nomodule "
                        + "src=\"//std.wpcdn.pl/wpjslib/wpjslib-inline.js\" id=\"wpjslib\"></script><script async "
                        + "crossorigin type=\"module\" src=\"//std.wpcdn.pl/wpjslib6/wpjslib-inline.js\" "
                        + "id=\"wpjslib6\"></script></body></html>");
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .site(Site.builder().page("https://test.page/").build())
                        .test(0)
                        .imp(singletonList(impCustomizer.apply(Imp.builder().id("123")
                                .ext(mapper.valueToTree(ExtPrebid
                                        .of(null, ExtImpSspbc
                                                .of("siteId", "AnyId", 123))))).build())))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().seat("anySeat")
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
