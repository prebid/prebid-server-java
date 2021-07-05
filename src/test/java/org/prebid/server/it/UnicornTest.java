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
public class UnicornTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromUnicorn() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/unicorn-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/unicorn/test-unicorn-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/unicorn/test-unicorn-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"unicorn":"UC-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InVuaWNvcm4iOiJVQy1VSUQifX0=")
                .body(jsonFrom("openrtb2/unicorn/test-auction-unicorn-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/unicorn/test-auction-unicorn-response.json",
                response, singletonList("unicorn"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
