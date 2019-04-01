package org.prebid.server.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.Json;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.lang.String.format;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource("test-application.properties")
public abstract class IntegrationTest extends VertxTest {

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;

    @SuppressWarnings("unchecked")
    @ClassRule
    public static final WireMockClassRule wireMockRule = new WireMockClassRule(
            options().port(WIREMOCK_PORT).extensions(IntegrationTest.CacheResponseTransformer.class));

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    static final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(APP_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    @BeforeClass
    public static void setUp() throws IOException {
        wireMockRule.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom("storedrequests/test-periodic-refresh.json"))));
        wireMockRule.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom("currency/latest.json"))));
    }

    static String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(ApplicationTest.class.getResourceAsStream(file)));
    }

    static String legacyAuctionResponseFrom(String templatePath, Response response, List<String> bidders)
            throws IOException {

        return auctionResponseFrom(templatePath, response,
                "bidder_status.find { it.bidder == '%s' }.response_time_ms", bidders);
    }

    static String openrtbAuctionResponseFrom(String templatePath, Response response, List<String> bidders)
            throws IOException {

        return auctionResponseFrom(templatePath, response, "ext.responsetimemillis.%s", bidders);
    }

    private static String auctionResponseFrom(String templatePath, Response response, String responseTimePath,
                                              List<String> bidders) throws IOException {
        final String hostAndPort = "localhost:" + WIREMOCK_PORT;
        final String cachePath = "/cache";
        final String cacheEndpoint = "http://" + hostAndPort + cachePath;

        String result = jsonFrom(templatePath)
                .replaceAll("\\{\\{ cache.resource_url }}", cacheEndpoint + "?uuid=")
                .replaceAll("\\{\\{ cache.host }}", hostAndPort)
                .replaceAll("\\{\\{ cache.path }}", cachePath);

        for (final String bidder : bidders) {
            result = result.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                    "http://" + hostAndPort + "/" + bidder + "-exchange");
            result = setResponseTime(response, result, bidder, responseTimePath);
        }

        return result;
    }

    private static String setResponseTime(Response response, String expectedResponseJson, String bidder,
                                          String responseTimePath) {
        final Object val = response.path(format(responseTimePath, bidder));
        final Integer responseTime = val instanceof Integer ? (Integer) val : null;
        if (responseTime != null) {
            expectedResponseJson = expectedResponseJson.replaceAll("\"\\{\\{ " + bidder + "\\.response_time_ms }}\"",
                    responseTime.toString());
        }

        final Object cacheVal = response.path("ext.responsetimemillis.cache");
        final Integer cacheResponseTime = val instanceof Integer ? (Integer) cacheVal : null;
        if (cacheResponseTime != null) {
            expectedResponseJson = expectedResponseJson.replaceAll("\"\\{\\{ cache\\.response_time_ms }}\"",
                    cacheResponseTime.toString());
        }

        return expectedResponseJson;
    }

    private static String cacheResponseFromRequestJson(String requestAsString, String requestCacheIdMapFile) throws
            IOException {
        List<CacheObject> responseCacheObjects = new ArrayList<>();

        try {
            final BidCacheRequest cacheRequest = mapper.readValue(requestAsString, BidCacheRequest.class);
            final JsonNode jsonNodeMatcher =
                    mapper.readTree(ApplicationTest.class.getResourceAsStream(requestCacheIdMapFile));
            final List<PutObject> puts = cacheRequest.getPuts();

            for (PutObject putItem : puts) {
                if (putItem.getType().equals("json")) {
                    String id = putItem.getValue().get("id").textValue() + "@" + putItem.getValue().get("price");
                    String uuid = jsonNodeMatcher.get(id).textValue();
                    responseCacheObjects.add(CacheObject.of(uuid));
                } else {
                    String id = putItem.getValue().textValue();
                    String uuid = jsonNodeMatcher.get(id).textValue();
                    responseCacheObjects.add(CacheObject.of(uuid));
                }
            }

            final BidCacheResponse bidCacheResponse = BidCacheResponse.of(responseCacheObjects);
            return Json.encode(bidCacheResponse);
        } catch (IOException e) {
            throw new IOException("Error while matching cache ids");
        }
    }

    public static class CacheResponseTransformer extends ResponseTransformer {

        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(
                Request request, com.github.tomakehurst.wiremock.http.Response response, FileSource files,
                Parameters parameters) {

            final String newResponse;
            try {
                newResponse = cacheResponseFromRequestJson(request.getBodyAsString(),
                        parameters.getString("matcherName"));
            } catch (IOException e) {
                return com.github.tomakehurst.wiremock.http.Response.response()
                        .body(e.getMessage())
                        .status(500)
                        .build();
            }
            return com.github.tomakehurst.wiremock.http.Response.response().body(newResponse).status(200).build();
        }

        @Override
        public String getName() {
            return "cache-response-transformer";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }
}
