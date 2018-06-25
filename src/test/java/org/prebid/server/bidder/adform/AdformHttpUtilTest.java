package org.prebid.server.bidder.adform;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.junit.Test;
import org.prebid.server.bidder.adform.model.UrlParameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.util.Lists.emptyList;

public class AdformHttpUtilTest {

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private static final CharSequence X_REQUEST_AGENT = HttpHeaders.createOptimized("X-Request-Agent");
    private static final CharSequence X_FORWARDED_FOR = HttpHeaders.createOptimized("X-Forwarded-For");

    @Test
    public void buildAdformHeadersShouldReturnAllHeaders() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip",
                "www.example.com", "buyeruid");

        // then
        assertThat(headers).hasSize(7)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpHeaders.USER_AGENT.toString(), "userAgent"),
                        tuple(X_FORWARDED_FOR.toString(), "ip"),
                        tuple(X_REQUEST_AGENT.toString(), "PrebidAdapter 0.1.0"),
                        tuple(HttpHeaders.REFERER.toString(), "www.example.com"),
                        tuple(HttpHeaders.COOKIE.toString(), "uid=buyeruid"));
    }

    @Test
    public void buildAdformHeadersShouldNotContainRefererHeaderIfRefererIsEmpty() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip", "", "buyeruid");

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpHeaders.REFERER.toString());
    }

    @Test
    public void buildAdformHeadersShouldNotContainCookieHeaderIfUserIdIsEmpty() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip", "referer", "");

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpHeaders.COOKIE.toString());
    }

    @Test
    public void buildAdformUrlShouldReturnCorrectUrl() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(
                UrlParameters.builder()
                        .masterTagIds(asList(15L, 16L))
                        .priceTypes(singletonList("gross"))
                        .endpointUrl("http://adx.adform.net/adx")
                        .tid("tid")
                        .ip("ip")
                        .advertisingId("adId")
                        .secure(false)
                        .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo(
                "http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&adid=adId&pt=gross&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldReturnHttpsProtocolIfSecureIsTrue() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("gross"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .advertisingId("adId")
                .secure(true)
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo(
                "https://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&adid=adId&pt=gross&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldNotContainAdidParamIfAdvertisingIdIsMissed() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("gross"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .advertisingId(null)
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&pt=gross&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldNotContainPtParamIfPriceTypesListIsEmpty() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(emptyList())
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&bWlkPTE1&bWlkPTE2");

    }

    @Test
    public void buildAdformUrlShouldNotContainPtParamIfNoValidPriceTypes() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("notValid"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldHasNetPtParamIfOnlyNetIsInPriceTypesList() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .masterTagIds(asList(15L, 16L))
                .priceTypes(singletonList("Net"))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&pt=net&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldHasGrossPtParamIfOnlyGrossIsInPriceTypesList() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .priceTypes(singletonList("Gross"))
                .masterTagIds(asList(15L, 16L))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&pt=gross&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldHasGrossPtParamIfGrossAndNetAndNotValidPriceTypesAreInList() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(UrlParameters.builder()
                .priceTypes(asList("Net", "Gross", "NotValid"))
                .masterTagIds(asList(15L, 16L))
                .endpointUrl("http://adx.adform.net/adx")
                .tid("tid")
                .ip("ip")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&ip=ip&pt=gross&bWlkPTE1&bWlkPTE2");
    }
}
