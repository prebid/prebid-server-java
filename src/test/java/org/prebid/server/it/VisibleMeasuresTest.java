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

public class VisibleMeasuresTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromVisibleMeasures() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/visiblemeasures-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/visiblemeasures/test-visiblemeasures-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/visiblemeasures/test-visiblemeasures-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/visiblemeasures/test-auction-visiblemeasures-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/visiblemeasures/test-auction-visiblemeasures-response.json", response,
                singletonList("visiblemeasures"));
    }
}
