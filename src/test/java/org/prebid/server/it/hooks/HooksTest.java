package org.prebid.server.it.hooks;

import io.restassured.http.Header;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.it.IntegrationTest;
import org.prebid.server.version.PrebidVersionProvider;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.empty;

public class HooksTest extends IntegrationTest {

    private static final String RUBICON = "rubicon";

    @Autowired
    private PrebidVersionProvider versionProvider;

    @Autowired
    private AnalyticsReporterDelegator analyticsReporterDelegator;

    @Test
    public void openrtb2AuctionShouldRunHooksAtEachStage() throws IOException, JSONException {
        Mockito.reset(analyticsReporterDelegator);

        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("hooks/sample-module/test-rubicon-bid-request-1.json", versionProvider)))
                .willReturn(aResponse().withBody(jsonFrom("hooks/sample-module/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .queryParam("sample-it-module-update", "headers,body")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/sample-module/test-auction-sample-module-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/sample-module/test-auction-sample-module-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(
                expectedAuctionResponse,
                response.asString(),
                new CustomComparator(
                        JSONCompareMode.LENIENT,
                        new Customization("seatbid[*].bid[*].id", (o1, o2) -> true)));

        //todo: remove everything below after at least one exitpoint module is added and tested by functional tests
        assertThat(response.getHeaders())
                .extracting(Header::getName, Header::getValue)
                .contains(tuple("Exitpoint-Hook-Header", "Exitpoint-Hook-Value"));

        final ArgumentCaptor<AuctionEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        Mockito.verify(analyticsReporterDelegator).processEvent(eventCaptor.capture(), Mockito.any());

        final AuctionEvent actualEvent = eventCaptor.getValue();
        final List<StageExecutionOutcome> exitpointHookOutcomes = actualEvent.getAuctionContext()
                .getHookExecutionContext().getStageOutcomes().get(Stage.exitpoint);

        final TagsImpl expectedTags = TagsImpl.of(singletonList(ActivityImpl.of(
                "exitpoint-device-id",
                "success",
                singletonList(ResultImpl.of(
                        "success",
                        mapper.createObjectNode().put("exitpoint-some-field", "exitpoint-some-value"),
                        AppliedToImpl.builder()
                                .impIds(singletonList("impId1"))
                                .request(true)
                                .build())))));

        assertThat(exitpointHookOutcomes).isNotEmpty().hasSize(1).first()
                .extracting(StageExecutionOutcome::getGroups)
                .extracting(List::getFirst)
                .extracting(GroupExecutionOutcome::getHooks)
                .extracting(List::getFirst)
                .extracting(HookExecutionOutcome::getAnalyticsTags)
                .isEqualTo(expectedTags);

        Mockito.reset(analyticsReporterDelegator);
    }

    @Test
    public void openrtb2AuctionShouldBeRejectedByEntrypointHook() throws IOException {
        given(SPEC)
                .queryParam("sample-it-module-reject", "true")
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/sample-module/test-auction-sample-module-request.json"))
                .post("/openrtb2/auction")
                .then()
                .statusCode(200)
                .body("seatbid", empty());
    }

    @Test
    public void openrtb2AuctionShouldBeRejectedByRawAuctionRequestHook() throws IOException {
        given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-raw-auction-request-reject-request.json"))
                .post("/openrtb2/auction")
                .then()
                .statusCode(200)
                .body("seatbid", empty());
    }

    @Test
    public void openrtb2AuctionShouldBeRejectedByProcessedAuctionRequestHook() throws IOException {
        given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-processed-auction-request-reject-request.json"))
                .post("/openrtb2/auction")
                .then()
                .statusCode(200)
                .body("seatbid", empty());
    }

    @Test
    public void openrtb2AuctionShouldRejectRubiconBidderByBidderRequestHook() throws IOException, JSONException {
        // when
        final Response response = given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-bidder-request-reject-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/reject/test-auction-bidder-request-reject-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);

        WIRE_MOCK_RULE.verify(0, postRequestedFor(urlPathEqualTo("/rubicon-exchange")));
    }

    @Test
    public void openrtb2AuctionShouldRejectRubiconBidderByRawBidderResponseHook() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .willReturn(aResponse().withBody(jsonFrom("hooks/reject/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-raw-bidder-response-reject-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/reject/test-auction-raw-bidder-response-reject-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);

        WIRE_MOCK_RULE.verify(1, postRequestedFor(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("hooks/reject/test-rubicon-bid-request-1.json", versionProvider))));
    }

    @Test
    public void openrtb2AuctionShouldRejectRubiconBidderByProcessedBidderResponseHook()
            throws IOException, JSONException {

        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .willReturn(aResponse().withBody(jsonFrom("hooks/reject/test-rubicon-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("User-Agent", "userAgent")
                .body(jsonFrom("hooks/reject/test-auction-processed-bidder-response-reject-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "hooks/reject/test-auction-processed-bidder-response-reject-response.json",
                response,
                singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.LENIENT);

        WIRE_MOCK_RULE.verify(1, postRequestedFor(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("hooks/reject/test-rubicon-bid-request-1.json", versionProvider))));
    }
}
