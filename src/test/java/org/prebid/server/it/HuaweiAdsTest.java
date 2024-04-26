package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
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
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ch"))
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
    public void testOpenrtb2AuctionBannerAppPromotionType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ch"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner_app_promotion_type/" + BID_REQUEST)))
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
    public void testOpenrtb2AuctionBannerChEndpoint() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ch"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner_ch_endpoint/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner_ch_endpoint/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_ch_endpoint/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner_ch_endpoint/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerEuEndpoint() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-eu"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner_eu_endpoint/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner_eu_endpoint/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_eu_endpoint/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner_eu_endpoint/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerImei() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ch"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner_imei/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner_imei/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_imei/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_imei/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerInterstitialType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-eu"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner_interstitial_type/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_interstitial_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_interstitial_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_interstitial_type/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerMccMnc() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/banner_mccmnc/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/banner_mccmnc/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_mccmnc/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/banner_mccmnc/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerNonIntegerMccMnc() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
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

    @Test
    public void testOpenrtb2AuctionBannerRuEndpoint() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ru"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_ru_endpoint/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_ru_endpoint/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_ru_endpoint/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_ru_endpoint/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerNotAppPromotionType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ch"))
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
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
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
    public void testOpenrtb2AuctionBannerWithUserGeo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_with_user_geo/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_with_user_geo/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_with_user_geo/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_with_user_geo/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerWithoutDeviceGeo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_without_device_geo/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_without_device_geo/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_without_device_geo/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_without_device_geo/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionBannerWithoutUserext() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-ch"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/banner_without_userext/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/banner_without_userext/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/banner_without_userext/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/banner_without_userext/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionInterstitialVideoType() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/video_interstitial_type/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/video_interstitial_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/video_interstitial_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/video_interstitial_type/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionNativeIncludeVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
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

    @Test
    public void testOpenrtb2AuctionNativeSingleImage() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-eu"))
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

    @Test
    public void testOpenrtb2AuctionNativeThreeImage() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
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

    @Test
    public void testOpenrtb2AuctionNativeThreeImageIncludeIcon() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
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
    public void testOpenrtb2AuctionVideoRewardedTypeNoIconsNoImages() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/huaweiads/video_rewarded_type_no_icons_no_images/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/huaweiads/video_rewarded_type_no_icons_no_images/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/video_rewarded_type_no_icons_no_images/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/video_rewarded_type_no_icons_no_images/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionVideoRewardedTypeWithIcon() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/video_rewarded_type_with_icon/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/video_rewarded_type_with_icon/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/video_rewarded_type_with_icon/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/video_rewarded_type_with_icon/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionVideoRewardedTypeWithImages() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/huaweiads/video_rewarded_type_with_images/" + BID_REQUEST)))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/huaweiads/video_rewarded_type_with_images/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/video_rewarded_type_with_images/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/huaweiads/video_rewarded_type_with_images/" + AUCTION_RESPONSE,
                response,
                List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionRollVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/video_roll_type/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/video_roll_type/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/video_roll_type/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/video_roll_type/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

    @Test
    public void testOpenrtb2AuctionVideo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/huaweiads-exchange-as"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/huaweiads/simple_video/" + BID_REQUEST)))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/huaweiads/simple_video/" + BID_RESPONSE))));

        // when
        final Response response = responseFor(
                "openrtb2/huaweiads/simple_video/" + AUCTION_REQUEST,
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/huaweiads/simple_video/" + AUCTION_RESPONSE, response, List.of("huaweiads"));
    }

}
