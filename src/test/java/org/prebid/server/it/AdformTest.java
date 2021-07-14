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
public class AdformTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdform() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/adform-exchange"))
                .withRequestBody(absent())
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adform/test-adform-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/adform/test-auction-adform-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adform/test-auction-adform-response.json", response,
                singletonList("adform"));
    }
}
