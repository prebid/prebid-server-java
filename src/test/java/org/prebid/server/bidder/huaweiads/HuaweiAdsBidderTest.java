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
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.bidder.huaweiads.model.response.Ad30;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdsResponse;
import org.prebid.server.bidder.huaweiads.model.response.Icon;
import org.prebid.server.bidder.huaweiads.model.response.ImageInfo;
import org.prebid.server.bidder.huaweiads.model.response.MediaFile;
import org.prebid.server.bidder.huaweiads.model.response.MetaData;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
import org.prebid.server.bidder.huaweiads.model.response.VideoInfo;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;
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
    private static final String CHINESE_ENDPOINT_URL = "https://acd.op.hicloud.com/ppsadx/getResult";
    private static final String RUSSIAN_ENDPOINT_URL = "https://adx-drru.op.hicloud.com/ppsadx/getResult";
    private static final String EUROPEAN_ENDPOINT_URL = "https://adx-dre.op.hicloud.com/ppsadx/getResult";
    private static final String ASIAN_ENDPOINT_URL = "https://adx-dra.op.hicloud.com/ppsadx/getResult";

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
                ENDPOINT_URL, PKG_NAME_CONVERTS, CLOSE_SITE_SELECTION_BY_COUNTRY,
                CHINESE_ENDPOINT_URL, RUSSIAN_ENDPOINT_URL, EUROPEAN_ENDPOINT_URL, ASIAN_ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp ->
                imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        String expectedAdslot30 = "{\"version\":\"3.4\",\"multislot\":[{\"slotid\":\"slotid\",\"adtype\":3,\"test\":0,"
                + "\"w\":200,\"h\":200,\"detailed_creative_type_list\":[\"903\"]}],\"app\":{\"country\":\"ZA\"},"
                + "\"device\":{\"imei\":\"123\"}}\"";

        // then
        assertThat(result.getErrors()).isEmpty();
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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(ASIAN_ENDPOINT_URL);
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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(ASIAN_ENDPOINT_URL);
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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(EUROPEAN_ENDPOINT_URL);
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
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(ASIAN_ENDPOINT_URL);
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
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(null, "invalid");

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
    public void makeBidsShouldReturnErrorIfErrorRetcode() throws JsonProcessingException {
        // given
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(400)
                        .reason("test")
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("HuaweiAdsResponse retcode: 400, reason: test"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseisEmpty() throws JsonProcessingException {
        // given
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMultiadEmpty() throws JsonProcessingException {
        // given
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("convert huaweiads response to bidder response failed:"
                + " multiad length is 0, get no ads from huawei side."));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpEmpty() throws JsonProcessingException {
        // given

        final BidRequest bidRequest = BidRequest.builder().build();

        Ad30 ad30 = givenAd30(identity());
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("convert huaweiads response to bidder response failed: "
                + "openRTBRequest.imp is null"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpHasBannerAdtypeSplash() throws JsonProcessingException {
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

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier.adType(1));
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("openrtb banner should correspond to huaweiads adtype:"
                + " banner or interstitial"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpHasAudio() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .audio(Audio.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "banner", "publisherId", "signkey", "keyId", "false")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier.adType(1));
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("no support bidtype: audio"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImageInfoEmpty() throws JsonProcessingException {
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

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier.contentList(singletonList(
                org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .metaData(MetaData.builder().build())
                        .creativeType(1)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("content.MetaData.ImageInfo is empty"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedBannerAdmPicture() throws JsonProcessingException {
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

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier.contentList(
                singletonList(org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .contentId("58025103")
                        .price(2.8)
                        .monitorList(singletonList(Monitor.of(
                                "click",
                                emptyList())))
                        .metaData(MetaData.builder()
                                .imageInfoList(singletonList(ImageInfo.builder()
                                        .url("https://ads.huawei.com/usermgtportal/home/img/huawei_logo_black.aaec817d.svg")
                                        .width(300)
                                        .height(250)
                                        .build()))
                                .clickUrl("https://ads.huawei.com/usermgtportal/home/index.html#/")
                                .build())
                        .creativeType(1)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        Bid expectedBid = Bid.builder()
                .id("impId")
                .impid("impId")
                .price(BigDecimal.valueOf(2.8))
                .crid("58025103")
                .w(300)
                .h(250)
                .adm("<style> html, body  { margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  html  { display: table; }  body { display: table-cell; vertical-align: middle; text-align: center; -webkit-text-size-adjust: none; }  </style> <span class=\"title-link advertiser_label\"></span> <a href='https://ads.huawei.com/usermgtportal/home/index.html#/' style=\"text-decoration:none\" onclick=sendGetReq()> <img src='https://ads.huawei.com/usermgtportal/home/img/huawei_logo_black.aaec817d.svg' width='300' height='250'/> </a> <script type=\"text/javascript\">var dspClickTrackings = [];function sendGetReq() {sendSomeGetReq(dspClickTrackings)}function sendOneGetReq(url) {var req = new XMLHttpRequest();req.open('GET', url, true);req.send(null);}function sendSomeGetReq(urls) {for (var i = 0; i < urls.length; i++) {sendOneGetReq(urls[i]);}}</script>")
                .adomain(singletonList("huaweiads"))
                .build();
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(expectedBid);
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnExpectedBannerAdmVideo() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .video(Video.builder()
                                .mimes(singletonList("video/mp4"))
                                .w(300)
                                .h(250)
                                .maxduration(10)
                                .placement(2)
                                .linearity(1)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "roll", "publisherId", "signkey", "keyId", "true")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier
                .adType(60)
                .contentList(singletonList(org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .contentId("58001445")
                        .price(2.8)
                        .monitorList(List.of(Monitor.of("vastError", List.of("http://test/vastError")),
                                Monitor.of("click", List.of("http://test/click", "http://test/dspclick")),
                                Monitor.of("imp", List.of("http://test/imp", "http://test/dspimp")),
                                Monitor.of("userclose", List.of("http://test/userclose")),
                                Monitor.of("playStart", List.of("http://test/playStart")),
                                Monitor.of("playEnd", List.of("http://test/playEnd1", "http://test/playEnd2")),
                                Monitor.of("playResume", List.of("http://test/playResume")),
                                Monitor.of("playPause", List.of("http://test/playPause")),
                                Monitor.of("appOpen", List.of("http://test/appOpen"))))
                        .metaData(MetaData.builder()
                                .imageInfoList(singletonList(ImageInfo.builder()
                                        .url("https://test.png")
                                        .width(300)
                                        .height(1280)
                                        .build()))
                                .title("%2Ftest%2F")
                                .clickUrl("https://ads.huawei.com/usermgtportal/home/index.html#/")
                                .intent("https%3A%2F%2Fhibobi.app.link%2FK1sog7A40hb")
                                .mediaFile(MediaFile.of("video/mp4", 720, 1280, 10000L, "https://test.png", ""))
                                .duration(6038L)
                                .videoInfo(VideoInfo.builder()
                                        .videoDownloadUrl("https://consumer.huawei.com/content/dam/huawei-cbg-site/ecommerce/ae/2022/may/watch-gt-3-pro/subscribe-phase/video/update/MKT_Odin_Frigga_PV_EN_30s%20Horizontal%20SHM.mp4")
                                        .height(500)
                                        .width(600)
                                        .videoRatio(BigDecimal.valueOf(0.5625))
                                        .videoDuration(6038L)
                                        .videoFileSize(949951)
                                        .sha256("aa08c8ffce82bbcd37cabefd6c8972b407de48f0b4e332e06d4cc18d25377d77")
                                        .build())
                                .build())
                        .creativeType(6)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        Bid expectedBid = Bid.builder()
                .id("impId")
                .impid("impId")
                .price(BigDecimal.valueOf(2.8))
                .crid("58001445")
                .w(720)
                .h(1280)
                .adm("<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"><Ad id=\"58001445\"><InLine><AdSystem>HuaweiAds</AdSystem><AdTitle>/test/</AdTitle><Error><![CDATA[http://test/vastError&et=[ERRORCODE]]]></Error><Impression><![CDATA[http://test/imp]]></Impression><Impression><![CDATA[http://test/dspimp]]></Impression><Creatives><Creative adId=\"58001445\" id=\"58001445\"><Linear><Duration>00:00:06.038</Duration><TrackingEvents><Tracking event=\"skip\"><![CDATA[http://test/userclose]]></Tracking><Tracking event=\"closeLinear\"><![CDATA[http://test/userclose]]></Tracking><Tracking event=\"start\"><![CDATA[http://test/playStart]]></Tracking><Tracking event=\"complete\"><![CDATA[http://test/playEnd1]]></Tracking><Tracking event=\"complete\"><![CDATA[http://test/playEnd2]]></Tracking><Tracking event=\"resume\"><![CDATA[http://test/playResume]]></Tracking><Tracking event=\"pause\"><![CDATA[http://test/playPause]]></Tracking></TrackingEvents><VideoClicks><ClickThrough><![CDATA[https://hibobi.app.link/K1sog7A40hb]]></ClickThrough><ClickTracking><![CDATA[http://test/click]]></ClickTracking><ClickTracking><![CDATA[http://test/dspclick]]></ClickTracking></VideoClicks><MediaFiles><MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"720\" height=\"1280\" scalable=\"true\" maintainAspectRatio=\"true\"> <![CDATA[https://test.png]]></MediaFile></MediaFiles></Linear></Creative></Creatives></InLine></Ad></VAST>")
                .adomain(singletonList("huaweiads"))
                .build();
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(expectedBid);
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnExpectedBannerAdmRewardedVideoIcon() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .video(Video.builder()
                                .mimes(singletonList("video/mp4"))
                                .w(720)
                                .h(1280)
                                .maxduration(10)
                                .placement(2)
                                .linearity(1)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "roll", "publisherId", "signkey", "keyId", "true")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier
                .adType(7)
                .contentList(singletonList(org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .contentId("58001445")
                        .price(2.8)
                        .monitorList(List.of(Monitor.of("vastError", List.of("http://test/vastError")),
                                Monitor.of("click", List.of("http://test/click", "http://test/dspclick")),
                                Monitor.of("imp", List.of("http://test/imp", "http://test/dspimp")),
                                Monitor.of("userclose", List.of("http://test/userclose")),
                                Monitor.of("playStart", List.of("http://test/playStart")),
                                Monitor.of("playEnd", List.of("http://test/playEnd1")),
                                Monitor.of("playResume", List.of("http://test/playResume")),
                                Monitor.of("playPause", List.of("http://test/playPause")),
                                Monitor.of("appOpen", List.of("http://test/appOpen"))))
                        .metaData(MetaData.builder()
                                .imageInfoList(singletonList(ImageInfo.builder()
                                        .url("https://test.png")
                                        .width(300)
                                        .height(1280)
                                        .build()))
                                .title("%2Ftest%2F")
                                .iconList(singletonList(Icon.builder()
                                        .fileSize(10797L)
                                        .height(160)
                                        .imageType("img")
                                        .sha256("042479eccbda9a8d7d3aa3da73c42486854407835623a30ffff875cb578242d0")
                                        .url("https://appimg.dbankcdn.com/application/icon144/678727f6687b4382ade1fa4cfc77e165.png")
                                        .width(160)
                                        .build()))
                                .clickUrl("https://ads.huawei.com/usermgtportal/home/index.html#/")
                                .mediaFile(MediaFile.of("video/mp4", 720, 1280, 10000L, "https://test.png", ""))
                                .duration(6038L)
                                .videoInfo(VideoInfo.builder()
                                        .videoDownloadUrl("https://consumer.huawei.com/content/dam/huawei-cbg-site/ecommerce/ae/2022/may/watch-gt-3-pro/subscribe-phase/video/update/MKT_Odin_Frigga_PV_EN_30s%20Horizontal%20SHM.mp4")
                                        .height(500)
                                        .width(600)
                                        .videoRatio(BigDecimal.valueOf(0.5625))
                                        .videoDuration(6038L)
                                        .videoFileSize(949951)
                                        .sha256("aa08c8ffce82bbcd37cabefd6c8972b407de48f0b4e332e06d4cc18d25377d77")
                                        .build())
                                .build())
                        .creativeType(6)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        Bid expectedBid = Bid.builder()
                .id("impId")
                .impid("impId")
                .price(BigDecimal.valueOf(2.8))
                .crid("58001445")
                .w(720)
                .h(1280)
                .adm("<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"><Ad id=\"58001445\"><InLine><AdSystem>HuaweiAds</AdSystem><AdTitle>/test/</AdTitle><Error><![CDATA[http://test/vastError&et=[ERRORCODE]]]></Error><Impression><![CDATA[http://test/imp]]></Impression><Impression><![CDATA[http://test/dspimp]]></Impression><Creatives><Creative adId=\"58001445\" id=\"58001445\"><Linear><Duration>00:00:06.038</Duration><TrackingEvents><Tracking event=\"skip\"><![CDATA[http://test/userclose]]></Tracking><Tracking event=\"closeLinear\"><![CDATA[http://test/userclose]]></Tracking><Tracking event=\"start\"><![CDATA[http://test/playStart]]></Tracking><Tracking event=\"complete\"><![CDATA[http://test/playEnd1]]></Tracking><Tracking event=\"resume\"><![CDATA[http://test/playResume]]></Tracking><Tracking event=\"pause\"><![CDATA[http://test/playPause]]></Tracking></TrackingEvents><VideoClicks><ClickThrough><![CDATA[https://ads.huawei.com/usermgtportal/home/index.html#/]]></ClickThrough><ClickTracking><![CDATA[http://test/click]]></ClickTracking><ClickTracking><![CDATA[http://test/dspclick]]></ClickTracking></VideoClicks><MediaFiles><MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"600\" height=\"500\" scalable=\"true\" maintainAspectRatio=\"true\"> <![CDATA[https://consumer.huawei.com/content/dam/huawei-cbg-site/ecommerce/ae/2022/may/watch-gt-3-pro/subscribe-phase/video/update/MKT_Odin_Frigga_PV_EN_30s%20Horizontal%20SHM.mp4]]></MediaFile></MediaFiles></Linear></Creative><Creative adId=\"58001445\" id=\"58001445\"><CompanionAds><Companion width=\"160\" height=\"160\"><StaticResource creativeType=\"image/png\"><![CDATA[https://appimg.dbankcdn.com/application/icon144/678727f6687b4382ade1fa4cfc77e165.png]]></StaticResource><CompanionClickThrough><![CDATA[https://ads.huawei.com/usermgtportal/home/index.html#/]]></CompanionClickThrough></Companion></CompanionAds></Creative></Creatives></InLine></Ad></VAST>")
                .adomain(singletonList("huaweiads"))
                .build();
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(expectedBid);
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnExpectedBannerAdmRewardedVideoImageInfo() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .video(Video.builder()
                                .mimes(singletonList("video/mp4"))
                                .w(720)
                                .h(1280)
                                .maxduration(10)
                                .placement(2)
                                .linearity(1)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "roll", "publisherId", "signkey", "keyId", "true")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier
                .adType(7)
                .contentList(singletonList(org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .contentId("58001445")
                        .price(2.8)
                        .monitorList(List.of(Monitor.of("vastError", List.of("http://test/vastError")),
                                Monitor.of("click", List.of("http://test/click", "http://test/dspclick")),
                                Monitor.of("imp", List.of("http://test/imp", "http://test/dspimp")),
                                Monitor.of("userclose", List.of("http://test/userclose")),
                                Monitor.of("playStart", List.of("http://test/playStart")),
                                Monitor.of("playEnd", List.of("http://test/playEnd1")),
                                Monitor.of("playResume", List.of("http://test/playResume")),
                                Monitor.of("playPause", List.of("http://test/playPause")),
                                Monitor.of("appOpen", List.of("http://test/appOpen"))))
                        .metaData(MetaData.builder()
                                .imageInfoList(singletonList(ImageInfo.builder()
                                        .url("http://image1.jpg")
                                        .width(400)
                                        .height(350)
                                        .build()))
                                .title("%2Ftest%2F")
                                .clickUrl("https://ads.huawei.com/usermgtportal/home/index.html#/")
                                .mediaFile(MediaFile.of("video/mp4", 720, 1280, 10000L, "https://test.png", ""))
                                .duration(6038L)
                                .videoInfo(VideoInfo.builder()
                                        .videoDownloadUrl("https://consumer.huawei.com/content/dam/huawei-cbg-site/ecommerce/ae/2022/may/watch-gt-3-pro/subscribe-phase/video/update/MKT_Odin_Frigga_PV_EN_30s%20Horizontal%20SHM.mp4")
                                        .height(500)
                                        .width(600)
                                        .videoRatio(BigDecimal.valueOf(0.5625))
                                        .videoDuration(6038L)
                                        .videoFileSize(949951)
                                        .sha256("aa08c8ffce82bbcd37cabefd6c8972b407de48f0b4e332e06d4cc18d25377d77")
                                        .build())
                                .build())
                        .creativeType(6)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        Bid expectedBid = Bid.builder()
                .id("impId")
                .impid("impId")
                .price(BigDecimal.valueOf(2.8))
                .crid("58001445")
                .w(720)
                .h(1280)
                .adm("<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"><Ad id=\"58001445\"><InLine><AdSystem>HuaweiAds</AdSystem><AdTitle>/test/</AdTitle><Error><![CDATA[http://test/vastError&et=[ERRORCODE]]]></Error><Impression><![CDATA[http://test/imp]]></Impression><Impression><![CDATA[http://test/dspimp]]></Impression><Creatives><Creative adId=\"58001445\" id=\"58001445\"><Linear><Duration>00:00:06.038</Duration><TrackingEvents><Tracking event=\"skip\"><![CDATA[http://test/userclose]]></Tracking><Tracking event=\"closeLinear\"><![CDATA[http://test/userclose]]></Tracking><Tracking event=\"start\"><![CDATA[http://test/playStart]]></Tracking><Tracking event=\"complete\"><![CDATA[http://test/playEnd1]]></Tracking><Tracking event=\"resume\"><![CDATA[http://test/playResume]]></Tracking><Tracking event=\"pause\"><![CDATA[http://test/playPause]]></Tracking></TrackingEvents><VideoClicks><ClickThrough><![CDATA[https://ads.huawei.com/usermgtportal/home/index.html#/]]></ClickThrough><ClickTracking><![CDATA[http://test/click]]></ClickTracking><ClickTracking><![CDATA[http://test/dspclick]]></ClickTracking></VideoClicks><MediaFiles><MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"600\" height=\"500\" scalable=\"true\" maintainAspectRatio=\"true\"> <![CDATA[https://consumer.huawei.com/content/dam/huawei-cbg-site/ecommerce/ae/2022/may/watch-gt-3-pro/subscribe-phase/video/update/MKT_Odin_Frigga_PV_EN_30s%20Horizontal%20SHM.mp4]]></MediaFile></MediaFiles></Linear></Creative><Creative adId=\"58001445\" id=\"58001445\"><CompanionAds><Companion width=\"400\" height=\"350\"><StaticResource creativeType=\"image/png\"><![CDATA[http://image1.jpg]]></StaticResource><CompanionClickThrough><![CDATA[https://ads.huawei.com/usermgtportal/home/index.html#/]]></CompanionClickThrough></Companion></CompanionAds></Creative></Creatives></InLine></Ad></VAST>")
                .adomain(singletonList("huaweiads"))
                .build();
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(expectedBid);
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnErrorIfAdtypeNotNative() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .xNative(Native.builder()
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "roll", "publisherId", "signkey", "keyId", "true")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier.contentList(singletonList(
                org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .metaData(MetaData.builder().build())
                        .creativeType(1)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("extract Adm for Native ad: huaweiads response is not a native ad"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNativeRequestEmpty() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .xNative(Native.builder()
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "roll", "publisherId", "signkey", "keyId", "true")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier.contentList(singletonList(
                        org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                                .metaData(MetaData.builder().build())
                                .creativeType(1)
                                .build()))
                .adType(3));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("extract Adm for Native ad: imp.Native.Request is empty"));
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test // TODO add label field
    public void makeBidsShouldReturnExpectedNative() throws JsonProcessingException {
        // given
        List<String> imei = singletonList("123");
        List<String> oaid = emptyList();
        List<String> gaid = emptyList();
        List<String> clientTime = emptyList();

        ObjectNode extUserData = createExtUserData(ExtUserDataDeviceIdHuaweiAds.of(imei, oaid, gaid, clientTime));

        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer
                        .banner(null)
                        .xNative(Native.builder()
                                .request("{\"context\":2,\"contextsubtype\":20,\"plcmttype\":1,\"plcmtcnt\":1,"
                                        + "\"seq\":0,\"aurlsupport\":0,\"durlsupport\":0,\"eventtrackers\":"
                                        + "[{\"event\":1,\"methods\":[1,2]}],\"privacy\":0,\"assets\":[{\"id\":100,"
                                        + "\"title\":{\"len\":90},\"required\":1},{\"id\":103,\"img\":{\"type\":3,"
                                        + "\"wmin\":200,\"hmin\":200},\"required\":1},{\"id\":105,\"data\":{\"type\":2,"
                                        + "\"len\":90},\"required\":1}],\"ver\":\"1.2\"}")
                                .ver("1.2")
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpHuaweiAds.of("slotid", "roll", "publisherId", "signkey", "keyId", "true")))),
                request -> request
                        .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build()));

        Ad30 ad30 = givenAd30(ad30Modifier -> ad30Modifier
                .adType(3)
                .contentList(singletonList(org.prebid.server.bidder.huaweiads.model.response.Content.builder()
                        .contentId("58022259")
                        .price(2.8)
                        .monitorList(List.of(Monitor.of("vastError", List.of("http://test/vastError")),
                                Monitor.of("click", List.of("http://test/click")),
                                Monitor.of("imp", List.of("http://test/imp")),
                                Monitor.of("userclose", List.of("http://test/userclose")),
                                Monitor.of("playStart", List.of("http://test/playStart")),
                                Monitor.of("playEnd", List.of("http://test/playEnd1")),
                                Monitor.of("playResume", List.of("http://test/playResume")),
                                Monitor.of("playPause", List.of("http://test/playPause")),
                                Monitor.of("appOpen", List.of("http://test/appOpen"))))
                        .metaData(MetaData.builder()
                                .imageInfoList(singletonList(ImageInfo.builder()
                                        .url("http://image.jpg")
                                        .width(720)
                                        .height(1280)
                                        .build()))
                                .title("%2Ftest%2F")
                                .clickUrl("https://ads.huawei.com/usermgtportal/home/index.html#/")
                                .mediaFile(MediaFile.of("video/mp4", 720, 1280, 10000L, "https://test.png", ""))
                                .duration(6038L)
                                .videoInfo(VideoInfo.builder()
                                        .videoDownloadUrl("https://consumer.huawei.com/content/dam/huawei-cbg-site/ecommerce/ae/2022/may/watch-gt-3-pro/subscribe-phase/video/update/MKT_Odin_Frigga_PV_EN_30s%20Horizontal%20SHM.mp4")
                                        .height(500)
                                        .width(600)
                                        .videoRatio(BigDecimal.valueOf(0.5625))
                                        .videoDuration(6038L)
                                        .videoFileSize(949951)
                                        .sha256("aa08c8ffce82bbcd37cabefd6c8972b407de48f0b4e332e06d4cc18d25377d77")
                                        .build())
                                .build())
                        .creativeType(6)
                        .build())));

        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(HuaweiAdsRequest.builder().build(),
                mapper.writeValueAsString(HuaweiAdsResponse.builder()
                        .retcode(200)
                        .multiad(singletonList(ad30))
                        .build()));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, bidRequest);

        Bid expectedBid = Bid.builder()
                .id("impId")
                .impid("impId")
                .price(BigDecimal.valueOf(2.8))
                .crid("58022259")
                .w(720)
                .h(1280)
                .adm("{\"ver\":\"1.2\",\"assets\":[{\"id\":100,\"title\":{\"text\":\"/test/\",\"len\":6}},{\"id\":103,\"img\":{\"type\":3,\"url\":\"http://image.jpg\",\"w\":720,\"h\":1280}},{\"id\":105,\"data\":{\"value\":\"\"}}],\"link\":{\"url\":\"https://ads.huawei.com/usermgtportal/home/index.html#/\",\"clicktrackers\":[\"http://test/click\"]},\"imptrackers\":[\"http://test/imp\"]}")
                .adomain(singletonList("huaweiads"))
                .build();
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(expectedBid);
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return impModifier.apply(Imp.builder()
                .id("impId")
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
                .putPOJO("gaid", extUserDataDeviceIdHuaweiAds.getGaid())
                .putPOJO("oaid", extUserDataDeviceIdHuaweiAds.getOaid())
                .putPOJO("imei", extUserDataDeviceIdHuaweiAds.getImei())
                .putPOJO("clientTime", extUserDataDeviceIdHuaweiAds.getClientTime());
    }

    private static BidderCall<HuaweiAdsRequest> givenHttpCall(HuaweiAdsRequest huaweiAdsRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<HuaweiAdsRequest>builder().payload(huaweiAdsRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static Ad30 givenAd30(UnaryOperator<Ad30.Ad30Builder> ad30Modifier) {
        return ad30Modifier.apply(Ad30.builder()
                .adType(8)
                .slotId("slotid")
                .retCode30(200)
                .contentList(singletonList(
                        org.prebid.server.bidder.huaweiads.model.response.Content.builder().build()))).build();
    }

}
