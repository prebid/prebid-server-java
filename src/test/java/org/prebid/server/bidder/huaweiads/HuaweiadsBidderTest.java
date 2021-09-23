package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.*;
import com.iab.openrtb.request.Format;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.*;

import org.prebid.server.bidder.huaweiads.model.Content;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adocean.ExtImpAdocean;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
                .user(User.builder()
                        .ext(givenUserExt()).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpSlotId() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.slotId(null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.adtype(null))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.publisherId(null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.keyId(null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.signKey(null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.isTestAuthorization(null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder
                        .video(Video.builder().maxduration(-1).build())
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder
                                .adtype("roll"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder
                .banner(null)
                .xNative(Native.builder().request(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.user(null), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .user(User.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .data(mapper.valueToTree(ExtImpAdocean.of("", "", "")))
                                .build())
                        .build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .data(mapper.valueToTree(ExtUserDataHuaweiAds.of(
                                        ExtUserDataDeviceIdHuaweiAds.of(
                                                new String[]{}, new String[]{}, new String[]{}, new String[]{})))).build()).build()),
                identity());
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder().bundle(null).build()),
                identity());
        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
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
    public void makeBidsShouldReturnErrorIfBidRequestImpNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.imp(null),
                        identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(identity())));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("convertHuaweiAdsResp2BidderResp: BidRequest.imp is null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpTypeNativeAdTypeBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), impBuilder -> impBuilder
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), identity()),
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
        final ArrayList<Content> content = new ArrayList<>();
        content.add(null);
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), identity()),
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
        final ArrayList<Content> content = new ArrayList<>();
        content.add(null);
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), impBuilder -> impBuilder
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), identity()),
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), identity()),
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), impBuilder -> impBuilder
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), impBuilder -> impBuilder
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), impBuilder -> impBuilder
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(), impBuilder -> impBuilder
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity(),
                        firstImpBuilder -> firstImpBuilder
                                .banner(null)
                                .video(Video.builder().build())
                                .ext(givenImpExt(impExtBuilder -> impExtBuilder
                                        .slotId("first"))),
                        secondImpBuilder -> secondImpBuilder
                                .ext(givenImpExt(impExtBuilder -> impExtBuilder
                                        .slotId("second")))),
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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
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
        final BidRequest bidRequest = givenBidRequest(identity(),identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiAdsRequest.class).getApp().getLang())
                .isEqualTo("en");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveCountry() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .country("NLD"))))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiAdsRequest.class).getDevice().getBelongCountry())
                .isEqualTo("NL");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveCountryEdgeCase() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .country("CHL"))))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiAdsRequest.class).getDevice().getBelongCountry())
                .isEqualTo("CL");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveCountryIfLengthIncorrect() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .geo(givenGeo(geoBuilder -> geoBuilder
                                        .country("C"))))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiAdsRequest.class).getDevice().getBelongCountry())
                .isEqualTo("ZA");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveModelName() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .model(null))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiAdsRequest.class).getDevice().getModel())
                .isEqualTo("HUAWEI");
    }

    @Test
    public void makeHttpRequestsShouldCorrectResolveNetworkType() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .connectiontype(null))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(mapper.readValue(result.getValue().get(0).getBody(), HuaweiAdsRequest.class).getNetwork().getType())
                .isEqualTo(0);
    }


    private static Geo givenGeo(Function<Geo.GeoBuilder, Geo.GeoBuilder> geoCustomizer) {
        return geoCustomizer.apply(Geo.builder().country("UA")).build();
    }

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
    }

    private static BidRequest givenBidRequest(Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .app(App.builder()
                                .ver("1.0")
                                .name("appName")
                                .bundle("someBundle")
                                .build())
                        .user(User.builder().ext(givenUserExt()).build())
                        .device(givenDevice(identity()))
                        .site(Site.builder().page("somePage").build())
                        .imp(givenImps(impCustomizer)))
                .build();
    }

    private static BidRequest givenBidRequest(Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return givenBidRequest(bidRequestCustomizer, identity());
    }

    private static String givenNativeRequest(Function<NativeRequest.NativeRequestBuilder, NativeRequest.NativeRequestBuilder> nativeRequestCustomizer) {
        String request;
        try {
             request = mapper.writeValueAsString(nativeRequestCustomizer.apply(NativeRequest.builder()
                    .eventTrackers(List.of(EventTracker.builder()
                            .eventTrackingMethods(1)
                            .eventType(1).build()))
                    .assets(List.of(Asset.builder().build()))));
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        return request;
    }

    private static List<Imp> givenImps(Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizer) {
        List<Imp> imps = new ArrayList<>();
        for(Function<Imp.ImpBuilder, Imp.ImpBuilder> impBuilderFunction : impCustomizer) {
            Imp imp = impBuilderFunction.apply(Imp.builder()
                            .id("someImpId")
                            .banner(Banner.builder().format(List.of(Format.builder().w(10).h(15).build())).build())
                            .ext(givenImpExt(identity())))
                    .build();
            imps.add(imp);
        }
        return imps;
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

    private static MediaFile givenMediaFile(Function<MediaFile.MediaFileBuilder, MediaFile.MediaFileBuilder> mediaFileCustomizer) {
        return mediaFileCustomizer.apply(MediaFile.builder()
                        .url("someMediadFileUrl")
                        .width(14)
                        .height(14))
                .build();
    }

    private static VideoInfo givenVideoInfo(Function<VideoInfo.VideoInfoBuilder, VideoInfo.VideoInfoBuilder> videoInfoCustomizer) {
        return videoInfoCustomizer.apply(VideoInfo.builder()
                        .width(14)
                        .height(14)
                        .videoDuration(1)
                        .videoDownloadUrl("someVideoDownloadUrl"))
                .build();
    }

    private static Metadata givenMetadata(Function<Metadata.MetadataBuilder, Metadata.MetadataBuilder> metadataCustomizer) {
        return metadataCustomizer.apply(Metadata.builder()
                        .duration(14)
                        .clickUrl("someClickUrl")
                        .imageInfo(List.of(ImageInfo.builder()
                                .url("someUrl")
                                .height(14)
                                .width(14).build()))
                        .intent("someIntent")
                        .mediaFile(givenMediaFile(identity()))
                        .videoInfo(givenVideoInfo(identity())))
                .build();
    }

    private static List<Content> givenContent(Function<Content.ContentBuilder, Content.ContentBuilder>... contentCustomizer) {
        List<Content> contents = new ArrayList<>();
        for(Function<Content.ContentBuilder, Content.ContentBuilder> contentBuilderFunction : contentCustomizer) {
            Content content = contentBuilderFunction.apply(Content.builder()
                            .price(BigDecimal.ONE)
                            .monitor(List.of(Monitor.builder()
                                    .url(List.of("someMonitorUrl"))
                                    .eventType("someMonitorEventType").build()))
                            .metaData(givenMetadata(identity()))
                            .creativetype(1))
                    .build();
            contents.add(content);
        }
        return contents;
    }

    private static List<Ad> givenAds(Function<Ad.AdBuilder, Ad.AdBuilder>... adCustomizer) {
        List<Ad> ads = new ArrayList<>();
        for(Function<Ad.AdBuilder, Ad.AdBuilder> adBuilderFunction : adCustomizer) {
            Ad ad = adBuilderFunction.apply(Ad.builder()
                            .slotId("someSlotId")
                            .retcode(200)
                            .content(givenContent(identity()))
                            .adType(8)) // adType banner code
                    .build();
            ads.add(ad);
        }
        return ads;
    }

    private static ExtUser givenUserExt() {
        return ExtUser.builder().data(mapper.valueToTree(ExtUserDataHuaweiAds.of( ExtUserDataDeviceIdHuaweiAds.of(
                new String[]{"someImei"},
                new String[]{"someOaid"},
                new String[]{"someGaid"},
                new String[]{"someClientTime"})))).build();
    }

    private static HuaweiAdsResponse givenHuaweiAdsResponse(Function<HuaweiAdsResponse.HuaweiAdsResponseBuilder, HuaweiAdsResponse.HuaweiAdsResponseBuilder> huaweiAdsResponseCustomizer) {
        return huaweiAdsResponseCustomizer.apply(HuaweiAdsResponse.builder()
                        .retcode(350)
                        .multiad(givenAds(identity())))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
