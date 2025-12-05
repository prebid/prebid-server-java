package org.prebid.server.bidder.msft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.msft.proto.MsftBidExt;
import org.prebid.server.bidder.msft.proto.MsftBidExtCreative;
import org.prebid.server.bidder.msft.proto.MsftBidExtVideo;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.msft.ExtImpMsft;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class MsftBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";
    private static final int DEFAULT_HB_SOURCE = 5;
    private static final int DEFAULT_HB_SOURCE_VIDEO = 6;

    private final MsftBidder target = new MsftBidder(
            ENDPOINT_URL,
            DEFAULT_HB_SOURCE,
            DEFAULT_HB_SOURCE_VIDEO,
            Map.of(10, "IAB4-5"),
            jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MsftBidder(
                "invalid_url",
                DEFAULT_HB_SOURCE,
                DEFAULT_HB_SOURCE_VIDEO,
                Collections.emptyMap(),
                jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldAddMemberIdToRequestUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenImpExt(impExt -> impExt.member(17))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + "?member_id=17");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod)
                .containsExactly(HttpMethod.POST);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(
                                HttpUtil.ACCEPT_HEADER.toString(),
                                HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.6"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtBidderIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                mapper.createObjectNode().put("bidder", "Invalid imp.ext.bidder")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(
                error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Failed to deserialize Microsoft imp extension: ");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfThereAreImpsWithDuplicateMemberIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenImpExt(impExt -> impExt.member(1))),
                givenImp(givenImpExt(impExt -> impExt.member(2))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Member id mismatch: "
                        + "all impressions must use the same member id but found two different ids: 1 and 2"));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreImpsWithoutMemberIdsWhenCheckingForDuplicateMemberIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenImpExt()),
                givenImp(givenImpExt(impExt -> impExt.member(1))),
                givenImp(givenImpExt()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReplaceImpTagIdWithInvCodeIfItIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp
                .tagid("oldInvCode")
                .ext(givenImpExt(impExt -> impExt.invCode("newInvCode")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("newInvCode");
    }

    @Test
    public void makeHttpRequestsShouldNotReplaceImpTagIdWithInvCodeIfItIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp
                .tagid("oldInvCode")
                .ext(givenImpExt(impExt -> impExt.invCode(null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("oldInvCode");
    }

    @Test
    public void makeHttpRequestsShouldSetBannerDimensionsToFirstFormatDimensionsIfBannerDimensionsAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder().format(List.of(
                        Format.builder().w(200).h(300).build(),
                        Format.builder().w(400).h(600).build())).build())
                .ext(givenImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsExactly(tuple(200, 300));
    }

    @Test
    public void makeHttpRequestsShouldntReplaceBannerDimensionsWithFirstFormatDimensionsIfBannerDimensionsArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder()
                        .w(10)
                        .h(20)
                        .format(List.of(
                                Format.builder().w(200).h(300).build(),
                                Format.builder().w(400).h(600).build())).build())
                .ext(givenImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsExactly(tuple(10, 20));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerApiToBannerFrameworksIfBannerApiIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder().build())
                .ext(givenImpExt(impExt -> impExt.bannerFrameworks(List.of(1, 2, 3))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getApi)
                .containsExactly(List.of(1, 2, 3));
    }

    @Test
    public void makeHttpRequestsShouldNotReplaceBannerApiWithBannerFrameworksIfBannerApiIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder().api(List.of(1, 2, 3)).build())
                .ext(givenImpExt(impExt -> impExt.bannerFrameworks(List.of(4, 5, 6))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getApi)
                .containsExactly(List.of(1, 2, 3));
    }

    @Test
    public void makeHttpRequestsShouldSetDisplayManagerVersionIfItIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.app(
                App.builder().ext(ExtApp.of(ExtAppPrebid.of("testSource", "testVersion"), null)).build()
        ), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("testSource-testVersion");
    }

    @Test
    public void makeHttpRequestsShouldNotReplaceDisplayManagerVersionIfItIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.app(
                App.builder().ext(ExtApp.of(ExtAppPrebid.of("testSource", "testVersion"), null)).build()
        ), givenImp(imp -> imp.displaymanagerver("testDisplayManagerVersion").ext(givenImpExt())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("testDisplayManagerVersion");
    }

    @Test
    public void makeHttpRequestsShouldReplaceImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenImpExt(impExt -> impExt
                .placementId(17)
                .allowSmallerSizes(true)
                .usePmtRule(false)
                .keywords("testKeywords")
                .trafficSourceCode("testTrafficSourceCode")
                .pubclick("testPubClick")
                .extInvCode("testExtInvCode")
                .extImpId("testExtImpId"))
                .put("gpid", "testGpid")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode()
                        .put("gpid", "testGpid")
                        .set("appnexus", mapper.valueToTree(
                                Map.of("placement_id", 17,
                                        "allow_smaller_sizes", true,
                                        "use_pmt_rule", false,
                                        "keywords", "testKeywords",
                                        "traffic_source_code", "testTrafficSourceCode",
                                        "pub_click", "testPubClick",
                                        "ext_inv_code", "testExtInvCode",
                                        "ext_imp_id", "testExtImpId")
                        ))
                );
    }

    @Test
    public void makeHttpRequestsShouldRemoveGpidFieldFromImpExtIfItIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenImpExt()
                .put("gpid", StringUtils.EMPTY)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode().set("appnexus", mapper.createObjectNode()));
    }

    @Test
    public void makeHttpRequestsShouldPreserveOtherBidRequestExtensions() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        final JsonNode anotherExtension = TextNode.valueOf("anotherExtensionValue");
        extRequest.addProperty("anotherExtension", anotherExtension);
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("anotherExtension"))
                .containsExactly(anotherExtension);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfMicrosoftBidRequestExtensionIsInvalid() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("appnexus", TextNode.valueOf("Invalid request.ext"));
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Failed to deserialize Microsoft bid request extension: ");
        });
    }

    @Test
    public void makeHttpRequestsShouldSetBrandCategoryFieldsToTrueIfIncludeBrandCategoryIsPresentInPrebidExtension() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().includebrandcategory(ExtIncludeBrandCategory.of(
                        1, "testPublisher", true, false)).build()).build());
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("appnexus"))
                .extracting(ext -> ext.get("include_brand_category"), ext -> ext.get("brand_category_uniqueness"))
                .containsExactly(tuple(BooleanNode.getTrue(), BooleanNode.getTrue()));
    }

    @Test
    public void makeHttpRequestsShouldPreserveBrandCategoryFieldsIfIncludeBrandCategoryIsAbsentInPrebidExtension() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("appnexus", mapper.valueToTree(Map.of(
                "include_brand_category", false,
                "brand_category_uniqueness", false
        )));
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("appnexus"))
                .extracting(ext -> ext.get("include_brand_category"), ext -> ext.get("brand_category_uniqueness"))
                .containsExactly(tuple(BooleanNode.getFalse(), BooleanNode.getFalse()));
    }

    @Test
    public void makeHttpRequestsShouldSetIsAmpToOneIfRequestComesFromAmpEndpoint() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder().server(ExtRequestPrebidServer.of(
                "testExternalUrl", 1, "testDatacenter", Endpoint.openrtb2_amp.value()
        )).build());
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("appnexus"))
                .extracting(ext -> ext.get("is_amp"))
                .containsExactly(IntNode.valueOf(1));
    }

    @Test
    public void makeHttpRequestsShouldSetIsAmpToZeroIfRequestDoesNotComeFromAmpEndpoint() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder().server(ExtRequestPrebidServer.of(
                "testExternalUrl", 1, "testDatacenter", Endpoint.openrtb2_video.value()
        )).build());
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("appnexus"))
                .extracting(ext -> ext.get("is_amp"))
                .containsExactly(IntNode.valueOf(0));
    }

    @Test
    public void makeHttpRequestsShouldSetHbSourceToHbSourceVideoValueIfRequestComesFromVideoEndpoint() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder().server(ExtRequestPrebidServer.of(
                "testExternalUrl", 1, "testDatacenter", Endpoint.openrtb2_video.value()
        )).build());
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("appnexus"))
                .extracting(ext -> ext.get("hb_source"))
                .containsExactly(IntNode.valueOf(6));
    }

    @Test
    public void makeHttpRequestsShouldSetHbSourceToDefaultValueIfRequestDoesNotComeFromVideoEndpoint() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder().server(ExtRequestPrebidServer.of(
                "testExternalUrl", 1, "testDatacenter", Endpoint.openrtb2_amp.value()
        )).build());
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperty("appnexus"))
                .extracting(ext -> ext.get("hb_source"))
                .containsExactly(IntNode.valueOf(5));
    }

    @Test
    public void makeHttpRequestsShouldPartitionImpressions() {
        // given
        final BidRequest bidRequest = givenBidRequest(Collections.nCopies(30, givenImp()).toArray(new Imp[0]));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .hasSize(3)
                .allSatisfy(imps -> assertThat(imps).hasSize(10));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorAndRequestWithOtherImpressionsIfThereAreImpressionsWithErrors() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt())),
                givenImp(imp -> imp.id("imp2")
                        .ext(mapper.createObjectNode().put("bidder", "Invalid imp.ext.bidder"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(HttpRequest::getImpIds)
                .containsExactly("imp1");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseHasInvalidBody() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                null,
                HttpResponse.of(HttpResponseStatus.CREATED.code(), HeadersMultiMap.headers(), "\"Invalid body\""),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to parse response as BidResponse: ");
        });
    }

    @Test
    public void makeBidsShouldReturnErrorIfThereAreNoBids() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("No valid bids found in response"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidIsMissingMicrosoftExtension() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(Bid.builder().build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactlyInAnyOrder(
                BidderError.badInput("Missing Microsoft bid extension"),
                BidderError.badServerResponse("No valid bids found in response"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidHasInvalidMicrosoftExtension() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().ext(mapper.createObjectNode().put("appnexus", "Invalid")).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Failed to deserialize Microsoft bid extension: ");
                },
                error -> assertThat(error)
                        .isEqualTo(BidderError.badServerResponse("No valid bids found in response"))
        );
    }

    @Test
    public void makeBidsShouldReturnExpectedBidCategory() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(givenBidExt(ext -> ext.brandCategoryId(10))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getCat)
                .containsExactly("IAB4-5");
    }

    @Test
    public void makeBidsShouldPreserveExistingBidCategoryIfIabCategoryFromExtIsUnknown() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(
                bid -> bid.cat(singletonList("testCat")).ext(givenBidExt(ext -> ext.brandCategoryId(-1)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getCat)
                .containsExactly("testCat");
    }

    @Test
    public void makeBidsShouldRemoveExistingBidCategoriesIfThereAreMoreThenOneAndIfIabCategoryFromExtIsUnknown() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(
                bid -> bid.cat(List.of("cat1", "cat2")).ext(givenBidExt(ext -> ext.brandCategoryId(-1)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .hasSize(1)
                .flatExtracting(Bid::getCat)
                .isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedBidTypes() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBid(bid -> bid.id("imp0").ext(givenBidExt(ext -> ext.bidAdType(0)))),
                givenBid(bid -> bid.id("imp1").ext(givenBidExt(ext -> ext.bidAdType(1)))),
                givenBid(bid -> bid.id("imp3").ext(givenBidExt(ext -> ext.bidAdType(3)))),
                givenBid(bid -> bid.id("imp_invalid").ext(givenBidExt(ext -> ext.bidAdType(-1)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(bid -> bid.getBid().getId(), BidderBid::getType)
                .containsExactly(
                        tuple("imp0", banner),
                        tuple("imp1", video),
                        tuple("imp3", xNative)
                );
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Unsupported bid ad type: -1"));
    }

    @Test
    public void makeBidsShouldReturnExpectedCurrency() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(givenBidExt(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("USD");
    }

    @Test
    public void makeBidsShouldReturnExpectedDealPriority() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(givenBidExt(ext -> ext.dealPriority(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getDealPriority)
                .containsExactly(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnDefaultDealPriority() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(givenBidExt(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getDealPriority)
                .containsExactly(0);
    }

    @Test
    public void makeBidsShouldReturnExpectedVideoDuration() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(givenBidExt(ext -> ext.creativeInfo(
                MsftBidExtCreative.of(MsftBidExtVideo.of(17))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .containsExactly(17);
    }

    @Test
    public void makeBidsShouldReturnDefaultVideoDuration() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid(givenBidExt(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .containsExactly(0);
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer
                .apply(BidRequest.builder()
                        .id("testBidRequestId")
                        .imp(List.of(imps)))
                .build();
    }

    private static Imp givenImp() {
        return givenImp(givenImpExt());
    }

    private static Imp givenImp(ObjectNode impExt) {
        return givenImp(imp -> imp.ext(impExt));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static ObjectNode givenImpExt() {
        return givenImpExt(identity());
    }

    private static ObjectNode givenImpExt(UnaryOperator<ExtImpMsft.ExtImpMsftBuilder> extImpCustomizer) {
        return mapper.valueToTree(ExtPrebid.of(
                null,
                extImpCustomizer.apply(ExtImpMsft.builder()).build()));
    }

    private static BidderCall<BidRequest> givenHttpCall(Bid... bids) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(HttpResponseStatus.CREATED.code(), null, givenBidResponse(bids)),
                null);
    }

    private static String givenBidResponse(Bid... bids) {
        try {
            return mapper.writeValueAsString(BidResponse.builder()
                    .cur("USD")
                    .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error encoding BidResponse to json: " + e);
        }
    }

    private static Bid givenBid(ObjectNode bidExt) {
        return givenBid(bid -> bid.ext(bidExt));
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static ObjectNode givenBidExt(UnaryOperator<MsftBidExt.MsftBidExtBuilder> extCustomizer) {
        return mapper.valueToTree(Map.of("appnexus", extCustomizer.apply(MsftBidExt.builder()).build()));
    }
}
