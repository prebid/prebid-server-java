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
public class Ttx33AcrossAliasTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFrom33Across() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/thirtythreeacross-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/33across/test-33across-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/33across/test-33across-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/33across/test-auction-33across-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/33across/test-auction-33across-response.json",
                response, singletonList("33across"));
    }
}
