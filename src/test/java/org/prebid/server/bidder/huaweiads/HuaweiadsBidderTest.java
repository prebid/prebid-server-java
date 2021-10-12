package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.bidder.huaweiads.model.ExtUserDataHuaweiAds;
import org.prebid.server.bidder.huaweiads.model.HuaweiAd;
import org.prebid.server.bidder.huaweiads.model.HuaweiAdSlot;
import org.prebid.server.bidder.huaweiads.model.HuaweiApp;
import org.prebid.server.bidder.huaweiads.model.HuaweiCellInfo;
import org.prebid.server.bidder.huaweiads.model.HuaweiContent;
import org.prebid.server.bidder.huaweiads.model.HuaweiDevice;
import org.prebid.server.bidder.huaweiads.model.HuaweiFormat;
import org.prebid.server.bidder.huaweiads.model.HuaweiImageInfo;
import org.prebid.server.bidder.huaweiads.model.HuaweiMediaFile;
import org.prebid.server.bidder.huaweiads.model.HuaweiMetadata;
import org.prebid.server.bidder.huaweiads.model.HuaweiMonitor;
import org.prebid.server.bidder.huaweiads.model.HuaweiNativeRequest;
import org.prebid.server.bidder.huaweiads.model.HuaweiNetwork;
import org.prebid.server.bidder.huaweiads.model.HuaweiRequest;
import org.prebid.server.bidder.huaweiads.model.HuaweiResponse;
import org.prebid.server.bidder.huaweiads.model.HuaweiVideoInfo;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuawei;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
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
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "someUa"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMissingImpSlotId() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(givenImpExt(extImpBuilder -> extImpBuilder.adType(null))));
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .video(Video.builder().maxduration(-1).build())
                .ext(givenImpExt(extImpBuilder -> extImpBuilder
                        .adType("roll"))));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .startsWith("resolveTotalDuration: Video maxDuration is empty when adtype is roll");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorfImpXnativeRequestIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(null)
                .xNative(Native.builder().request(null).build()));

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("getAssets: imp.xNative.request is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestMissingUser() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.user(null), identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .startsWith("resolveHuaweiDevice: Imei, Oaid, Gaid are all empty or null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestMissingUserExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .user(User.builder().build()), identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .startsWith("resolveHuaweiDevice: Imei, Oaid, Gaid are all empty or null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailIfBidRequestUserExtDataIsIncorrect() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .data(mapper.createObjectNode().put("incorrectField", "incorrectValue"))
                                        .build())
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .startsWith("resolveHuaweiDevice: Imei, Oaid, Gaid are all empty or null");
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
                                                ExtUserDataDeviceIdHuaweiAds.builder()
                                                        .gaid(new ArrayList<>())
                                                        .oaid(new ArrayList<>())
                                                        .imei(new ArrayList<>())
                                                        .clientTime(new ArrayList<>()).build()))).build()).build()),
                identity());
        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .startsWith("resolveHuaweiDevice: Imei, Oaid, Gaid are all empty or null");
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
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
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
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(
                        builder -> builder.multiad(Collections.emptyList()))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .startsWith("convertHuaweiAdsRespToBidderResp: multiad is null or length is 0");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpTypeNativeAdTypeBanner() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(identity())));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall,
                givenBidRequest(impBuilder -> impBuilder.xNative(Native.builder().build())));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("resolveNativeBid: response is not a native ad");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpTypeBannerAdTypeNative() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder.adType(3)))))); //adType of native

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .startsWith("resolveBannerBid: huaweiads response is not a banner ad");
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
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("No banner support for this creativetype: " + 100);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMetaDataImageInfoNull() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .imageInfo(null))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("getFirstImageInfo: Metadata.ImageInfo is null");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNativeRequestEmpty() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().request(null).build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .adType(3))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall,
                givenBidRequest(impBuilder -> impBuilder.xNative(Native.builder().request(null).build())));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("resolveNativeBid: imp.Native.Request is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfAdTypeIncorrect() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .adType(60) //bidType code of roll code
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .mediaFile(givenMediaFile(mediaFileBuilder -> mediaFileBuilder
                                                        .url(null))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .startsWith("resolveBannerBid: huaweiads response is not a banner ad");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfVideoDownloadUrlNull() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(), impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().build())),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .videoInfo(givenVideoInfo(videoInfoBuilder -> videoInfoBuilder
                                                        .videoDownloadUrl(null))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall,
                givenBidRequest(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().build())));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .startsWith("resolveResourceUrl: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpVideoWidthNull() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .creativetype(9)
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .videoInfo(givenVideoInfo(videoInfoBuilder -> videoInfoBuilder
                                                        .width(0)
                                                        .height(0))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall,
                givenBidRequest(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().h(14).build())));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("resolveVideoWidth: cannot get width");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpVideoHeightNull() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity()),
                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResponseBuilder -> huaweiAdsResponseBuilder
                        .multiad(givenAds(adBuilder -> adBuilder
                                .content(givenContent(contentBuilder -> contentBuilder
                                        .creativetype(9)
                                        .metaData(givenMetadata(metadataBuilder -> metadataBuilder
                                                .videoInfo(givenVideoInfo(videoInfoBuilder -> videoInfoBuilder
                                                        .width(0)
                                                        .height(0))))))))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall,
                givenBidRequest(impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().w(14).build())));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("resolveVideoHeight: cannot get height");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldProceedSuccessfullyWithFewImps() throws JsonProcessingException {
        // given
        final HttpCall<HuaweiRequest> httpCall = givenHttpCall(givenHuaweiRequest(identity(),
                firstImpBuilder -> firstImpBuilder
                        .banner(null)
                        .video(Video.builder().build())
                        .ext(givenImpExt(impExtBuilder -> impExtBuilder
                                .slotId("first"))),
                secondImpBuilder -> secondImpBuilder
                        .ext(givenImpExt(impExtBuilder -> impExtBuilder
                                .slotId("second"))),
                thirdBuilder -> thirdBuilder
                        .xNative(Native.builder().request(givenNativeRequest(identity())).build())),

                mapper.writeValueAsString(givenHuaweiAdsResponse(huaweiAdsResBuilder -> huaweiAdsResBuilder
                        .multiad(givenAds(
                                firstAdBuilder -> firstAdBuilder
                                        .slotId("first")
                                        .adType(60),
                                secondAdBuilder -> secondAdBuilder
                                        .slotId("second"))))));

        // when
        final Result<List<BidderBid>> result = huaweiAdsBidder.makeBids(httpCall, givenBidRequest(identity(),
                firstImpBuilder -> firstImpBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.slotId("first")))
                        .video(Video.builder().build())
                        .banner(null),
                secondImpBuilder -> secondImpBuilder
                        .ext(givenImpExt(extImpBuilder -> extImpBuilder.slotId("second")))));

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
    public void makeHttpRequestsShouldCorrectResolveAppLangIfNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getApp)
                .extracting(HuaweiApp::getLang)
                .containsExactly("someLang");
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
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getDevice)
                .extracting(HuaweiDevice::getBelongCountry)
                .containsExactly("CL");
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
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getDevice)
                .extracting(HuaweiDevice::getBelongCountry)
                .containsExactly("ZA");
    }

    @Test
    public void makeHttpRequestsCorrectlyresolveHuaweiAdsReqRegsInfo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(4, null)),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getRegs)
                .extracting(Regs::getCoppa)
                .containsExactly(4);
    }

    @Test
    public void makeHttpRequestsResolveHuaweiAdsReqNetWorkInfo() throws JsonProcessingException {
        HuaweiNetwork compareNetwork = HuaweiNetwork.builder()
                .type(4)
                .carrier(2)
                .cellInfoList(List.of(HuaweiCellInfo.of("46", "000"))).build();
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .device(givenDevice(deviceBuilder -> deviceBuilder
                                .connectiontype(compareNetwork.getType())
                                .carrier("4")
                                .mccmnc("46-000-4"))),
                identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getNetwork)
                .extracting(HuaweiNetwork::getType, HuaweiNetwork::getCarrier,
                        element -> element.getCellInfoList().get(0).getMcc(),
                        element -> element.getCellInfoList().get(0).getMnc())
                .containsExactly(tuple(4, 2, "46", "000"));
    }

    @Test
    public void makeHttpRequestsResolveHuaweiAdsReqGeoInfo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
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

    @Test
    public void makeHttpRequestsResolveHuaweiAdsReqAppInfo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getApp)
                .extracting(HuaweiApp::getLang, HuaweiApp::getName,
                        HuaweiApp::getCountry, HuaweiApp::getPkgname, HuaweiApp::getVersion)
                .containsExactly(tuple("someLang", "someName", "FR", "someBundle", "someVersion"));
    }

    @Test
    public void makeHttpRequestsResolveHuaweiAdsReqDeviceInfo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<HuaweiRequest>>> result = huaweiAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(HuaweiRequest::getDevice)
                .extracting(HuaweiDevice::getBelongCountry, HuaweiDevice::getLocaleCountry, HuaweiDevice::getModel,
                        HuaweiDevice::getUseragent, HuaweiDevice::getMaker, HuaweiDevice::getOs,
                        HuaweiDevice::getVersion, HuaweiDevice::getType, HuaweiDevice::getHeight,
                        HuaweiDevice::getWidth, HuaweiDevice::getIp, HuaweiDevice::getPxratio,
                        HuaweiDevice::getLanguage)
                .containsExactly(tuple("FR", "FR", "someModel", "someUa", "someMaker",
                        "someOs", "someOsv", 4, 4, 4, "someIp", BigDecimal.ONE, "someLang"));
    }

    private static Geo givenGeo(Function<Geo.GeoBuilder, Geo.GeoBuilder> geoCustomizer) {
        return geoCustomizer.apply(Geo.builder().country("FRN")).build();
    }

    private static Device givenDevice(Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceCustomizer) {
        return deviceCustomizer.apply(Device.builder()
                .geo(givenGeo(identity()))
                .devicetype(4)
                .os("someOs")
                .osv("someOsv")
                .make("someMaker")
                .h(4)
                .w(4)
                .language("someLang")
                .pxratio(BigDecimal.ONE)
                .connectiontype(4)
                .ua("someUa")
                .model("someModel")
                .dnt(5)
                .mccmnc("someMccmnc-andAnotherMccmnc")
                .ip("someIp")
                .language("someLang")).build();
    }

    private static HuaweiDevice givenHuaweiDevice(
            Function<HuaweiDevice.HuaweiDeviceBuilder, HuaweiDevice.HuaweiDeviceBuilder> deviceCustomizer) {
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
                .isGaidTrackingEnabled("someGaidTrackingEnabled")
                .isTrackingEnabled("isTrackingEnabled") //can be boolean ?
                .height(14)
                .width(14)
                .pxratio(BigDecimal.ONE)
                .type(14)
                .dpi(14)).build();
    }

    private static HuaweiNetwork givenNetwork(Function<HuaweiNetwork.HuaweiNetworkBuilder,
            HuaweiNetwork.HuaweiNetworkBuilder> networkCustomizer) {
        return networkCustomizer.apply(HuaweiNetwork.builder()
                .carrier(14) //use better type
                .type(14)//same
                .cellInfoList(Arrays.asList(HuaweiCellInfo.of("46", "00")))).build();
    }

    private static HuaweiApp givenHuaweiApp(Function<HuaweiApp.HuaweiAppBuilder,
            HuaweiApp.HuaweiAppBuilder> huaweiAdsAppCustomizer) {
        return huaweiAdsAppCustomizer.apply(HuaweiApp.builder()
                .country("someFrance")
                .lang("someLanguage")
                .pkgname("somePackageName")
                .version("someVersion")).build();
    }

    private static List<HuaweiAdSlot> givenAdSlots(
            Function<HuaweiAdSlot.HuaweiAdSlotBuilder, HuaweiAdSlot.HuaweiAdSlotBuilder>... adSlotCustomizer) {
        return Arrays.stream(adSlotCustomizer).map(builder -> builder.apply(HuaweiAdSlot.builder()
                .test(14)
                .h(14)
                .w(14)
                .slotId("someSlotID")
                .format(Arrays.asList(HuaweiFormat.of(14, 14)))).build()).collect(Collectors.toList());
    }

    private static HuaweiRequest givenHuaweiRequest(
            Function<HuaweiRequest.HuaweiRequestBuilder, HuaweiRequest.HuaweiRequestBuilder> huaweiRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizer) {

        return huaweiRequestCustomizer.apply(HuaweiRequest.builder()
                        .app(givenHuaweiApp(identity()))
                        .device(givenHuaweiDevice(identity()))
                        .geo(givenGeo(identity()))
                        .network(givenNetwork(identity()))
                        .regs(Regs.of(14, null))
                        .multislot(givenAdSlots(identity())))
                .build();
    }

    private static HuaweiRequest givenHuaweiRequest(
            Function<HuaweiRequest.HuaweiRequestBuilder, HuaweiRequest.HuaweiRequestBuilder> huaweiRequestCustomizer) {
        return givenHuaweiRequest(huaweiRequestCustomizer, identity());
    }

    private static String givenNativeRequest(
            Function<HuaweiNativeRequest.HuaweiNativeRequestBuilder,
                    HuaweiNativeRequest.HuaweiNativeRequestBuilder> nativeRequestCustomizer) {
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
        return Arrays.stream(impCustomizer)
                .map(builder -> builder.apply(Imp.builder()
                        .id("someImpId")
                        .banner(Banner.builder().format(List.of(Format.builder()
                                .w(14)
                                .h(14).build())).build())
                        .ext(givenImpExt(identity()))).build())
                .collect(Collectors.toList());
    }

    private static ObjectNode givenImpExt(
            Function<ExtImpHuawei.ExtImpHuaweiBuilder, ExtImpHuawei.ExtImpHuaweiBuilder> extImpCustomizer) {
        return mapper
                .createObjectNode()
                .set("bidder", mapper.valueToTree(extImpCustomizer.apply(ExtImpHuawei.builder()
                        .slotId("someSlotId")
                        .adType("someAdType")
                        .publisherId("somePublisherID")
                        .keyId("someKeyId")
                        .signKey("someSignKey")
                        .isTestAuthorization("true")).build()));
    }

    private static HuaweiMediaFile givenMediaFile(
            Function<HuaweiMediaFile.HuaweiMediaFileBuilder,
                    HuaweiMediaFile.HuaweiMediaFileBuilder> mediaFileCustomizer) {
        return mediaFileCustomizer.apply(HuaweiMediaFile.builder()
                        .url("someMediadFileUrl")
                        .width(14)
                        .height(14))
                .build();
    }

    private static HuaweiVideoInfo givenVideoInfo(
            Function<HuaweiVideoInfo.HuaweiVideoInfoBuilder,
                    HuaweiVideoInfo.HuaweiVideoInfoBuilder> videoInfoCustomizer) {
        return videoInfoCustomizer.apply(HuaweiVideoInfo.builder()
                        .width(14)
                        .height(14)
                        .videoDuration(1)
                        .videoDownloadUrl("someVideoDownloadUrl"))
                .build();
    }

    private static HuaweiMetadata givenMetadata(
            Function<HuaweiMetadata.HuaweiMetadataBuilder, HuaweiMetadata.HuaweiMetadataBuilder> metadataCustomizer) {
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

    private static HuaweiRequest givenHuaweiAdsRequest(
            Function<HuaweiRequest.HuaweiRequestBuilder,
                    HuaweiRequest.HuaweiRequestBuilder> huaweiAdsRequestCustomizer) {
        return huaweiAdsRequestCustomizer.apply(HuaweiRequest.builder()).build();
    }

    private static List<HuaweiContent> givenContent(
            Function<HuaweiContent.HuaweiContentBuilder, HuaweiContent.HuaweiContentBuilder>... contentCustomizer) {
        return Arrays.stream(contentCustomizer).map(builder -> builder.apply(HuaweiContent.builder()
                .contentid("someContentid")
                .price(BigDecimal.ONE)
                .monitor(List.of(HuaweiMonitor.builder()
                        .url(List.of("someMonitorUrl"))
                        .eventType("someMonitorEventType").build()))
                .metaData(givenMetadata(identity()))
                .creativetype(1)).build()).collect(Collectors.toList());
    }

    private static List<HuaweiAd> givenAds(
            Function<HuaweiAd.HuaweiAdBuilder, HuaweiAd.HuaweiAdBuilder>... adCustomizer) {
        return Arrays.stream(adCustomizer).map(builder -> builder.apply(HuaweiAd.builder()
                .slotId("someSlotId")
                .retcode(200)
                .content(givenContent(identity()))
                .adType(8)).build()).collect(Collectors.toList());
    }

    private static ExtUser givenUserExt() {
        return ExtUser.builder().data(mapper.valueToTree(ExtUserDataHuaweiAds.of(ExtUserDataDeviceIdHuaweiAds.builder()
                .imei(List.of("someImei"))
                .oaid(List.of("someOaid"))
                .gaid(List.of("someGaid"))
                .clientTime(List.of("someClientTime")).build()))).build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .user(User.builder().ext(givenUserExt()).build())
                .device(givenDevice(identity()))
                .app(givenApp(identity()))
                .imp(givenImps(impCustomizer)).build();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidReqCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizer) {
        return bidReqCustomizer.apply(BidRequest.builder()
                .user(User.builder().ext(givenUserExt()).build())
                .app(givenApp(identity()))
                .device(givenDevice(identity()))
                .imp(givenImps(impCustomizer))).build();
    }

    private static App givenApp(Function<App.AppBuilder, App.AppBuilder> appCustomizer) {
        return appCustomizer.apply(App.builder()
                .id("someId")
                .ver("someVersion")
                .name("someName")
                .content(Content.builder().language("someLang").build())
                .bundle("someBundle")).build();
    }

    private static HuaweiResponse givenHuaweiAdsResponse(Function<HuaweiResponse.HuaweiResponseBuilder,
            HuaweiResponse.HuaweiResponseBuilder> huaweiAdsResponseCustomizer) {
        return huaweiAdsResponseCustomizer.apply(HuaweiResponse.builder()
                        .retcode(350)
                        .multiad(givenAds(identity())))
                .build();
    }

    private static HttpCall<HuaweiRequest> givenHttpCall(HuaweiRequest huaweiRequest, String body) {
        return HttpCall.success(
                HttpRequest.<HuaweiRequest>builder().payload(huaweiRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}



