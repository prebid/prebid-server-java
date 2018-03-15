package org.prebid.server.bidder.adform.util;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.junit.Test;
import org.prebid.server.bidder.adform.utils.AdformHttpUtil;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AdformHttpUtilTest {

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private static final CharSequence X_REQUEST_AGENT = HttpHeaders.createOptimized("X-Request-Agent");
    private static final CharSequence X_FORWARDED_FOR = HttpHeaders.createOptimized("X-Forwarded-For");

    @Test
    public void buildAdformHeadersShouldReturnAllHeaders() {
        // given
        final MultiMap commonHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);

        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders(commonHeaders, "0.1.0", "userAgent", "ip",
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
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders(MultiMap.caseInsensitiveMultiMap(), "0.1.0",
                "userAgent", "ip", "", "buyeruid");

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpHeaders.REFERER.toString());
    }

    @Test
    public void buildAdformHeadersShouldNotContainCookieHeaderIfUserIdIsEmpty() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders(MultiMap.caseInsensitiveMultiMap(), "0.1.0",
                "userAgent", "ip", "referer", "");

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpHeaders.COOKIE.toString());
    }

    @Test
    public void buildAdformUrlShouldReturnCorrectUrl() {
        // when
        final String url = AdformHttpUtil.buildAdformUrl(Arrays.asList("15", "16"), "http://adx.adform.net/adx", "tid",
                false);

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo("http://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&bWlkPTE1&bWlkPTE2");
    }

    @Test
    public void buildAdformUrlShouldReturnHttpsProtocolIfSecureIsTrue(){
        // when
        final String url = AdformHttpUtil.buildAdformUrl(Arrays.asList("15", "16"), "http://adx.adform.net/adx", "tid",
                true);

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo("https://adx.adform.net/adx/?CC=1&rp=4&fd=1&stid=tid&bWlkPTE1&bWlkPTE2");
    }
}
