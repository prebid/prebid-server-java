package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdoceanTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdocean() throws IOException, JSONException {

        WIRE_MOCK_RULE.stubFor(get(WireMock.urlPathMatching("/adocean-exchange/_[0-9]*/ad.json"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adocean/test-adocean-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/adocean/test-auction-adocean-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adocean/test-auction-adocean-response.json", response,
                singletonList("adocean"));
    }
}
