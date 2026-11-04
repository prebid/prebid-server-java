package org.prebid.server.bidder.adgeneration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adgeneration.model.request.AdgenerationRequest;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationLocationParams;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationOption;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationResponse;
import org.prebid.server.bidder.adgeneration.model.response.AdgenerationResult;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.adgeneration.ExtImpAdgeneration;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class AdgenerationBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";
    private static final String PBS_VERSION = "0.0.0";
    private static final String DEFAULT_CURRENCY = "JPY";
    private static final String VAST_XML = """
            <VAST>
            <Ad>ad-test</Ad>
            </VAST>""";
    private static final String ENCODED_VAST_XML = "%3CVAST%3E%3CAd%3Ead-test%3C%2FAd%3E%3C%2FVAST%3E";
    private static final String UPPER_BILLBOARD_ADM = """
            <script type="text/javascript" src="https://i.socdm.com/sdk/js/adg-browser-m.js"></script>
            <script type="text/javascript">
            window.ADGBrowserM.init({vastXml: decodeURIComponent('%s'), marginTop: '%s'});
            </script>""";
    private static final String TRACKER_URL = "https://test.tracker";

    private final AdgenerationBidder target = new AdgenerationBidder(ENDPOINT_URL, PBS_VERSION, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdgenerationBidder("invalid_url", PBS_VERSION, jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .asString()
                .startsWith("Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithExpectedHeaders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .device(Device.builder().ip("123").ua("test").build())
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("User-Agent", "test"),
                        tuple("X-Forwarded-For", "123"));
    }

    @Test
    public void makeHttpRequestsShouldCreateSeparateRequestForEachValidImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(identity()),
                        givenImp(imp -> imp.id("456").ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpAdgeneration.of(" ", null))))),
                        givenImp(imp -> imp
                                .id("789")
                                .banner(null)
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(
                                        null, ExtImpAdgeneration.of("location-789", null)))))))
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("No Location ID in ExtImpAdgeneration."));
        assertThat(result.getValue())
                .extracting(
                        HttpRequest::getImpIds,
                        HttpRequest::getUri,
                        request -> request.getPayload().getOrtb().getImp().getFirst().getId(),
                        request -> request.getPayload().getIMark())
                .containsExactly(
                        tuple(Set.of("123"),
                                "https://test.endpoint.com/?id=123&posall=SSPLOC&sdktype=0",
                                "123", 1),
                        tuple(Set.of("789"),
                                "https://test.endpoint.com/?id=location-789&posall=SSPLOC&sdktype=0",
                                "789", null));
    }

    @Test
    public void makeHttpRequestsShouldSelectUsdIgnoringCase() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .cur(List.of("EUR", "usd"))
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .extracting(AdgenerationRequest::getCurrency)
                .isEqualTo("USD");
    }

    @Test
    public void makeHttpRequestsShouldUseDefaultCurrencyWhenUsdIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .cur(asList("GBP", "EUR"))
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .extracting(AdgenerationRequest::getCurrency)
                .isEqualTo(DEFAULT_CURRENCY);
    }

    @Test
    public void makeHttpRequestsShouldUseDefaultCurrencyWhenCurrenciesAreMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .singleElement()
                .extracting(HttpRequest::getPayload)
                .extracting(AdgenerationRequest::getCurrency)
                .isEqualTo(DEFAULT_CURRENCY);
    }

    @Test
    public void makeHttpRequestsShouldPreferExplicitWebChannelOverAppPresence() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .app(App.builder().build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("web"))
                        .build()))
                .device(Device.builder().os("android").build())
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/?id=123&posall=SSPLOC&sdktype=0");
    }

    @Test
    public void makeHttpRequestsShouldDetectSdkTypeByAppWhenChannelIsMissing() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .app(App.builder().build())
                .device(Device.builder().os("android").build())
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/?id=123&posall=SSPLOC&sdktype=1");
    }

    @Test
    public void makeHttpRequestsShouldDetectSdkTypeIgnoringCase() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("APP"))
                        .build()))
                .device(Device.builder().os("iOS").build())
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/?id=123&posall=SSPLOC&sdktype=2");
    }

    @Test
    public void makeHttpRequestsShouldUseDefaultSdkTypeWhenAppOsIsUnknown() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .app(App.builder().build())
                .device(Device.builder().os("linux").build())
                .build();

        // when
        final Result<List<HttpRequest<AdgenerationRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/?id=123&posall=SSPLOC&sdktype=0");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(bidRequest, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldNotReturnErrorIfResultsAreEmpty() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse();
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("auction")
                .imp(singletonList(givenImp(identity())))
                .build();
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                AdgenerationResult.of(
                        "<body>ad</body>", null, null, BigDecimal.TEN,
                        "creativeid", "dealid", 100, 200, null, null, null, null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("auction-123")
                        .impid("123")
                        .price(BigDecimal.TEN)
                        .crid("creativeid")
                        .dealid("dealid")
                        .w(200)
                        .h(100)
                        .adm("ad")
                        .build(),
                BidType.banner, DEFAULT_CURRENCY);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expected);
    }

    @Test
    public void makeBidsShouldUseOnlyFirstResult() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("first", "first"),
                givenBidResult("second", "second"));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getCrid)
                .containsExactly("first");
    }

    @Test
    public void makeBidsShouldMatchImpFromOutgoingRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("auction")
                .imp(List.of(givenImp(identity()), givenImp(imp -> imp.id("456"))))
                .build();
        final BidRequest sentRequest = bidRequest.toBuilder()
                .imp(List.of(bidRequest.getImp().get(1)))
                .build();
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("ad", null, null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                sentRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(
                        BidderBid::getType,
                        bid -> bid.getBid().getImpid(),
                        bid -> bid.getBid().getId())
                .containsExactly(tuple(BidType.banner, "456", "auction-456"));
    }

    @Test
    public void makeBidsShouldNotReturnErrorWhenSentImpIsNotInOriginalRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidRequest sentRequest = givenBidRequest(imp -> imp.id("another-imp"));
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("ad", null, null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                sentRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldSupportWrappedNativeAssets() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode rawNative = mapper.createObjectNode();
        rawNative.set("native", givenNativeAd());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, null, null, rawNative));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bid -> {
                    final JsonNode nativeAd = jacksonMapper.decodeValue(
                            bid.getBid().getAdm(), JsonNode.class).path("native");
                    return tuple(bid.getType(), nativeAd.path("assets").size(), nativeAd.has("imptrackers"));
                })
                .containsExactly(tuple(BidType.xNative, 1, false));
    }

    @Test
    public void makeBidsShouldAppendBeaconWhenNativeImpressionTrackersAreNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode nativeAd = givenNativeAd().putNull("imptrackers");
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, TRACKER_URL, null, nativeAd));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bid -> jacksonMapper.decodeValue(bid.getBid().getAdm(), JsonNode.class)
                        .at("/native/imptrackers"))
                .containsExactly(mapper.valueToTree(List.of(TRACKER_URL)));
    }

    @Test
    public void makeBidsShouldAppendBeaconToExistingNativeImpressionTrackers() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode nativeAd = givenNativeAd();
        nativeAd.putArray("imptrackers").add("https://existing.tracker/imp");
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, TRACKER_URL, null, nativeAd));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bid -> jacksonMapper.decodeValue(bid.getBid().getAdm(), JsonNode.class)
                        .at("/native/imptrackers"))
                .containsExactly(mapper.valueToTree(List.of(
                        "https://existing.tracker/imp",
                        TRACKER_URL)));
    }

    @Test
    public void makeBidsShouldNotAppendEmptyNativeBeacon() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, "", null, givenNativeAd()));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bid -> jacksonMapper.decodeValue(bid.getBid().getAdm(), JsonNode.class)
                        .path("native").has("imptrackers"))
                .containsExactly(false);
    }

    @Test
    public void makeBidsShouldReturnErrorForNonStringNativeImpressionTracker() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode nativeAd = givenNativeAd();
        nativeAd.putArray("imptrackers").add(123);
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, TRACKER_URL, null, nativeAd));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getType, BidderError::getMessage)
                .containsExactly(tuple(BidderError.Type.bad_server_response, "Invalid native.imptrackers"));
    }

    @Test
    public void makeBidsShouldReturnNativeAdmWithBeaconTracker() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, TRACKER_URL, null, givenNativeAd()));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(
                        BidderBid::getType,
                        bid -> jacksonMapper.decodeValue(bid.getBid().getAdm(), JsonNode.class)
                                .at("/native/imptrackers"))
                .containsExactly(tuple(
                        BidType.xNative,
                        mapper.valueToTree(List.of(TRACKER_URL))));
    }

    @Test
    public void makeBidsShouldNotDuplicateNativeImpressionTracker() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode nativeAd = givenNativeAd();
        nativeAd.putArray("imptrackers").add(TRACKER_URL);
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, TRACKER_URL, null, givenNativeAd()));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bid -> jacksonMapper.decodeValue(bid.getBid().getAdm(), JsonNode.class)
                        .at("/native/imptrackers"))
                .containsExactly(mapper.valueToTree(List.of(TRACKER_URL)));
    }

    @Test
    public void makeBidsShouldReturnErrorForInvalidNativeImpressionTrackers() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode nativeAd = givenNativeAd().put("imptrackers", "invalid");
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, TRACKER_URL, null, nativeAd));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getType, BidderError::getMessage)
                .containsExactly(tuple(BidderError.Type.bad_server_response, "Invalid native imp trackers"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenNativeAssetsAreMissingAndBannerIsAbsent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).xNative(Native.builder().build()));
        final ObjectNode nativeAd = mapper.createObjectNode();
        nativeAd.set("assets", mapper.createArrayNode());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, null, null, nativeAd));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("no usable native assets in the response for imp 123 and the imp has no banner");
    }

    @Test
    public void makeBidsShouldReturnCorrectAdmIfBodyTagOfAdIsAbsent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("ad", "beacon", null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "ad"));
    }

    @Test
    public void makeBidsShouldReturnCorrectAdmIfBodyTagExistsInAd() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("ad<body>script</body>", "beacon", null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "scriptbeacon"));
    }

    @Test
    public void makeBidsShouldNotAppendBlankBeacon() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("ad<body>script</body>", "", null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "script"));
    }

    @Test
    public void makeBidsShouldFallbackToBannerWhenNativeIsAbsent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.xNative(Native.builder().build()));
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("banner-ad", null, null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "banner-ad"));
    }

    @Test
    public void makeBidsShouldFallbackToBannerWhenNativeIsNotAnObject() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.xNative(Native.builder().build()));
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("banner-ad", null, null, null, mapper.getNodeFactory().textNode("invalid")));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "banner-ad"));
    }

    @Test
    public void makeBidsShouldFallbackToBannerWhenNativeAssetsAreEmpty() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.xNative(Native.builder().build()));
        final ObjectNode nativeAd = mapper.createObjectNode();
        nativeAd.set("assets", mapper.createArrayNode());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("banner-ad", null, null, null, nativeAd));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "banner-ad"));
    }

    @Test
    public void makeBidsShouldEncodeVastForApvWithoutDoubleEncoding() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult(null, null, null, VAST_XML, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .singleElement()
                .asString()
                .contains(ENCODED_VAST_XML)
                .doesNotContain("%253C");
    }

    @Test
    public void makeBidsShouldUseAdgBrowserMForUpperBillboard() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(mapper.valueToTree(
                ExtPrebid.of(null, ExtImpAdgeneration.of("123", "10px")))));
        final AdgenerationResponse adgenerationResponse = AdgenerationResponse.of(
                "location",
                AdgenerationLocationParams.of(AdgenerationOption.of("upper_billboard")),
                List.of(givenBidResult(null, null, null, VAST_XML, null)));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, UPPER_BILLBOARD_ADM.formatted(ENCODED_VAST_XML, "10px")));
    }

    @Test
    public void makeBidsShouldUseDefaultMarginTopForUpperBillboard() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = AdgenerationResponse.of(
                "location",
                AdgenerationLocationParams.of(AdgenerationOption.of("upper_billboard")),
                List.of(givenBidResult(null, null, null, VAST_XML, null)));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, UPPER_BILLBOARD_ADM.formatted(ENCODED_VAST_XML, "0")));
    }

    @Test
    public void makeBidsShouldInsertBeaconLiterallyWhenItContainsDollarSign() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("<body>ad</body>", "$5", null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, bid -> bid.getBid().getAdm())
                .containsExactly(tuple(BidType.banner, "ad$5"));
    }

    @Test
    public void makeBidsShouldUseUsdCurrencyWhenRequestContainsUsd() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .cur(List.of(DEFAULT_CURRENCY, "USD"))
                .build();
        final AdgenerationResponse adgenerationResponse = givenBidResponse(
                givenBidResult("ad", null, null, null, null));
        final BidderCall<AdgenerationRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getBidCurrency)
                .isEqualTo("USD");
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder().imp(singletonList(givenImp(impCustomizer))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().id("banner_id").build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdgeneration.of("123", null)))))
                .build();
    }

    private static AdgenerationResponse givenBidResponse(AdgenerationResult... results) {
        return AdgenerationResponse.of("location", null, List.of(results));
    }

    private static AdgenerationResult givenBidResult(String ad, String creativeId) {
        return AdgenerationResult.of(
                ad, null, null, BigDecimal.ONE, creativeId, null,
                null, null, null, null, null, null, null, null);
    }

    private static AdgenerationResult givenBidResult(String ad,
                                                     String beacon,
                                                     String beaconUrl,
                                                     String vastXml,
                                                     JsonNode nativeAd) {

        return AdgenerationResult.of(
                ad, beacon, beaconUrl, BigDecimal.ONE, null, null,
                null, null, null, vastXml, null, null, null, nativeAd);
    }

    private static ObjectNode givenNativeAd() {
        final ObjectNode nativeAd = mapper.createObjectNode();
        nativeAd.putArray("assets").add(mapper.createObjectNode().put("id", 1));
        return nativeAd;
    }

    private static BidderCall<AdgenerationRequest> givenHttpCall(BidRequest bidRequest, String body) {
        final AdgenerationRequest request = AdgenerationRequest.builder()
                .ortb(bidRequest)
                .build();

        return BidderCall.succeededHttp(
                HttpRequest.<AdgenerationRequest>builder()
                        .body(jacksonMapper.encodeToBytes(request))
                        .payload(request)
                        .build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
