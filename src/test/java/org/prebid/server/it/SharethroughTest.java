package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.util.HttpUtil;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SharethroughTest extends IntegrationTest {

    private static final Date TEST_TIME = new Date(1604455678999L); // hardcoded value in bidder
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String TEST_FORMATTED_TIME = DATE_FORMAT.format(TEST_TIME);
    private static final String DEADLINE_FORMATTED_TIME = DATE_FORMAT.format(new Date(TEST_TIME.getTime() + 1000L));

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSharethrough() throws IOException, JSONException {
        // given
        wireMockRule.stubFor(post(urlPathEqualTo("/sharethrough-exchange"))
                .withQueryParam("placement_key", equalTo("abc123"))
                .withQueryParam("bidId", equalTo("bid"))
                .withQueryParam("consent_required", equalTo("false"))
                .withQueryParam("consent_string", equalTo("consentValue"))
                .withQueryParam("instant_play_capable", equalTo("true"))
                .withQueryParam("stayInIframe", equalTo("true"))
                .withQueryParam("height", equalTo("50"))
                .withQueryParam("width", equalTo("50"))
                .withQueryParam("supplyId", equalTo("FGMrCMMc"))
                .withQueryParam("adRequestAt", equalTo(TEST_FORMATTED_TIME))
                .withQueryParam("ttduid", equalTo("id"))
                .withQueryParam("stxuid", equalTo("STR-UID"))
                .withQueryParam("strVersion", equalTo("7"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("User-Agent", equalTo("Android Chrome/60"))
                .withHeader("X-Forwarded-For", equalTo("127.0.0.1"))
                .withHeader("Origin", equalTo("http://www.example.com"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withRequestBody(equalTo(jsonFrom("openrtb2/sharethrough/test-sharethrough-request.json")
                        .replace("{{ DEADLINE_FORMATTED_TIME }}", DEADLINE_FORMATTED_TIME)))
                .willReturn(
                        aResponse().withBody(jsonFrom("openrtb2/sharethrough/test-sharethrough-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sharethrough/test-cache-sharethrough-request.json")))
                .willReturn(
                        aResponse().withBody(jsonFrom("openrtb2/sharethrough/test-cache-sharethrough-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"sharethrough":"STR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InNoYXJldGhyb3VnaCI6IlNUUi1VSUQifX0=")
                .body(jsonFrom("openrtb2/sharethrough/test-auction-sharethrough-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/sharethrough/test-auction-sharethrough-response.json",
                response, singletonList("sharethrough"))
                .replace("{{ TEST_FORMATTED_TIME }}", HttpUtil.encodeUrl(TEST_FORMATTED_TIME))
                .replace("{{ DEADLINE_FORMATTED_TIME }}", DEADLINE_FORMATTED_TIME);

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
