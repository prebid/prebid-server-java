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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SeedingAllianceTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSeedingAlliance() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/seedingAlliance-exchange"))
                .withQueryParam("ssp", equalTo("accountId"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/seedingAlliance/test-seedingAlliance-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/seedingAlliance/test-seedingAlliance-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/seedingAlliance/test-auction-seedingAlliance-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/seedingAlliance/test-auction-seedingAlliance-response.json", response,
                singletonList("seedingAlliance"));
    }
}

