package org.prebid.server.bidder;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncUtilTest {

    @Test
    public void enrichUsersyncUrlWithFormatShouldNotChangeUrlIfMissing() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat(null, "iframe");

        // then
        assertThat(url).isNull();
    }

    @Test
    public void enrichUsersyncUrlWithFormatShouldNotChangeUrlIfEmpty() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat("", "iframe");

        // then
        assertThat(url).isEmpty();
    }

    @Test
    public void enrichUsersyncUrlWithFormatShouldNotChangeUrlIfTypeMissing() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat("", null);

        // then
        assertThat(url).isEmpty();
    }

    @Test
    public void enrichUsersyncUrlWithFormatShouldNotChangeUrlIfTypeEmpty() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat("", "");

        // then
        assertThat(url).isEmpty();
    }

    @Test
    public void enrichUsersyncUrlWithFormatShouldAddFormat() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat("//url", "iframe");

        // then
        assertThat(url).isEqualTo("//url?f=b");
    }

    @Test
    public void enrichUsersyncUrlWithFormatShouldAppendFormat() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat("http://url?param1=value1", "redirect");

        // then
        assertThat(url).isEqualTo("http://url?param1=value1&f=i");
    }

    @Test
    public void enrichUsersyncUrlWithFormatShouldInsertFormat() {
        // given and when
        final String url = UsersyncUtil.enrichUsersyncUrlWithFormat("http://url?param1=value1&param2=value2",
                "redirect");

        // then
        assertThat(url).isEqualTo("http://url?param1=value1&f=i&param2=value2");
    }
}
