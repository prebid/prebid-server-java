package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdvangelistsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdvangelists() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/advangelists-exchange"))
                .withQueryParam("pubid", equalTo("19f1b372c7548ec1fe734d2c9f8dc688"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/advangelists/test-advangelists-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/advangelists/test-advangelists-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/advangelists/test-auction-advangelists-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/advangelists/test-auction-advangelists-response.json", response,
                singletonList("advangelists"));
    }
}

