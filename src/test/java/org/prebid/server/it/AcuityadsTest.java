package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AcuityadsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAcuityAds() throws IOException, JSONException {
        // given
        // Acuityads bid response for imp
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/acuityads-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalToIgnoreCase("test-user-agent"))
                .withHeader("X-Forwarded-For", equalToIgnoreCase("123.123.123.123"))
                .withHeader("X-Openrtb-Version", equalToIgnoreCase("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/acuityads/test-acuityads-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/acuityads/test-acuityads-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/acuityads/test-cache-acuityads-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/acuityads/test-cache-acuityads-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"acuityads":"AA-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFjdWl0eWFkcyI6IkFBLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/acuityads/test-auction-acuityads-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/acuityads/test-auction-acuityads-response.json",
                response, singletonList("acuityads"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
