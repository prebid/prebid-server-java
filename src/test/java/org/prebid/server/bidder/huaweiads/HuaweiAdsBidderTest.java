package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.bidder.huaweiads.model.request.RussianSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataHuaweiAds;
import org.prebid.server.util.HttpUtil;

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

    private static final String CONVERT_PKG_NAME = "";
    private static final String CLOSE_SITE_SELECTION_BY_COUNTRY = "";

    private static final List<String> EXCEPTION_PKG_NAMES = List.of();
    private static final List<String> UNCONVERTED_PKG_NAMES = List.of();
    private static final List<String> UNCONVERTED_PKG_NAMES_PREFIXS = List.of();

    private static final List<PkgNameConvert> PKG_NAME_CONVERTS = List.of(PkgNameConvert
            .builder()
            .convertedPkgName(CONVERT_PKG_NAME)
            .exceptionPkgNames(EXCEPTION_PKG_NAMES)
            .unconvertedPkgNames(UNCONVERTED_PKG_NAMES)
            .unconvertedPkgNamePrefixs(UNCONVERTED_PKG_NAMES_PREFIXS)
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
        List<String> imei = List.of("123");
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
    public void makeHttpRequestsShouldReturnExpectedHttpRequestForAsianByDefault() {
        // given
        List<String> imei = List.of("123");
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
        List<String> imei = List.of("123");
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
        List<String> imei = List.of("123");
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

}
