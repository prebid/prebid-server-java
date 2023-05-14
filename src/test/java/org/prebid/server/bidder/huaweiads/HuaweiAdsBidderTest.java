package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.request.CellInfo;
import org.prebid.server.bidder.huaweiads.model.request.HuaweiAdsRequest;
import org.prebid.server.bidder.huaweiads.model.request.Network;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.bidder.huaweiads.model.request.RussianSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataHuaweiAds;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class HuaweiAdsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://acd.op.hicloud.com/ppsadx/getResult";

    private HuaweiAdsBidder huaweiAdsBidder;

    private static final String CONVERT_PKG_NAME = "com.wavehk.android";
    private static final String CLOSE_SITE_SELECTION_BY_COUNTRY = "";

    private static final List<String> EXCEPTION_PKG_NAMES = emptyList();
    private static final List<String> UNCONVERTED_PKG_NAMES = emptyList();
    private static final List<String> UNCONVERTED_PKG_NAMES_PREFIXS = emptyList();
    private static final List<String> UNCONVERTED_PKG_NAME_KEYWORDS = emptyList();

    private static final List<PkgNameConvert> PKG_NAME_CONVERTS = singletonList(PkgNameConvert
            .builder()
            .convertedPkgName(CONVERT_PKG_NAME)
            .exceptionPkgNames(EXCEPTION_PKG_NAMES)
            .unconvertedPkgNames(UNCONVERTED_PKG_NAMES)
            .unconvertedPkgNamePrefixs(UNCONVERTED_PKG_NAMES_PREFIXS)
            .unconvertedPkgNameKeyWords(UNCONVERTED_PKG_NAME_KEYWORDS)
            .build());

    @Before
    public void setUp() {
        huaweiAdsBidder = new HuaweiAdsBidder(
                ENDPOINT_URL, PKG_NAME_CONVERTS, CLOSE_SITE_SELECTION_BY_COUNTRY, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp ->
                imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .allMatch(errorMessage -> errorMessage.startsWith("Unmarshalling error:"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSlotIdEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of("", "adtype", "publisherId", "signKey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ExtImpHuaweiAds: slotid is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdtypeEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of("slotid", "", "publisherId", "signKey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ExtImpHuaweiAds: adtype is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPublisherIdEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of("slotid", "adtype", "", "signKey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ExtHuaweiAds: publisherid is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSignKeyEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of("slotid", "adtype", "publisherId", "", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ExtHuaweiAds: signkey is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfKeyIdEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of("slotid", "adtype", "publisherId", "signkey", "", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ExtImpHuaweiAds: keyid is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasBannerAndAdtypeNative() {
        // given
        String adtype = "NATIVE";
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", adtype, "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("check openrtb format: request has banner, "
                        + "doesn't correspond to huawei adtype " + adtype));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasNativeAndAdtypeBanner() {
        // given
        String adtype = "BANNER";
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .xNative(Native.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", adtype, "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("check openrtb format: request has native,"
                        + " doesn't correspond to huawei adtype " + adtype));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasEmptyNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .xNative(Native.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", "native", "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("extract openrtb native failed: imp.Native.Request is empty"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasNotAnyType() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", "native", "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("check openrtb format: please choose one of our"
                        + " supported type banner, native, or video"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasIncorrectNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .xNative(Native.builder().request("{").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", "native", "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Error decoding native: Unexpected end-of-input:"
                        + " expected close marker for Object (start marker at [Source: "
                        + "(String)\"{\"; line: 1, column: 1])\n at [Source: (String)\"{\"; line: 1, column: 2]"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyResolveNative() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));
        String nativeRequest = "{\"context\":2,\"contextsubtype\":20,\"plcmttype\":1,\"plcmtcnt\":1,\"seq\":0,"
                + "\"aurlsupport\":0,\"durlsupport\":0,\"eventtrackers\":[{\"event\":1,\"methods\":[1,2]}],"
                + "\"privacy\":0,\"assets\":[{\"id\":100,\"title\":{\"len\":90},\"required\":1},"
                + "{\"id\":101,\"img\":{\"type\":3,\"wmin\":200,\"hmin\":200},\"required\":1},"
                + "{\"id\":107,\"video\":{\"mimes\":[\"mp4\"],\"minduration\":100,\"maxduration\":100,"
                + "\"protocols\":[1,2]},\"required\":1},{\"id\":105,\"data\":{\"type\":2,\"len\":90},\"required\":1}],"
                + "\"ver\":\"1.2\"}";

        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                        .banner(null)
                        .xNative(Native.builder().request(nativeRequest).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of(
                                        "slotid", "native", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        String expectedAdslot30 = "{\"version\":\"3.4\",\"multislot\":[{\"slotid\":\"slotid\",\"adtype\":3,\"test\":0,"
                + "\"w\":200,\"h\":200,\"detailed_creative_type_list\":[\"903\"]}],\"app\":{\"country\":\"ZA\"},"
                + "\"device\":{\"imei\":\"123\"}}\"";

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .containsExactly(Native.builder().request(nativeRequest).build());
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readTree(httpRequest.getBody()))
                .containsOnly(mapper.readTree(expectedAdslot30));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasVideoAndAdtypeAudio() {
        // given
        String adtype = "AUDIO";
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", adtype, "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("check openrtb format: request has video, "
                        + "doesn't correspond to huawei adtype " + adtype));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasAudio() {
        // given
        String adtype = "AUDIO";
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .audio(Audio.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", adtype, "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("check openrtb format:"
                        + " request has audio, not currently supported"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasVideoAndAdtypeRollAndMaxDurationNotSet() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .banner(null)
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of(
                                "slotid", "ROLL", "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Extract openrtb video failed:"
                        + " MaxDuration is empty when huaweiads adtype is roll."));
    }

    @Test
    public void makeHttpRequestsShouldReturnCorrectRequestForVideo() {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(HuaweiAdsConstants.ASIAN_SITE_ENDPOINT);
            assertThat(httpRequest.getHeaders())
                    .extracting(Map.Entry::getKey, Map.Entry::getValue)
                    .contains(
                            tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                            tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                            tuple(HttpUtil.AUTHORIZATION_HEADER.toString(),
                                    httpRequest.getHeaders().get(HttpUtil.AUTHORIZATION_HEADER)));
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfUserExtAndDeviceGaidIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("getDeviceID: openRTBRequest.User.Ext is null"
                        + " and device.Gaid is not specified."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImeiOaidGaidIsNotPresent() {
        // given
        List<String> imei = emptyList();
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder()
                                        .data(extUserData)
                                        .build())
                                .build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("getDeviceID: Imei, Oaid, Gaid are all empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAppBundleEmpty() {
        // given
        List<String> imei = emptyList();
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build())
                        .app(App.builder().build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput(
                        "generate HuaweiAds AppInfo failed: openrtb BidRequest.App.Bundle is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedAppInfo() {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));
        App app = App.builder()
                .name("Huawei Browser")
                .bundle("com.wavehk.android")
                .ver("9.1.0.301")
                .content(Content.builder()
                        .language("ua")
                        .build())
                .build();

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build())
                        .app(app));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        org.prebid.server.bidder.huaweiads.model.request.App expectedApp =
                org.prebid.server.bidder.huaweiads.model.request.App.builder()
                        .country("ZA")
                        .name("Huawei Browser")
                        .pkgname("com.wavehk.android")
                        .version("9.1.0.301")
                        .lang("ua")
                        .build();

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), HuaweiAdsRequest.class))
                .flatExtracting(HuaweiAdsRequest::getApp)
                .containsExactly(expectedApp);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedDeviceRegsNetworkInfo() {
        // given
        List<String> imei = emptyList();
        List<String> oaid = singletonList("oaid");
        List<String> gaid = singletonList("gaid");
        List<String> clientTime = singletonList("2018-08-10 20:01:11.214+0200");
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));
        Device device = Device.builder()
                .ua("useragent")
                .h(1920)
                .language("en")
                .model("COL-TEST")
                .os("android")
                .osv("10.0.0")
                .devicetype(4)
                .make("huawei")
                .w(1080)
                .ip("ip")
                .pxratio(BigDecimal.valueOf(23.01))
                .geo(Geo.builder().country("ATA").build())
                .dnt(1)
                .mccmnc("655-1")
                .carrier("carrier")
                .connectiontype(1)
                .build();

        Regs regs = Regs.builder()
                .coppa(1)
                .build();

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .data(extUserData)
                                        .consent("CPaYLJBPaYLJBIPAAAENCSCgAPAAAAAAAAAAGsQAQGsAAAAA.YAAAAAAAAAA")
                                        .build())
                                .build())
                        .device(device)
                        .regs(regs));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        org.prebid.server.bidder.huaweiads.model.request.Device expectedDevice =
                org.prebid.server.bidder.huaweiads.model.request.Device.builder()
                        .height(1920)
                        .language("en")
                        .oaid("oaid")
                        .os("android")
                        .type(4)
                        .ip("ip")
                        .localeCountry("AT")
                        .pxratio(BigDecimal.valueOf(23.01))
                        .width(1080)
                        .model("COL-TEST")
                        .clientTime("2018-08-10 20:01:11.214+0200")
                        .gaid("gaid")
                        .useragent("useragent")
                        .version("10.0.0")
                        .maker("huawei")
                        .belongCountry("AT")
                        .gaidTrackingEnabled("0")
                        .isTrackingEnabled("0")
                        .build();

        Network expectedNetwork = Network.builder()
                .carrier(99)
                .cellInfo(singletonList(CellInfo.of("655", "1")))
                .type(1)
                .build();

        org.prebid.server.bidder.huaweiads.model.request.Regs expectedRegs =
                org.prebid.server.bidder.huaweiads.model.request.Regs.of(1);

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), HuaweiAdsRequest.class))
                .flatExtracting(HuaweiAdsRequest::getDevice)
                .containsExactly(expectedDevice);

        assertThat(result.getValue())
                .hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), HuaweiAdsRequest.class))
                .flatExtracting(HuaweiAdsRequest::getNetwork)
                .containsExactly(expectedNetwork);

        assertThat(result.getValue())
                .hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), HuaweiAdsRequest.class))
                .flatExtracting(HuaweiAdsRequest::getRegs)
                .containsExactly(expectedRegs);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHttpRequestForAsianByDefault() {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(HuaweiAdsConstants.ASIAN_SITE_ENDPOINT);
            assertThat(httpRequest.getHeaders())
                    .extracting(Map.Entry::getKey, Map.Entry::getValue)
                    .contains(
                            tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                            tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                            tuple(HttpUtil.AUTHORIZATION_HEADER.toString(),
                                    httpRequest.getHeaders().get(HttpUtil.AUTHORIZATION_HEADER)));
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHttpRequestForEuropean() {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();
        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .device(Device.builder()
                                .geo(Geo.builder().lat(1f).lon(1f).accuracy(1).lastfix(1).country("POL").build())
                                .build())
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(HuaweiAdsConstants.EUROPEAN_SITE_ENDPOINT);
            assertThat(httpRequest.getHeaders())
                    .extracting(Map.Entry::getKey, Map.Entry::getValue)
                    .contains(
                            tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                            tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                            tuple(HttpUtil.AUTHORIZATION_HEADER.toString(),
                                    httpRequest.getHeaders().get(HttpUtil.AUTHORIZATION_HEADER)));
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnAsianUrlIfRuPresentInRequest() {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .device(Device.builder().geo(
                                        Geo.builder()
                                                .lat(1f)
                                                .lon(1f)
                                                .accuracy(1)
                                                .lastfix(1)
                                                .country(RussianSiteCountryCode.RU.name())
                                                .build())
                                .build())
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(HuaweiAdsConstants.ASIAN_SITE_ENDPOINT);
            assertThat(httpRequest.getHeaders())
                    .extracting(Map.Entry::getKey, Map.Entry::getValue)
                    .contains(
                            tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                            tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                            tuple(HttpUtil.AUTHORIZATION_HEADER.toString(),
                                    httpRequest.getHeaders().get(HttpUtil.AUTHORIZATION_HEADER)));
        });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("Unable to parse server response"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseisEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return impModifier.apply(Imp.builder()
                .banner(Banner.builder().h(150)
                        .w(300)
                        .format(singletonList(Format.builder().h(100).w(100).build())).build())).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return givenBidRequest(impModifier, identity());
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impModifier,
            UnaryOperator<BidRequest.BidRequestBuilder> requestModifier) {

        return requestModifier.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impModifier))))
                .build();
    }

    private ObjectNode createExtUserData(ExtUserDataDeviceIdHuaweiAds extUserDataDeviceIdHuaweiAds) {
        return jacksonMapper.mapper().createObjectNode()
                .putPOJO("data", ExtUserDataHuaweiAds.of(null))
                .putPOJO("data", extUserDataDeviceIdHuaweiAds);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
