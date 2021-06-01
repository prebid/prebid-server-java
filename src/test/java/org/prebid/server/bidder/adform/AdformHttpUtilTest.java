package org.prebid.server.bidder.adform;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adform.model.UrlParameters;
import org.prebid.server.util.HttpUtil;

import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.util.Lists.emptyList;

public class AdformHttpUtilTest extends VertxTest {

    private AdformHttpUtil httpUtil;

    @Before
    public void setUp() {
        httpUtil = new AdformHttpUtil();
    }

    @Test
    public void buildAdformHeadersShouldReturnAllHeaders() {
        // when
        final MultiMap headers = httpUtil.buildAdformHeaders(
                "0.1.0",
                "userAgent",
                "ip",
                "www.example.com",
                "buyeruid");

        // then
        assertThat(headers).hasSize(7)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "userAgent"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ip"),
                        tuple(HttpUtil.X_REQUEST_AGENT_HEADER.toString(), "PrebidAdapter 0.1.0"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "www.example.com"),
                        tuple(HttpUtil.COOKIE_HEADER.toString(), "uid=buyeruid"));
    }

    @Test
    public void buildAdformHeadersShouldNotContainRefererHeaderIfRefererIsEmpty() {
        // when
        final MultiMap headers = httpUtil.buildAdformHeaders(
                "0.1.0",
                "userAgent",
                "ip",
                "",
                "buyeruid");

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpUtil.REFERER_HEADER.toString());
    }

    @Test
    public void buildAdformHeadersShouldNotContainCookieHeaderIfUserIdIsEmpty() {
        // when
        final MultiMap headers = httpUtil.buildAdformHeaders(
                "0.1.0",
                "userAgent",
                "ip",
                "referer",
                "");

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpUtil.COOKIE_HEADER.toString());
    }

    @Test
    public void buildAdformHeaderShouldContainCookieHeaderOnlyWithUserIdIfUserIdPresent() {
        // when
        final MultiMap headers = httpUtil.buildAdformHeaders(
                "0.1.0",
                "userAgent",
                "ip",
                "referer",
                "buyeruid");

        // then
        assertThat(headers).extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.COOKIE_HEADER.toString(), "uid=buyeruid"));
    }

    @Test
    public void buildAdformUrlShouldReturnCorrectUrl() {
        // when
        final String url = httpUtil.buildAdformUrl(
                UrlParameters.builder()
                        .masterTagIds(asList(15L, 16L))
                        .keyValues(asList("color:red", "age:30-40"))
                        .keyWords(asList("red", "blue"))
                        .priceTypes(singletonList("gross"))
                        .cdims(asList("300x300,400x200", "300x200"))
                        .minPrices(asList(23.1, null))
                        .endpointUrl("http://adx.adform.net/adx")
                        .tid("tid")
                        .ip("ip")
                        .advertisingId("adId")
                        .gdprApplies("1")
                        .consent("consent")
                        .secure(false)
                        .currency("USD")
                        .eids("eyJ0ZXN0LmNvbSI6eyJvdGh")
                        .url("https://adform.com?a=b")
                        .build());

        // then
        final String expectedEncodedPart = Stream.of(
                "mid=15&rcur=USD&mkv=color:red&mkw=red&cdims=300x300,400x200&minp=23.10",
                "mid=16&rcur=USD&mkv=age:30-40&mkw=blue&cdims=300x200")
                .map(s -> Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes()))
                .collect(Collectors.joining("&"));

        assertThat(url).isEqualTo(
                "http://adx.adform.net/adx?CC=1&adid=adId&eids=eyJ0ZXN0LmNvbSI6eyJvdGh&"
                        + "fd=1&gdpr=1&gdpr_consent=consent&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&url=https%3A%2F%2Fadform.com%3Fa%3Db"
                        + "&" + expectedEncodedPart);
    }

    @Test
    public void buildAdformUrlShouldReturnHttpsProtocolIfSecureIsTrue() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("gross"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .advertisingId("adId")
                .gdprApplies("")
                .consent("")
                .secure(true)
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo(
                "https://adx.adform.net/adx?CC=1&adid=adId&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldNotContainAdidParamIfAdvertisingIdIsMissed() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("gross"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .advertisingId(null)
                .gdprApplies("")
                .consent("")
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4&"
                        + "stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldNotContainPtParamIfPriceTypesListIsEmpty() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(emptyList())
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .consent("")
                .gdprApplies("")
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&rp=4&"
                + "stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldNotContainPtParamIfNoValidPriceTypes() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("notValid"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .gdprApplies("")
                .consent("")
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&rp=4&"
                + "stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldHasNetPtParamIfOnlyNetIsInPriceTypesList() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("Net"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .gdprApplies("")
                .consent("")
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=net&rp=4&"
                + "stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldHasGrossPtParamIfOnlyGrossIsInPriceTypesList() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .priceTypes(singletonList("Gross"))
                .masterTagIds(asList(15L, 16L))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .gdprApplies("")
                .consent("")
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldHasGrossPtParamIfGrossAndNetAndNotValidPriceTypesAreInList() {
        // when
        final String url = httpUtil.buildAdformUrl(UrlParameters.builder()
                .priceTypes(asList("Net", "Gross", "NotValid"))
                .masterTagIds(asList(15L, 16L))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .gdprApplies("")
                .consent("")
                .currency("USD")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1JnJjdXI9VVNE&bWlkPTE2JnJjdXI9VVNE");
    }

    @Test
    public void buildAdformUrlShouldNotContainEidsParamIfEmptyEids() {
        // when
        final String url = httpUtil.buildAdformUrl(
                UrlParameters.builder()
                        .masterTagIds(asList(15L, 16L))
                        .keyValues(asList("color:red", "age:30-40"))
                        .keyWords(asList("red", "blue"))
                        .priceTypes(singletonList("gross"))
                        .endpointUrl("http://adx.adform.net/adx")
                        .tid("tid")
                        .ip("ip")
                        .advertisingId("adId")
                        .gdprApplies("1")
                        .consent("consent")
                        .secure(false)
                        .currency("USD")
                        .eids(null)
                        .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo(
                "http://adx.adform.net/adx?CC=1&adid=adId&fd=1&gdpr=1&gdpr_consent=consent&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1JnJjdXI9VVNEJm1rdj1jb2xvcjpyZWQmbWt3PXJlZA"
                        + "&bWlkPTE2JnJjdXI9VVNEJm1rdj1hZ2U6MzAtNDAmbWt3PWJsdWU");
    }
}
