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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class HooksTest extends IntegrationTest {

    private static final String RUBICON = "rubicon";

    @Test
    public void openrtb2AuctionShouldRunHooksAtEachStage() throws IOException, JSONException {
        // given
        // rubicon bid response for imp 1
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("hooks/sample-module/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("hooks/sample-module/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .queryParam("sample-it-module-update", "headers,body")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/sample-module//test-auction-sample-module-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/sample-module/test-auction-sample-module-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);
    }
}
