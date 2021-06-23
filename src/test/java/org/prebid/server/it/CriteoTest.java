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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class CriteoTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromCriteo() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/criteo-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("Cookie", equalTo("uid=CR-UID"))
                .withHeader("X-Forwarded-For", equalTo("91.199.242.236"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/criteo/test-criteo-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/criteo/test-criteo-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"criteo":"CR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNyaXRlbyI6IkNSLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/criteo/test-auction-criteo-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/criteo/test-auction-criteo-response.json", response, singletonList("criteo"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
