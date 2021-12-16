package org.prebid.server.it.hooks;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.it.IntegrationTest;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.empty;

@RunWith(SpringRunner.class)
public class HooksTest extends IntegrationTest {

    private static final String RUBICON = "rubicon";

    @Test
    public void openrtb2AuctionShouldRunHooksAtEachStage() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("hooks/sample-module/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("hooks/sample-module/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .queryParam("sample-it-module-update", "headers,body")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/sample-module/test-auction-sample-module-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/sample-module/test-auction-sample-module-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);
    }

    @Test
    public void openrtb2AuctionShouldBeRejectedByEntrypointHook() throws IOException {
        given(SPEC)
                .queryParam("sample-it-module-reject", "true")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/sample-module/test-auction-sample-module-request.json"))
                .post("/openrtb2/auction")
                .then()
                .statusCode(200)
                .body("seatbid", empty());
    }

    @Test
    public void openrtb2AuctionShouldBeRejectedByRawAuctionRequestHook() throws IOException {
        given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-raw-auction-request-reject-request.json"))
                .post("/openrtb2/auction")
                .then()
                .statusCode(200)
                .body("seatbid", empty());
    }

    @Test
    public void openrtb2AuctionShouldBeRejectedByProcessedAuctionRequestHook() throws IOException {
        given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-processed-auction-request-reject-request.json"))
                .post("/openrtb2/auction")
                .then()
                .statusCode(200)
                .body("seatbid", empty());
    }

    @Test
    public void openrtb2AuctionShouldRejectRubiconBidderByBidderRequestHook() throws IOException, JSONException {
        // when
        final Response response = given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-bidder-request-reject-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/reject/test-auction-bidder-request-reject-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);

        WIRE_MOCK_RULE.verify(0, postRequestedFor(urlPathEqualTo("/rubicon-exchange")));
    }

    @Test
    public void openrtb2AuctionShouldRejectRubiconBidderByRawBidderResponseHook() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .willReturn(aResponse().withBody(jsonFrom("hooks/reject/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-raw-bidder-response-reject-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/reject/test-auction-raw-bidder-response-reject-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);

        WIRE_MOCK_RULE.verify(1, postRequestedFor(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("hooks/reject/test-rubicon-bid-request-1.json"))));
    }

    @Test
    public void openrtb2AuctionShouldRejectRubiconBidderByProcessedBidderResponseHook()
            throws IOException, JSONException {

        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .willReturn(aResponse().withBody(jsonFrom("hooks/reject/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-processed-bidder-response-reject-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/reject/test-auction-processed-bidder-response-reject-response.json",
                response,
                singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);

        WIRE_MOCK_RULE.verify(1, postRequestedFor(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("hooks/reject/test-rubicon-bid-request-1.json"))));
    }
}
