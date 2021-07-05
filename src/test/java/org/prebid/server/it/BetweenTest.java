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
public class BetweenTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBetween() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/between-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalTo("testUa"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Referer", equalTo("awesomePage"))
                .withQueryParam("host", equalTo("lbs-ru1.ads"))
                .withQueryParam("pubId", equalTo("publisherTestID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/between/test-between-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/between/test-between-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"between":"BTW-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImJldHdlZW4iOiJCVFctVUlEIn19")
                .body(jsonFrom("openrtb2/between/test-auction-between-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/between/test-auction-between-response.json",
                response, singletonList("between"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
