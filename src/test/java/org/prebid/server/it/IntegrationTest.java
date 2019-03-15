package org.prebid.server.it;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.ClassRule;
import org.junit.Rule;
import org.prebid.server.VertxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.lang.String.format;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource("test-application.properties")
public abstract class IntegrationTest extends VertxTest {

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;

    @SuppressWarnings("unchecked")
    @ClassRule
    public static final WireMockClassRule wireMockRule =
            new WireMockClassRule(options().port(WIREMOCK_PORT).extensions(ApplicationTest.CacheResponseTransformer.class));

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    static final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(APP_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

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
}
