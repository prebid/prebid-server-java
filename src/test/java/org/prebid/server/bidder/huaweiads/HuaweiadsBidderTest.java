package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.*;

import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.*;

import org.prebid.server.bidder.huaweiads.model.HuaweiContent;
import org.prebid.server.bidder.huaweiads.model.HuaweiFormat;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adocean.ExtImpAdocean;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.tuple;

public class HuaweiadsBidderTest extends VertxTest {
    private static final String ENDPOINT_URL = "https://huaweiads.com/adxtest/";

    private HuaweiAdsBidder huaweiAdsBidder;

    @Before
    public void setUp() {
        huaweiAdsBidder = new HuaweiAdsBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new HuaweiAdsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .device(Device.builder().ua("someUa").build())
                        .user(User.builder()
                                .ext(givenUserExt()).build()));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "someUa"));
        assertThat(result.getErrors()).isEmpty();
        //do i need to check authorization ?
    }
/*
    @Test
    public void makeHttpRequestsShouldFailIfMissingImpSlotId() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.slotId(null))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ExtImpHuaweiAds: slotId is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpAdType() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.adtype(null))));
        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ExtImpHuaweiAds: adType is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpPublisherId() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.publisherId(null))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ExtImpHuaweiAds: publisherId is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpKeyId() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.keyId(null))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ExtImpHuaweiAds: keyId is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpSignKey() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.signKey(null))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ExtImpHuaweiAds: signKey is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpIsTestAuth() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.isTestAuthorization(null))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ExtImpHuaweiAds: IsTestAuthorization is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorfImpAdTypeIsRollAndVideoMaxDurationIncorrect() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(),
                impBuilder -> impBuilder
                        .video(Video.builder().maxduration(-1).build())
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder
                                .adtype("roll"))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("GetHuaweiAdsReqAdslot: Video maxDuration is empty when adtype is roll");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorfImpXnativeRequestIsEmpty() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), impBuilder -> impBuilder
                .banner(null)
                .xNative(Native.builder().request(null).build()));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("getNativeFormat: imp.xNative.request is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestMissingUser() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder.user(null), identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("getDeviceID: BidRequest.user is null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestMissingUserExt() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                .user(User.builder().build()));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("getDeviceID: BidRequest.user.ext is null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestUserExtDataIsIncorrect() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .data(mapper.valueToTree(ExtImpAdocean.of("", "", "")))
                                        .build())
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestUserExtDataFieldsIncorrect() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .data(mapper.valueToTree(ExtUserDataHuaweiAds.of(
                                                ExtUserDataDeviceIdHuaweiAds.of(
                                                        new String[]{}, new String[]{}, new String[]{}, new String[]{})))).build()).build()),
                identity());
        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("getDeviceID: Imei ,Oaid, Gaid are all empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestAppB() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder().bundle(null).build()),
                identity());
        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("resolvePkgName: app.bundle is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfRetcodeIncorrect() throws JsonProcessingException {
        // given
        final int incorrectRetcode = 250;
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(
                        builder -> builder.retcode(250))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("HuaweiAdsResponse retcode: " + incorrectRetcode);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMultiadEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(
                        builder -> builder.multiad(Collections.emptyList()))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("convertHuaweiAdsResp2BidderResp: multiad length is 0, get no ads from huawei side");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpTypeNativeAdTypeBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(identity())));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmNative: response is not a native ad");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpTypeBannerAdTypeNative() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder.adType(3)))))); //adType of native

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmBanner: huaweiads response is not a banner ad");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpBannerAdContentEmpty() throws JsonProcessingException {
        // given
        final ArrayList<HuaweiContent> content = new ArrayList<>();
        content.add(null);
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(content))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmPicture: content is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpVideoAdContentEmpty() throws JsonProcessingException {
        // given
        final ArrayList<HuaweiContent> content = new ArrayList<>();
        content.add(null);
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(content))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmVideo: content is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNoCreativeTypeFound() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .creativetype(100)))))))); // incorrect creativeType code

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("no banner support creativetype");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMetaDataImageInfoNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .imageInfo(null))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("content.MetaData.ImageInfo is null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNativeRequestEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().request(null).build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .adType(3))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmNative: imp.Native.Request is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNativeRequestUnparseable() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder()
                                .request("somethingUnparseable").build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .adType(3))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmNative: cant convert request to object");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMetaDataMediaFileUrlNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .adType(60) //bidType code of roll
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .mediaFile(givenMediaFile(mediaFileBuilder -> mediaFileBuilder
                                                        .url(null))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmVideo: Content.MetaData.MediaFile.Url is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfVideoDownloadUrlNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .videoInfo(givenVideoInfo(videoInfoBuilder -> videoInfoBuilder
                                                        .videoDownloadUrl(null))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmVideo: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpVideoNullVideoInfoNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .creativetype(9)
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .videoInfo(givenVideoInfo(videoInfoBuilder -> videoInfoBuilder
                                                        .width(0)
                                                        .height(0))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("extractAdmVideo: cannot get width, height");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldProceedSuccessfully() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(),
                        firstImpBuilder -> firstImpBuilder
                                .banner(null)
                                .video(Video.builder().build())
                                .ext(givenImpExt(impExtBuilder -> impExtBuilder
                                        .slotId("first"))),
                        secondImpBuilder -> secondImpBuilder
                                .ext(givenImpExt(impExtBuilder -> impExtBuilder
                                        .slotId("second"))),
                        thirdBuilder -> thirdBuilder.xNative(Native.builder().request(givenNativeRequest(identity())).build())),

                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(
                                firstAdBuilder -> firstAdBuilder
                                        .slotId("first")
                                        .adType(60),
                                secondAdBuilder -> secondAdBuilder
                                        .slotId("second"))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(2);
        assertThat(result.getValue().get(0)).satisfies(value -> {
            assertThat(value.getBidCurrency()).startsWith("CNY");
            assertThat(value.getType()).isEqualTo(BidType.video);
            assertThat(value.getBid().getAdm()).isNotBlank();
            assertThat(value.getBid().getId()).isEqualTo("someImpId");
        });
        assertThat(result.getValue().get(1)).satisfies(value -> {
            assertThat(value.getBidCurrency()).startsWith("CNY");
            assertThat(value.getType()).isEqualTo(BidType.banner);
            assertThat(value.getBid().getAdm()).isNotBlank();
            assertThat(value.getBid().getId()).isEqualTo("someImpId");
        });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldProceedSuccessfully() {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                .user(User.builder().ext(givenUserExt()).build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveAppLangIfNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getApp().getLang())
                .isEqualTo("en");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveCountry() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .country("NLD"))))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getDevice().getBelongCountry())
                .isEqualTo("NL");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveCountryEdgeCase() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .country("CHL"))))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getDevice().getBelongCountry())
                .isEqualTo("CL");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveCountryIfLengthIncorrect() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .country("C"))))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getDevice().getBelongCountry())
                .isEqualTo("ZA");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveModelName() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .model(null))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getDevice().getModel())
                .isEqualTo("HUAWEI");
    }

    @Test
    public void makeHttpRequestsCorrectlyresolveHuaweiAdsReqRegsInfo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(4, null)),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getRegs().getCoppa())
                .isEqualTo(4);
    }

    @Test
    public void makeHttpRequestsResolveHuaweiAdsReqNetWorkInfo() throws JsonProcessingException {
        HuaweiNetwork compareNetwork = HuaweiNetwork.of(4, 2, List.of(HuaweiCellInfo.of("46", "000")));
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .connectiontype(compareNetwork.getType())
                                .carrier("4")
                                .mccmnc("46-000-4"))),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiRequest.class).getNetwork()).satisfies(
                network -> {
                    assertThat(network.getType()).isEqualTo(compareNetwork.getType());
                    assertThat(network.getCarrier()).isEqualTo(compareNetwork.getCarrier());
                    assertThat(network.getCellInfoList().toString()).isEqualTo(compareNetwork.getCellInfoList().toString());
                }

        );
    }
*/
    /*
    @Test
    public void makeHttpRequestsResolveHuaweiAdsReqGeoInfo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenHuaweiRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .lat(60.45f)
                                        .lon(70.45f)
                                        .accuracy(90))))),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getGeo)
                .extracting(Geo::getLat, Geo::getLon, Geo::getAccuracy)
                .containsExactly(tuple(60.45f, 70.45f, 90));
    }
 */
    private static Geo givenGeo(Function<Geo.GeoBuilder, Geo.GeoBuilder> geoCustomizer) {
        return geoCustomizer.apply(Geo.builder().country("UA")).build();
    }


    /*
    private static Device givenDevice(Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceCustomizer) {
        return deviceCustomizer.apply(Device.builder()
                .geo(givenGeo(identity()))
                .connectiontype(4)
                .ua("someUa")
                .model("apple")
                .dnt(5)
                .mccmnc("someMccmnc-andAnotherMccmnc")
                .ip("someIp")
                .language("someLanguage")).build();
    }*/


    private static HuaweiDevice givenDevice(Function<HuaweiDevice.HuaweiDeviceBuilder, HuaweiDevice.HuaweiDeviceBuilder> deviceCustomizer) {
        return deviceCustomizer.apply(HuaweiDevice.builder()
                .ip("someIp")
                .useragent("someUserAgent")
                .localeCountry("someLocaleCountry")
                .version("someVersion")
                .belongCountry("someBelongCountry")
                .os("someOs")
                .model("someModel")
                .language("someLanguage")
                .maker("someMaker")
                .buildVersion("someBuildVersion")
                .clientTime("someClientTime") //needs time
                .emuiVer("someEmuiVer")
                .imei("someImei")
                .oaid("someOaid")
                .gaid("someGaid")
                .gaidTrackingEnabled("someGaidTrackingEnabled")
                .isTrackingEnabled("isTrackingEnabled") //can be boolean ?
                .height(14)
                .width(14)
                .pxratio(BigDecimal.ONE)
                .type(14)
                .dpi(14)).build();
    }

    private static HuaweiNetwork givenNetwork(Function<HuaweiNetwork.HuaweiNetworkBuilder, HuaweiNetwork.HuaweiNetworkBuilder> networkCustomizer) {
        return networkCustomizer.apply(HuaweiNetwork.builder()
                .carrier(14) //use better type
                .type(14)//same
                .cellInfoList(Arrays.asList(HuaweiCellInfo.builder()
                        .mcc("46")
                        .mnc("00").build()))).build();
    }

        private static HuaweiApp givenApp(Function<HuaweiApp.HuaweiAppBuilder, HuaweiApp.HuaweiAppBuilder> huaweiAdsAppCustomizer) {
        return huaweiAdsAppCustomizer.apply(HuaweiApp.builder()
                .country("someFrance")
                .lang("someLanguage") // is it valid ?
                .pkgname("somePackageName")
                .version("someVersion")).build();
    }

    private static List<HuaweiAdSlot> givenAdSlots(Function<HuaweiAdSlot.HuaweiAdSlotBuilder, HuaweiAdSlot.HuaweiAdSlotBuilder>... adSlotCustomizer) {
        return Arrays.stream(adSlotCustomizer).map(builder -> builder.apply(HuaweiAdSlot.builder()
                .test(14)
                .h(14)
                .w(14)
                .slotId("someSlotID")
                .format(Arrays.asList(HuaweiFormat.of(14, 14)))).build()).collect(Collectors.toList());
    }

    private static HuaweiRequest givenHuaweiRequest(Function<HuaweiRequest.HuaweiRequestBuilder, HuaweiRequest.HuaweiRequestBuilder> huaweiRequestCustomizer,
                                                    Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizer) {

        return huaweiRequestCustomizer.apply(HuaweiRequest.builder()
                        .app(givenApp(identity()))
                        .device(givenDevice(identity()))
                        .geo(givenGeo(identity()))
                        .network(givenNetwork(identity()))
                        .regs(Regs.of(14, null))
                        .multislot(givenAdSlots(identity())))
                .build();
    }

    private static HuaweiRequest givenHuaweiRequest(Function<HuaweiRequest.HuaweiRequestBuilder, HuaweiRequest.HuaweiRequestBuilder> huaweiRequestCustomizer) {
        return givenHuaweiRequest(huaweiRequestCustomizer, identity());
    }

    private static String givenNativeRequest(Function<HuaweiNativeRequest.HuaweiNativeRequestBuilder, HuaweiNativeRequest.HuaweiNativeRequestBuilder> nativeRequestCustomizer) {
        String request;
        try {
            request = mapper.writeValueAsString(nativeRequestCustomizer.apply(HuaweiNativeRequest.builder()
                    .eventTrackers(List.of(EventTracker.builder()
                            .eventTrackingMethods(1)
                            .eventType(1).build()))
                    .assets(List.of(Asset.builder().build()))).build());
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        return request;
    }


    private static List<Imp> givenImps(Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizer) {
        return Arrays.stream(impCustomizer).map(builder -> builder.apply(Imp.builder()
                .id("someImpId")
                .banner(Banner.builder().format(List.of(Format.builder().w(14).h(14).build())).build())
                .ext(givenImpExt(identity()))).build()).collect(Collectors.toList());
    }


    private static ObjectNode givenImpExt(Function<ExtImpHuaweiAds.ExtImpHuaweiAdsBuilder, ExtImpHuaweiAds.ExtImpHuaweiAdsBuilder> extImpCustomizer) {
        return mapper
                .createObjectNode()
                .set("bidder", mapper.valueToTree(extImpCustomizer.apply(ExtImpHuaweiAds.builder()
                        .slotId("someSlotId")
                        .adtype("someAdType")
                        .publisherId("somePublisherID")
                        .keyId("someKeyId")
                        .signKey("someSignKey")
                        .isTestAuthorization("true")).build()));
    }

    private static HuaweiMediaFile givenMediaFile(Function<HuaweiMediaFile.HuaweiMediaFileBuilder, HuaweiMediaFile.HuaweiMediaFileBuilder> mediaFileCustomizer) {
        return mediaFileCustomizer.apply(HuaweiMediaFile.builder()
                        .url("someMediadFileUrl")
                        .width(14)
                        .height(14))
                .build();
    }

    private static HuaweiVideoInfo givenVideoInfo(Function<HuaweiVideoInfo.HuaweiVideoInfoBuilder, HuaweiVideoInfo.HuaweiVideoInfoBuilder> videoInfoCustomizer) {
        return videoInfoCustomizer.apply(HuaweiVideoInfo.builder()
                        .width(14)
                        .height(14)
                        .videoDuration(1)
                        .videoDownloadUrl("someVideoDownloadUrl"))
                .build();
    }

    private static HuaweiMetadata givenMetadata(Function<HuaweiMetadata.HuaweiMetadataBuilder, HuaweiMetadata.HuaweiMetadataBuilder> metadataCustomizer) {
        return metadataCustomizer.apply(HuaweiMetadata.builder()
                        .duration(14)
                        .clickUrl("someClickUrl")
                        .imageInfo(List.of(HuaweiImageInfo.builder()
                                .url("someUrl")
                                .height(14)
                                .width(14).build()))
                        .intent("someIntent")
                        .mediaFile(givenMediaFile(identity()))
                        .videoInfo(givenVideoInfo(identity())))
                .build();
    }

    private static HuaweiRequest givenHuaweiAdsRequest(Function<HuaweiRequest.HuaweiRequestBuilder, HuaweiRequest.HuaweiRequestBuilder> huaweiAdsRequestCustomizer) {
        return huaweiAdsRequestCustomizer.apply(HuaweiRequest.builder()).build();
    }

    private static List<HuaweiContent> givenContent(Function<HuaweiContent.HuaweiContentBuilder, HuaweiContent.HuaweiContentBuilder>... contentCustomizer) {
        return Arrays.stream(contentCustomizer).map(builder -> builder.apply(HuaweiContent.builder()
                .price(BigDecimal.ONE)
                .monitor(List.of(HuaweiMonitor.builder()
                        .url(List.of("someMonitorUrl"))
                        .eventType("someMonitorEventType").build()))
                .metaData(givenMetadata(identity()))
                .creativetype(1)).build()).collect(Collectors.toList());
    }

    private static List<HuaweiAd> givenAds(Function<HuaweiAd.HuaweiAdBuilder, HuaweiAd.HuaweiAdBuilder>... adCustomizer) {
        return Arrays.stream(adCustomizer).map(builder -> builder.apply(HuaweiAd.builder()
                .slotId("someSlotId")
                .retcode(200)
                .content(givenContent(identity()))
                .adType(8)).build()).collect(Collectors.toList());
    }

    private static ExtUser givenUserExt() {
        return ExtUser.builder().data(mapper.valueToTree(ExtUserDataHuaweiAds.of(ExtUserDataDeviceIdHuaweiAds.of(
                new String[]{"someImei"},
                new String[]{"someOaid"},
                new String[]{"someGaid"},
                new String[]{"someClientTime"})))).build();
    }

    private static BidRequest givenBidRequest(Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(givenImps())).build();
    }
/*
    private static HuaweiAdsResponse givenHuaweiAdsResponse(Function<HuaweiAdsResponse.HuaweiAdsResponseBuilder, HuaweiAdsResponse.HuaweiAdsResponseBuilder> huaweiAdsResponseCustomizer) {
        return huaweiAdsResponseCustomizer.apply(HuaweiAdsResponse.builder()
                        .retcode(350)
                        .multiad(givenAds(identity())))
                .build();
    }


    private static HttpCall<BidRequest> givenHttpCall(HuaweiAdsRequest huaweiAdsRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
    /*
}

 */
}
