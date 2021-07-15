package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class GammaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromGamma() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/gamma-exchange/"))
                .withRequestBody(absent())
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/gamma/test-gamma-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/gamma/test-auction-gamma-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/gamma/test-auction-gamma-response.json", response, singletonList("gamma"));
    }
}
