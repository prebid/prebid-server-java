package org.prebid.server.bidder.adform;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.junit.Test;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.AdformDigitrustPrivacy;
import org.prebid.server.bidder.adform.model.UrlParameters;

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
                "www.example.com", "buyeruid", AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(true)));

        // then
        assertThat(headers).hasSize(7)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpHeaders.USER_AGENT.toString(), "userAgent"),
                        tuple(X_FORWARDED_FOR.toString(), "ip"),
                        tuple(X_REQUEST_AGENT.toString(), "PrebidAdapter 0.1.0"),
                        tuple(HttpHeaders.REFERER.toString(), "www.example.com"),
                        tuple(HttpHeaders.COOKIE.toString(),
                                // Base64 encoded {"id":"id","version":1,"keyv":123,"privacy":{"optout":true}}
                                "uid=buyeruid;DigiTrust.v1.identity=eyJpZCI6ImlkIiwidmVyc2lvbiI6MSwia2V5diI6MTIzLC" +
                                        "Jwcml2YWN5Ijp7Im9wdG91dCI6dHJ1ZX19"));
    }

    @Test
    public void buildAdformHeadersShouldNotContainRefererHeaderIfRefererIsEmpty() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip", "", "buyeruid",
                AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(true)));

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpHeaders.REFERER.toString());
    }

    @Test
    public void buildAdformHeadersShouldNotContainCookieHeaderIfUserIdAndDigiTrustAreEmpty() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip", "referer", "", null);

        // then
        assertThat(headers).extracting(Map.Entry::getKey).doesNotContain(HttpHeaders.COOKIE.toString());
    }

    @Test
    public void buildAdformHeaderShouldContainCookieHeaderOnlyWithUserIdIfUserIdPresentAndDigitrustAbsent() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip", "referer", "buyeruid",
                null);

        // then
        assertThat(headers).extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpHeaders.COOKIE.toString(), "uid=buyeruid"));
    }

    @Test
    public void buildAdformHeaderShouldContainCookieHeaderOnlyWithDigitrustIfUserIsAbsentAndDigitrustPresent() {
        // when
        final MultiMap headers = AdformHttpUtil.buildAdformHeaders("0.1.0", "userAgent", "ip", "referer", "",
                AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(true)));

        // then
        assertThat(headers).extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpHeaders.COOKIE.toString(),
                        // Base64 encoded {"id":"id","version":1,"keyv":123,"privacy":{"optout":true}}
                        "DigiTrust.v1.identity=eyJpZCI6ImlkIiwidmVyc2lvbiI6MSwia2V5diI6MTIzLCJwcml2YWN5Ijp7Im9wdG91dC"
                                + "I6dHJ1ZX19"));
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
                        .gdprApplies("1")
                        .consent("consent")
                        .secure(false)
                        .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo(
                "http://adx.adform.net/adx?CC=1&adid=adId&fd=1&gdpr=1&gdpr_consent=consent&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1&bWlkPTE2");
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
                .gdprApplies("")
                .consent("")
                .secure(true)
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url).isEqualTo(
                "https://adx.adform.net/adx?CC=1&adid=adId&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1&bWlkPTE2");
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
                .gdprApplies("")
                .consent("")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4&"
                        + "stid=tid&bWlkPTE1&bWlkPTE2");
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
                .consent("")
                .gdprApplies("")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&rp=4&stid=tid&bWlkPTE1&bWlkPTE2");

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
                .gdprApplies("")
                .consent("")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&rp=4&stid=tid&bWlkPTE1&bWlkPTE2");
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
                .gdprApplies("")
                .consent("")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=net&rp=4&stid=tid&bWlkPTE1&bWlkPTE2");
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
                .gdprApplies("")
                .consent("")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1&bWlkPTE2");
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
                .gdprApplies("")
                .consent("")
                .build());

        // then
        // bWlkPTE1 is Base64 encoded mid=15 and bWlkPTE2 encoded mid=16, so bWlkPTE1&bWlkPTE2 = mid=15&mid=16
        assertThat(url)
                .isEqualTo("http://adx.adform.net/adx?CC=1&fd=1&gdpr=&gdpr_consent=&ip=ip&pt=gross&rp=4"
                        + "&stid=tid&bWlkPTE1&bWlkPTE2");
    }
}
