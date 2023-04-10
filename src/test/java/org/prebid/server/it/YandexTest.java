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
public class YandexTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromYandex() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(
                urlPathEqualTo("/yandex-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/yandex/test-yandex-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/yandex/test-yandex-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/yandex/test-auction-yandex-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/yandex/test-auction-yandex-response.json", response, singletonList("yandex"));
    }
}
