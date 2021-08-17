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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class ConversantTest extends IntegrationTest {

    private static final String CONVERSANT = "conversant";
    private static final String CONVERSANT_ALIAS = "conversantAlias";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConversant() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/test-conversant-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/conversant/test-conversant-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/conversant/test-auction-conversant-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/conversant/test-auction-conversant-response.json", response,
                singletonList(CONVERSANT));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConversantAlias() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/alias/test-conversant-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/conversant/alias/test-conversant-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/conversant/alias/test-auction-conversant-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/conversant/alias/test-auction-conversant-response.json", response,
                asList(CONVERSANT, CONVERSANT_ALIAS));
    }
}
