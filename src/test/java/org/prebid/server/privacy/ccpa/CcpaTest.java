package org.prebid.server.privacy.ccpa;

import org.junit.Test;
import org.prebid.server.exception.PreBidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CcpaTest {

    @Test
    public void isCCPAEnforcedShouldReturnFalseWhenCCPAisBlank() {
        // given
        final Ccpa ccpa = Ccpa.of("");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isFalse();
    }

    @Test
    public void isCCPAEnforcedShouldReturnFalseWhenCCPAisNull() {
        // given
        final Ccpa ccpa = Ccpa.of(null);

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isFalse();
    }

    @Test
    public void isCCPAEnforcedShouldReturnFalseWhenCCPAisInvalid() {
        // given
        final Ccpa ccpa = Ccpa.of("invalid");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isFalse();
    }

    @Test
    public void isCCPAEnforcedShouldReturnFalseWhenCCPAHasNoInOutSaleIndex() {
        // given
        final Ccpa ccpa = Ccpa.of("1YNY");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isFalse();
    }

    @Test
    public void isCCPAEnforcedShouldReturnFalseWhenCCPAHasNoInOutSaleIndexLowercase() {
        // given
        final Ccpa ccpa = Ccpa.of("1yny");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isFalse();
    }

    @Test
    public void isCCPAEnforcedShouldReturnFalseWhenCCPAHasNoInOutSaleIndexMixedCase() {
        // given
        final Ccpa ccpa = Ccpa.of("1-Ny");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isFalse();
    }

    @Test
    public void isCCPAEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndex() {
        // given
        final Ccpa ccpa = Ccpa.of("1NYN");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isTrue();
    }

    @Test
    public void isCCPAEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndexLowercase() {
        // given
        final Ccpa ccpa = Ccpa.of("1nyn");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isTrue();
    }

    @Test
    public void isCCPAEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndexMixedCase() {
        // given
        final Ccpa ccpa = Ccpa.of("1-Yn");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isTrue();
    }

    @Test
    public void isCcpaStringShouldReturnTrueWhenValidCcpaStringIsProvided() {
        // given, when and then
        assertThat(Ccpa.isCcpaString("1YYY")).isTrue();
        assertThat(Ccpa.isCcpaString("1NNN")).isTrue();
        assertThat(Ccpa.isCcpaString("1NYN")).isTrue();
        assertThat(Ccpa.isCcpaString("1NyN")).isTrue();
        assertThat(Ccpa.isCcpaString("1nyN")).isTrue();
        assertThat(Ccpa.isCcpaString("1---")).isTrue();
        assertThat(Ccpa.isCcpaString("1--N")).isTrue();
        assertThat(Ccpa.isCcpaString("1--y")).isTrue();
        assertThat(Ccpa.isCcpaString("1Y-y")).isTrue();
    }

    @Test
    public void isCcpaStringShouldReturnFalseWhenNotValidCcpaStringIsProvided() {
        // given, when and then
        assertThat(Ccpa.isCcpaString("2YYY")).isFalse();
        assertThat(Ccpa.isCcpaString("")).isFalse();
        assertThat(Ccpa.isCcpaString(null)).isFalse();
        assertThat(Ccpa.isCcpaString("1iyN")).isFalse();
        assertThat(Ccpa.isCcpaString("1nyNa")).isFalse();
        assertThat(Ccpa.isCcpaString("1-----")).isFalse();
        assertThat(Ccpa.isCcpaString("1#1251-N")).isFalse();
        assertThat(Ccpa.isCcpaString("1")).isFalse();
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenLengthIsNotFour() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("largerthanfour"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must contain 4 characters");
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenVersionIsNotOne() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("2YYY"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must specify version 1");
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenExplicitNoticeInvalid() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("1IYN"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must specify 'N' or 'n', 'Y' or 'y', '-' for the explicit notice");
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenOptOutSaleInvalid() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("1nIN"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must specify 'N' or 'n', 'Y' or 'y', '-' for the opt-out sale");
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenServiceProviderAgreementInvalid() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("1NYI"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must specify 'N' or 'n', 'Y' or 'y', '-' for the "
                        + "limited service provider agreement");
    }
}
