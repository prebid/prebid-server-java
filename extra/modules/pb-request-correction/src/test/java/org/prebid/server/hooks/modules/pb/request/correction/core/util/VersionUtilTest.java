package org.prebid.server.hooks.modules.pb.request.correction.core.util;

import org.junit.jupiter.api.Test;

import static java.lang.Integer.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {

    @Test
    public void isVersionLessThanShouldReturnFalseIfVersionGreaterThanRequired() {
        // when and then
        assertThat(VersionUtil.isVersionLessThan("2.4.3", 2, 2, 3)).isFalse();
    }

    @Test
    public void isVersionLessThenShouldReturnFalseIfVersionIsEqualToRequired() {
        // when and then
        assertThat(VersionUtil.isVersionLessThan("2.4.3", 2, 4, 3)).isFalse();
    }

    @Test
    public void isVersionLessThenShouldReturnTrueIfVersionIsLessThanRequired() {
        // when and then
        assertThat(VersionUtil.isVersionLessThan("2.2.3", 2, 4, 3)).isTrue();
    }

    @Test
    public void isVersionLessThenShouldReturnFalseIfVersionIsUnparsable() {
        // when and then
        assertThat(VersionUtil.isVersionLessThan("2.2a.3", 2, 4, 3)).isFalse();
    }

    @Test
    public void isVersionLessThenShouldReturnExpectedResults() {
        // major
        assertThat(VersionUtil.isVersionLessThan("0", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("1", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("3", 2, 2, 3)).isFalse();

        // minor
        assertThat(VersionUtil.isVersionLessThan("0" + MAX_VALUE, 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("1" + MAX_VALUE, 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.0", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.1", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.2", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.3", 2, 2, 3)).isFalse();

        // patch
        assertThat(VersionUtil.isVersionLessThan("0.%d.%d".formatted(MAX_VALUE, MAX_VALUE), 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("1.%d.%d".formatted(MAX_VALUE, MAX_VALUE), 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.1." + MAX_VALUE, 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.2.3", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.2.2", 2, 2, 3)).isTrue();
        assertThat(VersionUtil.isVersionLessThan("2.2.3", 2, 2, 3)).isFalse();
    }
}
