package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class LogicadTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLogicad() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/logicad-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/logicad/test-logicad-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/logicad/test-logicad-bid-response.json"))));
        // when
        final Response response = responseFor("openrtb2/logicad/test-auction-logicad-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/logicad/test-auction-logicad-response.json", response, singletonList("logicad"));
    }
}
