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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.emptyList;

@RunWith(SpringRunner.class)
public class AppnexusVideoTest extends IntegrationTest {

    @Test
    public void openrtb2VideoShouldRespondWithBidsFromAppnexus() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/video/test-video-appnexus-bid-request-1.json"), true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/video/test-video-appnexus-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/video/test-video-appnexus-bid-request-2.json"), true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/video/test-video-appnexus-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/video/test-video-cache-request.json")))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/video/test-video-cache-response-matcher.json")));

        // when
        final Response response = responseFor("openrtb2/video/test-video-appnexus-request.json",
                Endpoint.openrtb2_video);

        // then
        assertJsonEquals("openrtb2/video/test-video-appnexus-response.json", response, emptyList());
    }
}
