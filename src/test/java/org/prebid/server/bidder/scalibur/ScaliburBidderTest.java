package org.prebid.server.bidder.scalibur;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.scalibur.proto.request.ExtImpScalibur;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;

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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScaliburBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String VAST_XML = """
            <VAST version="3.0"><Ad><Wrapper><VASTAdTagURI><![CDATA[%s]]></VASTAdTagURI></Wrapper></Ad></VAST>""";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private ScaliburBidder target;

    @BeforeEach
    public void setUp() {
        target = new ScaliburBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ScaliburBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWithoutRequestWhenAllImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldAddErrorsOnInvalidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                // valid imps
                givenImp(identity()),
                givenImp(imp -> imp.ext(givenImpExt(ExtImpScalibur.of("placementId", null, DEFAULT_BID_CURRENCY)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(1)
                .satisfies(errors ->
                        assertThat(errors.getFirst()).startsWith("Cannot deserialize value of type"));
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorIfCurrencyIsDifferent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("EUR")));

        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "EUR", DEFAULT_BID_CURRENCY))
                .willReturn(BigDecimal.ONE);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldNotConvertBidfloorAndAssignUSDCurrencyWhenBidfloorHasEmptyCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur(null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldOverrideBidfloorAndCurrencyWhenScaliburBidFloorIsValid() {
        // given

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("EUR")
                        .ext(givenImpExt(ExtImpScalibur.of("placementId", BigDecimal.ONE, DEFAULT_BID_CURRENCY)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldNotOverrideBidfloorAndCurrencyWhenScaliburBidFloorIsInvalid() {
        // given

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur(DEFAULT_BID_CURRENCY)
                        .ext(givenImpExt(ExtImpScalibur.of("placementId", null, "EUR")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldFallbackToImpCurrencyWhenScaliburCurrencyIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("EUR")
                        .ext(givenImpExt(ExtImpScalibur.of("placementId", BigDecimal.ONE, null)))));

        given(currencyConversionService.convertCurrency(BigDecimal.ONE, bidRequest, "EUR", DEFAULT_BID_CURRENCY))
                .willReturn(BigDecimal.valueOf(2));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.valueOf(2), DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldSetPlacementIdInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .ext(givenImpExt(ExtImpScalibur.of("placementId", null, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(ExtImpScalibur.of("placementId", null, DEFAULT_BID_CURRENCY)));
    }

    @Test
    public void makeHttpRequestsShouldBuildImpExtWithScaliburFieldsAndGpid() {
        // given
        final ObjectNode impExt = givenImpExt(ExtImpScalibur.of("placementId", BigDecimal.ONE, DEFAULT_BID_CURRENCY));
        impExt.put("gpid", "test-gpid");
        impExt.put("random", "test-random");

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(
                        mapper.createObjectNode()
                                .put("placementId", "placementId")
                                .put("bidfloor", BigDecimal.ONE)
                                .put("bidfloorcur", DEFAULT_BID_CURRENCY)
                                .put("gpid", "test-gpid"));
    }

    @Test
    public void makeHttpRequestsShouldFillVideoDefaultValues() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(null)
                        .video(Video.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .satisfies(request -> assertThat(request.getImp())
                        .singleElement()
                        .extracting(Imp::getVideo)
                        .satisfies(video -> {
                            assertThat(video.getMimes()).containsExactly("video/mp4");
                            assertThat(video.getMinduration()).isEqualTo(1);
                            assertThat(video.getMaxduration()).isEqualTo(180);
                            assertThat(video.getMaxbitrate()).isEqualTo(30_000);
                            assertThat(video.getProtocols()).containsExactly(2, 3, 5, 6);
                            assertThat(video.getW()).isEqualTo(640);
                            assertThat(video.getH()).isEqualTo(480);
                            assertThat(video.getPlacement()).isEqualTo(1);
                            assertThat(video.getLinearity()).isEqualTo(1);
                        }));
    }

    @Test
    public void makeHttpRequestsShouldPreserveProvidedVideoValues() {
        // given
        final Video video = Video.builder()
                .mimes(List.of("video/webm"))
                .minduration(10)
                .maxduration(30)
                .maxbitrate(5000)
                .protocols(List.of(1, 2))
                .w(1280)
                .h(720)
                .placement(2)
                .linearity(2)
                .build();

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(null)
                        .video(video)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .satisfies()
                .satisfies(request -> assertThat(request.getImp())
                        .singleElement()
                        .extracting(Imp::getVideo)
                        .isEqualTo(video));
    }

    @Test
    public void makeHttpRequestsShouldSetIsDebugWhenTestIsEnabled() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(identity()))
                .toBuilder()
                .test(1)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .satisfies(ext -> assertThat(ext.getProperty("isDebug").asInt()).isEqualTo(1));
    }

    @Test
    public void makeHttpRequestsShouldSetIsDebugWhenPrebidDebugIsEnabled() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(identity()))
                .toBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .satisfies(ext -> assertThat(ext.getProperty("isDebug").asInt()).isEqualTo(1));
    }

    @Test
    public void makeHttpRequestsShouldRemoveRequestExtWhenDebugIsDisabled() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("random", TextNode.valueOf("test-random"));

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp))
                .toBuilder()
                .ext(extRequest)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .isNull();
    }

    @Test
    public void makeBidsShouldReturnBidsFromAllSeatBids() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(identity()),
                givenImp(imp -> imp.id("456")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder.id("bid-1").impid("123"),
                                bidBuilder -> bidBuilder.id("bid-2").impid("456")
                        )));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bid-1", "bid-2");
    }

    @Test
    public void makeBidsShouldUseUsdCurrencyWhenResponseCurrencyIsMissing() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.id("bid-1").impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBidCurrency)
                .isEqualTo(DEFAULT_BID_CURRENCY);
    }

    @Test
    public void makeBidsShouldUseResponseCurrency() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidResponse bidResponse = givenBidResponse(bidBuilder -> bidBuilder.id("bid-1").impid("123")).toBuilder()
                .cur("EUR")
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBidCurrency)
                .isEqualTo("EUR");
    }

    @Test
    public void makeBidsShouldRejectInvalidBidImpId() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("invalid-imp"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("Invalid bid imp ID invalid-imp");
    }

    @Test
    public void makeBidsShouldResolveBannerBidTypeFromMtype() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.banner);
    }

    @Test
    public void makeBidsShouldResolveVideoBidTypeFromMtype() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.banner(null).video(Video.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.video);
    }

    @Test
    public void makeBidsShouldResolveBidTypeFromImpWhenMtypeIsMissing() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(Banner.builder().w(1).h(1).build()).video(null)));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.banner);
    }

    @Test
    public void makeBidsShouldResolveVideoBidTypeFromVideoImpWhenMtypeIsMissing() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(null).video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.video);
    }

    @Test
    public void makeBidsShouldRecoverMediaTypeFromImpWhenMtypeIsInvalid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.banner);
    }

    @Test
    public void makeBidsShouldRejectAmbiguousImpMediaType() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(Banner.builder().w(1).h(1).build())
                        .video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("Unsupported or ambiguous media type for bid id=bid-1");
    }

    @Test
    public void makeBidsShouldRejectInvalidMediaType() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(null)
                        .audio(Audio.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("Unsupported or ambiguous media type for bid id=bid-1");
    }

    @Test
    public void makeBidsShouldReplaceAdmWithVastXml() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(null).video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .ext(mapper.createObjectNode().put("vastXml", "<VAST>test</VAST>")))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .isEqualTo("<VAST>test</VAST>");
    }

    @Test
    public void makeBidsShouldReplaceNotPresentAdmWithVastUrlWrapper() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(null).video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .ext(mapper.createObjectNode().put("vastUrl", "https://test.com")))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .isEqualTo(VAST_XML.formatted("https://test.com"));
    }


    @Test
    public void makeBidsShouldPrioritizeVastXmlOverVastUrlWhenBothArePresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(null).video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .ext(mapper.createObjectNode()
                                                .put("vastXml", "<VAST>test</VAST>")
                                                .put("vastUrl", "https://test.com")))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .isEqualTo("<VAST>test</VAST>");
    }

    @Test
    public void makeBidsShouldPreserveAdmWhenVastUrlIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(null).video(Video.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .id("bid-1")
                                        .impid("123")
                                        .adm("test-adm")
                                        .ext(mapper.createObjectNode().put("vastUrl", "https://test.com")))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .isEqualTo("test-adm");
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseHasNoSeatBids() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(Collections.emptyList())
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(givenImp(identity())), "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("Failed to decode: Unrecognized token"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnValidBidsAndErrorsForInvalidBids() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder.id("valid-bid").impid("123"),
                                bidBuilder -> bidBuilder.id("invalid-bid").impid("invalid-imp"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("valid-bid");

        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("Invalid bid imp ID invalid-imp");
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder()
                .cur(List.of("USD"))
                .imp(Arrays.stream(imps).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(givenImpExt(ExtImpScalibur.of("placementId", null, null))))
                .build();
    }

    private static ObjectNode givenImpExt(ExtImpScalibur extImpScalibur) {
        return mapper.valueToTree(ExtPrebid.of(null, extImpScalibur));
    }

    @SafeVarargs
    @SneakyThrows
    private BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .toList())
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
