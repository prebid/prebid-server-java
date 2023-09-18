package org.prebid.server.bidder;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncUtilTest {

    @Test
    public void resolveFormatShouldPreferFormatOverride() {
        // given and when
        final UsersyncFormat format = UsersyncUtil.resolveFormat(
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.IFRAME)
                        .formatOverride(UsersyncFormat.PIXEL)
                        .build());

        // then
        assertThat(format).isEqualTo(UsersyncFormat.PIXEL);
    }

    @Test
    public void resolveFormatShouldReturnDefaultFormatForUsersyncTypeWhenOverrideAbsent() {
        // given and when
        final UsersyncFormat format = UsersyncUtil.resolveFormat(
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.IFRAME)
                        .build());

        // then
        assertThat(format).isEqualTo(UsersyncFormat.BLANK);
    }

    @Test
    public void enrichUrlWithFormatShouldNotChangeUrlIfMissing() {
        // given and when
        final String url = UsersyncUtil.enrichUrlWithFormat(null, UsersyncFormat.BLANK);

        // then
        assertThat(url).isNull();
    }

    @Test
    public void enrichUrlWithFormatShouldNotChangeUrlIfEmpty() {
        // given and when
        final String url = UsersyncUtil.enrichUrlWithFormat("", UsersyncFormat.BLANK);

        // then
        assertThat(url).isEmpty();
    }

    @Test
    public void enrichUrlWithFormatShouldNotChangeUrlIfTypeMissing() {
        // given and when
        final String url = UsersyncUtil.enrichUrlWithFormat("", UsersyncFormat.BLANK);

        // then
        assertThat(url).isEmpty();
    }

    @Test
    public void enrichUrlWithFormatShouldInsertFormat() {
        // given and when
        final String url = UsersyncUtil.enrichUrlWithFormat(
                "http://url?param1=value1&param2=value2",
                UsersyncFormat.PIXEL);

        // then
        assertThat(url).isEqualTo("http://url?param1=value1&f=i&param2=value2");
    }
}
