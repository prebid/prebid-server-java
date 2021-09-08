package org.prebid.server.it;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.LineItemService;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RunWith(SpringRunner.class)
@TestPropertySource(locations = {"test-application.properties", "deals/test-deals-application.properties"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PrematureReturnTest extends VertxTest {

    private static final int APP_PORT = 8070;
    private static final int WIREMOCK_PORT = 8090;

    private static final RequestSpecification SPEC = IntegrationTest.spec(APP_PORT);

    @SuppressWarnings("unchecked")
    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_RULE = new WireMockClassRule(
            options().port(WIREMOCK_PORT).extensions(IntegrationTest.ResponseOrderTransformer.class));

    private static final String RUBICON = "rubicon";

    @Autowired
    private LineItemService lineItemService;

    @Autowired
    private Clock clock;

    @BeforeClass
    public static void setUpInner() throws IOException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/planner-plan"))
                .withQueryParam("instanceId", equalTo("localhost"))
                .withQueryParam("region", equalTo("local"))
                .withQueryParam("vendor", equalTo("local"))
                .withBasicAuth("username", "password")
                .withHeader("pg-trx-id", new AnythingPattern())
                .willReturn(aResponse()
                        .withBody(plannerResponseFrom("deals/premature/test-planner-plan-response.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/planner-register"))
                .withBasicAuth("username", "password")
                .withHeader("pg-trx-id", new AnythingPattern())
                .withRequestBody(equalToJson(IntegrationTest.jsonFrom("deals/test-planner-register-request-1.json"),
                        false, true))
                .willReturn(aResponse()));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/user-data-details"))
                .withRequestBody(equalToJson(IntegrationTest
                        .jsonFrom("deals/test-user-data-details-request-1.json"), false, true))
                .willReturn(aResponse().withBody(IntegrationTest
                        .jsonFrom("deals/test-user-data-details-response-1.json"))));
    }

    @Test
    public void openrtb2AuctionWhenAllThreeBidsReturnsInOrderWithMinimalDelay() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem1");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem3");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-1.json"), 200, 0L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 20L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-3.json"), 200, 20L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-in-order-response.json", response, singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenAllThreeBidsReturnsInReverseOrderWithMinimalDelay() throws IOException,
            JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem3");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem1");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-1.json"), 200, 20L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 20L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-3.json"), 200, 0L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-in-reverse-order-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenAllThreeBidsReturnsInOrderWithHighDelay() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem1");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem3");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-1.json"), 200, 0L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 200L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-3.json"), 200, 200L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-in-order-response.json", response, singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenAllThreeBidsReturnsInReverseOrderWithHighDelay() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem3");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem1");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-1.json"), 200, 200L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 200L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-3.json"), 200, 0L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-in-reverse-order-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenOnlyFirstBidComesBack() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem1");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem3");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-1.json"), 200, 0L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-2.json"), 200, 20L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-3.json"), 200, 20L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-first-bid-only-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenOnlySecondBidComesBack() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem1");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem3");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-1.json"), 200, 0L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 20L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-3.json"), 200, 20L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-second-bid-only-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenOnlyThirdBidComesBack() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem1");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem3");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-1.json"), 200, 0L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-2.json"), 200, 20L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-3.json"), 200, 20L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();
        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-third-bid-only-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenFirstAndSecondBidsComesBackReverseWithHighDelay() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem1");
        lineItemResponseOrder.add("extLineItem3");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-1.json"), 200, 200L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 0L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-3.json"), 200, 200L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-first-and-second-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    @Test
    public void openrtb2AuctionWhenSecondAndThirdBidsComesBackReverseWithHighDelay() throws IOException, JSONException {
        // given
        awaitForLineItemMetadata();

        final Queue<String> lineItemResponseOrder = new LinkedList<>();
        lineItemResponseOrder.add("extLineItem3");
        lineItemResponseOrder.add("extLineItem2");
        lineItemResponseOrder.add("extLineItem1");

        final Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters = new HashMap<>();
        idToExecutionParameters.put("extLineItem1", IntegrationTest.BidRequestExecutionParameters.of("extLineItem1",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-no-bid-response-1.json"), 200, 200L));
        idToExecutionParameters.put("extLineItem2", IntegrationTest.BidRequestExecutionParameters.of("extLineItem2",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-2.json"), 200, 200L));
        idToExecutionParameters.put("extLineItem3", IntegrationTest.BidRequestExecutionParameters.of("extLineItem3",
                IntegrationTest.jsonFrom("deals/premature/test-rubicon-bid-response-3.json"), 200, 0L));

        stubExchange(lineItemResponseOrder, idToExecutionParameters);

        // when
        final io.restassured.response.Response response = givenResponse();

        final String expectedAuctionResponse = withTemporalFields(IntegrationTest.openrtbAuctionResponseFrom(
                "deals/premature/responses/test-auction-third-and-second-response.json", response,
                singletonList(RUBICON)));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbDeepDebugTimeComparator());
    }

    private io.restassured.response.Response givenResponse() throws IOException {
        return given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("User-Agent", "userAgent")
                .header("X-Forwarded-For", "185.199.110.153")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .body(IntegrationTest.jsonFrom("deals/premature/test-auction-request.json"))
                .post("/openrtb2/auction");
    }

    private void stubExchange(Queue<String> lineItemResponseOrder,
                              Map<String, IntegrationTest.BidRequestExecutionParameters> idToExecutionParameters) {
        WIRE_MOCK_RULE.stubFor(post(urlMatching("/rubicon-exchange.*"))
                .willReturn(aResponse()
                        .withTransformers("response-order-transformer")
                        .withTransformerParameter(IntegrationTest.LINE_ITEM_RESPONSE_ORDER, lineItemResponseOrder)
                        .withTransformerParameter(IntegrationTest.ID_TO_EXECUTION_PARAMETERS,
                                idToExecutionParameters)));
    }

    private void awaitForLineItemMetadata() {
        await().atMost(10, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> lineItemService.accountHasDeals("2001", ZonedDateTime.now(clock)));
    }

    private static String plannerResponseFrom(String templatePath) throws IOException {
        final ZonedDateTime now = ZonedDateTime.now().withFixedOffsetZone();

        return IntegrationTest.jsonFrom(templatePath)
                .replaceAll("\\{\\{ now }}", now.toString())
                .replaceAll("\\{\\{ lineItem.startTime }}", now.minusDays(5).toString())
                .replaceAll("\\{\\{ lineItem.endTime }}", now.plusDays(5).toString())
                .replaceAll("\\{\\{ plan.startTime }}", now.minusHours(1).toString())
                .replaceAll("\\{\\{ plan.endTime }}", now.plusHours(1).toString());
    }

    private static CustomComparator openrtbDeepDebugTimeComparator() {
        final ValueMatcher<Object> timeValueMatcher = (actual, expected) -> {
            try {
                return mapper.readValue("\"" + actual.toString() + "\"", ZonedDateTime.class) != null;
            } catch (IOException e) {
                return false;
            }
        };

        final ArrayValueMatcher<Object> arrayValueMatcher = new ArrayValueMatcher<>(new CustomComparator(
                JSONCompareMode.NON_EXTENSIBLE,
                new Customization("ext.debug.trace.deals[*].time", timeValueMatcher)));

        final List<Customization> arrayValueMatchers = IntStream.range(1, 5)
                .mapToObj(i -> new Customization("ext.debug.trace.lineitems.lineItem" + i,
                        new ArrayValueMatcher<>(new CustomComparator(
                                JSONCompareMode.NON_EXTENSIBLE,
                                new Customization("ext.debug.trace.lineitems.lineItem" + i + "[*].time",
                                        timeValueMatcher)))))
                .collect(Collectors.toList());

        arrayValueMatchers.add(new Customization("ext.debug.trace.deals", arrayValueMatcher));
        arrayValueMatchers.add(new Customization("**.requestheaders.x-prebid", (o1, o2) -> true));

        return new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                arrayValueMatchers.toArray(new Customization[0]));
    }

    private String withTemporalFields(String auctionResponse) {
        final ZonedDateTime dateTime = ZonedDateTime.now(clock);

        return auctionResponse
                .replaceAll("\"?\\{\\{ userdow }}\"?", Integer.toString(
                        dateTime.getDayOfWeek().get(WeekFields.SUNDAY_START.dayOfWeek())))
                .replaceAll("\"?\\{\\{ userhour }}\"?", Integer.toString(dateTime.getHour()));
    }
}
