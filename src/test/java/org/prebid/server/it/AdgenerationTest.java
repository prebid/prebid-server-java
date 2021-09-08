package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdgenerationTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdgeneration() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/adgeneration-exchange"))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/adgeneration/test-adgeneration-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adgeneration/test-auction-adgeneration-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adgeneration/test-auction-adgeneration-response.json", response,
                singletonList("adgeneration"));
    }
}
