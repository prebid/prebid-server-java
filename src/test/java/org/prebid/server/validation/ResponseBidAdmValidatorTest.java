package org.prebid.server.validation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseBidAdmValidatorTest {

    //todo: add separate test for unique cases
    private final ResponseBidAdmValidator target = new ResponseBidAdmValidator(Set.of("www.w3.org", "www.url.ua/page"));

    @Test
    public void shouldValidateAdm() {
        assertThat(target.isSecure("http:")).isFalse();
        assertThat(target.isSecure("http:http:")).isFalse();
        // treats 'https' as a domain, which is not allowed
        assertThat(target.isSecure("http://https:")).isFalse();
        // 'http:///' is actually empty
        assertThat(target.isSecure("https:http:///")).isFalse();
        assertThat(target.isSecure("http://www.url.uahttps:")).isFalse();

        assertThat(target.isSecure("https:")).isTrue();
        assertThat(target.isSecure("http:https:")).isTrue();
        assertThat(target.isSecure("http:/https:")).isTrue();
        assertThat(target.isSecure("http:https://")).isTrue();
        // 'http://' is treated as empty only in the end
        assertThat(target.isSecure("https:http://")).isTrue();
        assertThat(target.isSecure("https:http:")).isTrue();
        assertThat(target.isSecure("https:https:")).isTrue();
        assertThat(target.isSecure("http://www.w3.orghttps:")).isTrue();
        assertThat(target.isSecure("http://www.url.ua/pagehttps:")).isTrue();
        // empty domain because no slashes
        assertThat(target.isSecure("http:www.w3.orghttps:")).isTrue();
    }

    @Test
    public void shouldValidateUrlEncodedAdm() {
        // case insensitive
        assertThat(target.isSecure("https%3a")).isFalse();
        assertThat(target.isSecure("http%3A")).isFalse();
        assertThat(target.isSecure("http%3Ahttp%3A")).isFalse();
        //treats 'https' as a domain, which is not allowed
        assertThat(target.isSecure("http%3A%2F%2Fhttps%3A")).isFalse();
        assertThat(target.isSecure("https%3Ahttp%3A%2F%2F%2F")).isFalse();
        assertThat(target.isSecure("http%3A%2F%2Fwww.url.uahttps%3A")).isFalse();

        assertThat(target.isSecure("https%3A")).isTrue();
        assertThat(target.isSecure("http%3Ahttps%3A")).isTrue();
        assertThat(target.isSecure("http%3A%2Fhttps%3A")).isTrue();
        assertThat(target.isSecure("http%3Ahttps%3A%2F%2F")).isTrue();
        // http:// is treated as empty only in the end
        assertThat(target.isSecure("https%3Ahttp%3A%2F%2F")).isTrue();
        assertThat(target.isSecure("https%3Ahttps%3A")).isTrue();
        assertThat(target.isSecure("http%3A%2F%2Fwww.w3.orghttps%3A")).isTrue();
        assertThat(target.isSecure("http%3A%2F%2Fwww.url.ua%2Fpagehttps%3A")).isTrue();
        // empty domain because no slashes
        assertThat(target.isSecure("http%3Awww.w3.orghttps%3A")).isTrue();
    }

    @Test
    public void shouldValidatePlainAndUrlEncodedPaths() {
        // combined plain and encoded is not allowed
        assertThat(target.isSecure("http://www.url.ua%2Fpagehttps%3A")).isFalse();

        assertThat(target.isSecure("http%3Ahttps:")).isTrue();
        assertThat(target.isSecure("http:https%3A")).isTrue();
        assertThat(target.isSecure("https%3Ahttp%3A//")).isTrue();
        // 'http%3A//' is treated like an empty 'http:' and '//' separately
        assertThat(target.isSecure("http%3A//any.domainhttps%3A")).isTrue();
        assertThat(target.isSecure("http://www.url.ua/pagehttps%3A")).isTrue();
        // empty domain because no slashes
        assertThat(target.isSecure("http:www.w3.orghttps%3A")).isTrue();
        assertThat(target.isSecure("http://www.w3.orghttps://any.domain,http%3Aanything")).isTrue();
    }
}
