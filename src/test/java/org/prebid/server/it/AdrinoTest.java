package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
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
public class AdrinoTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdrino() throws IOException, JSONException {
        // given
        // Adtarget bid response for imp 14
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adrino-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/adrino/test-adrino-bid-request-single-native.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adrino/test-adrino-bid-request-single-native.json"))));

        // when
        final Response response = responseFor("openrtb2/adrino/test-auction-adrino-requiest.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adrino/test-auction-adrino-response.json", response,
                singletonList("adrino"));
    }
}
