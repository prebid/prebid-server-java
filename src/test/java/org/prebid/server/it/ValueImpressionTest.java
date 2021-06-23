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
public class ValueImpressionTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromValueImpression() throws IOException, JSONException {
        // given
        // ValueImpression bid response
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/valueimpression-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/valueimpression/test-valueimpression-bid-request-1.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/valueimpression/test-valueimpression-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7InZhbHVlaW1wcmVzc2lvbiI6IlZJLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/valueimpression/test-auction-valueimpression-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/valueimpression/test-auction-valueimpression-response.json",
                response, singletonList("valueimpression"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
