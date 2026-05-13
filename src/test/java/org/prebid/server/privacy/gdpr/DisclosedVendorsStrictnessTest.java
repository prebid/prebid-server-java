package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import com.iabtcf.encoder.TCStringEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.settings.model.GdprConfig;

import java.time.Instant;
import java.time.Month;
import java.time.Year;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

public class DisclosedVendorsStrictnessTest {

    private static final Instant CUTOFF_DATE = Year.of(2026)
            .atMonth(Month.MARCH)
            .atDay(1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);

    private DisclosedVendorsStrictness target;

    @BeforeEach
    public void setUp() {
        target = target(true);
    }

    @Test
    public void isValidShouldReturnTrueOnInvalidIfStrictnessDisabled() {
        // given
        target = target(false);
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .toTCString();

        // when and then
        assertThat(target.isValid(tcString)).isTrue();
    }

    @Test
    public void isValidShouldReturnFalseIfStrictnessEnabledByDefault() {
        // given
        target = new DisclosedVendorsStrictness(null);
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .toTCString();

        // when and then
        assertThat(target.isValid(tcString)).isFalse();
    }

    @Test
    public void isValidShouldReturnTrueOnInvalidIfLatestUpdateBeforeCutoffDate() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .created(CUTOFF_DATE.minusNanos(1L))
                .lastUpdated(CUTOFF_DATE.minusNanos(1L))
                .toTCString();

        // when and then
        assertThat(target.isValid(tcString)).isTrue();
    }

    @Test
    public void isValidShouldReturnFalseIfLatestUpdateAfterCutoffDate() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .created(CUTOFF_DATE.minusNanos(1L))
                .lastUpdated(CUTOFF_DATE)
                .toTCString();

        // when and then
        assertThat(target.isValid(tcString)).isFalse();
    }

    @Test
    public void isValidShouldReturnTrueIfDisclosedVendorsPresent() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .addDisclosedVendors(1)
                .toTCString();

        // when and then
        assertThat(target.isValid(tcString)).isTrue();
    }

    @Test
    public void isVendorDisclosedShouldReturnTrueOnNotDisclosedIfStrictnessDisabled() {
        // given
        target = target(false);
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .toTCString();

        // when and then
        assertThat(target.isVendorDisclosed(tcString, 1)).isTrue();
    }

    @Test
    public void isVendorDisclosedShouldReturnFalseIfStrictnessEnabledByDefault() {
        // given
        target = new DisclosedVendorsStrictness(null);
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .toTCString();

        // when and then
        assertThat(target.isVendorDisclosed(tcString, 1)).isFalse();
    }

    @Test
    public void isVendorDisclosedShouldReturnFalseOnNull() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .toTCString();

        // when and then
        assertThat(target.isVendorDisclosed(tcString, null)).isFalse();
    }

    @Test
    public void isVendorDisclosedShouldReturnTrueOnNotDisclosedIfLatestUpdateBeforeCutoffDate() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .created(CUTOFF_DATE.minusNanos(1L))
                .lastUpdated(CUTOFF_DATE.minusNanos(1L))
                .toTCString();

        // when and then
        assertThat(target.isVendorDisclosed(tcString, 1)).isTrue();
    }

    @Test
    public void isVendorDisclosedShouldReturnFalseIfLatestUpdateAfterCutoffDate() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .created(CUTOFF_DATE.minusNanos(1L))
                .lastUpdated(CUTOFF_DATE)
                .toTCString();

        // when and then
        assertThat(target.isVendorDisclosed(tcString, 1)).isFalse();
    }

    @Test
    public void isVendorDisclosedShouldReturnTrueIfDisclosed() {
        // given
        final TCString tcString = TCStringEncoder.newBuilder()
                .version(2)
                .addDisclosedVendors(1)
                .toTCString();

        // when and then
        assertThat(target.isVendorDisclosed(tcString, 1)).isTrue();
    }

    private static DisclosedVendorsStrictness target(boolean enabled) {
        return new DisclosedVendorsStrictness(GdprConfig.builder().strictDisclosedVendorsTreatment(enabled).build());
    }
}
