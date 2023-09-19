package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@RunWith(SpringRunner.class)
public class HuaweiAdsTest extends IntegrationTest {

    private static final String BID_REQUEST = "test-huaweiads-bid-request.json";
    private static final String BID_RESPONSE = "test-huaweiads-bid-response.json";
    private static final String AUCTION_REQUEST = "test-huaweiads-auction-request.json";
    private static final String AUCTION_RESPONSE = "test-huaweiads-auction-response.json";

    @Test
    public void testOpenrtb2AuctionCoreFunctionality() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBanner1() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner1/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner1/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner1/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner1/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }


    //todo: ifa is masked because coppa = 1, works when coppa = 0
    @Test
    public void testOpenrtb2AuctionBanner1WithoutUserext() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner1_without_userext/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner1_without_userext/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner1_without_userext/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner1_without_userext/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    //todo: reworked originally stored request;
    // alpha 2 country code and absent code doesn't work for the auction request, but Huawei bidder can handle it

    @Test
    public void testOpenrtb2AuctionBanner2() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner2/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner2/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner2/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner2/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBanner3() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner3/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner3/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner3/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner3/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBanner4Mccmnc() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner4_mccmnc/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner4_mccmnc/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner4_mccmnc/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner4_mccmnc/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    //todo: geo country should be present, but it has higher priority, so user's geo won't be used
    @Test
    @Ignore
    public void testOpenrtb2AuctionBanner5UserGeo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner5_user_geo/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner5_user_geo/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner5_user_geo/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner5_user_geo/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBanner6Imei() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner6_imei/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner6_imei/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner6_imei/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner6_imei/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    //todo: works only when coppa = 0
    @Test
    public void testOpenrtb2AuctionbannerAppPromotionType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_app_promotion_type/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_app_promotion_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_app_promotion_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_app_promotion_type/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionbannerNonIntegerMccmnc() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_non_integer_mccmnc/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_non_integer_mccmnc/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_non_integer_mccmnc/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_non_integer_mccmnc/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    //todo: works only when coppa = 0
    @Test
    public void testOpenrtb2AuctionBannerNotAppPromotionType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_not_app_promotion_type/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_not_app_promotion_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_not_app_promotion_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_not_app_promotion_type/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerWrongMccmnc() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_wrong_mccmnc/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_wrong_mccmnc/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_wrong_mccmnc/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_wrong_mccmnc/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerTestExtraInfo1() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_test_extra_info_1/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_test_extra_info_1/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_test_extra_info_1/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_test_extra_info_1/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerTestExtraInfo2() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_test_extra_info_2/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_test_extra_info_2/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_test_extra_info_2/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_test_extra_info_2/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerTestExtraInfo3() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_test_extra_info_3/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_test_extra_info_3/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_test_extra_info_3/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_test_extra_info_3/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionInterstitialBannerType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/interstitial_banner_type/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/interstitial_banner_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/interstitial_banner_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/interstitial_banner_type/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionInterstitialVideoType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/interstitial_video_type/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/interstitial_video_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/interstitial_video_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/interstitial_video_type/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    //todo: geo.country = "" not working, default country code has to be used, but privacy security enforcer blocks it
    // and asset type was missed
    @Test
    public void testOpenrtb2AuctionNativeIncludeVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/native_include_video/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/native_include_video/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/native_include_video/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/native_include_video/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    //todo: org.prebid.server.auction.BidResponseCreator.setAssetTypes sets asset types intentionally
    @Test
    public void testOpenrtb2AuctionNativeSingleImage() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/native_single_image/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/native_single_image/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/native_single_image/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/native_single_image/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }


    //todo: org.prebid.server.auction.BidResponseCreator.setAssetTypes sets asset types
    @Test
    public void testOpenrtb2AuctionNativeThreeImage() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/native_three_image/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/native_three_image/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/native_three_image/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/native_three_image/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    //todo: geo.country = "" not working, default country code has to be used, but privacy security enforcer blocks it
    // and the asset type was added

    @Test
    public void testOpenrtb2AuctionNativeThreeImageIncludeIcon() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/native_three_image_include_icon/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/native_three_image_include_icon/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/native_three_image_include_icon/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/native_three_image_include_icon/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRewardedVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/rewarded_video/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/rewarded_video/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/rewarded_video/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/rewarded_video/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRewardedVideo1() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/rewarded_video1/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/rewarded_video1/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/rewarded_video1/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/rewarded_video1/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRewardedVideo2() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/rewarded_video2/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/rewarded_video2/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/rewarded_video2/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/rewarded_video2/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRewardedVideo3() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/rewarded_video3/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/rewarded_video3/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/rewarded_video3/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/rewarded_video3/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRewardedVideo4() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/rewarded_video4/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/rewarded_video4/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/rewarded_video4/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/rewarded_video4/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRollVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/roll_video/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/roll_video/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/roll_video/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/roll_video/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/video/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/video/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/video/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/video/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

}
