package org.prebid.server.privacy.ccpa;

import org.junit.Test;
import org.prebid.server.exception.PreBidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CcpaTest {

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAisNull() {
        // given
        final Ccpa ccpa = Ccpa.of(null);

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAisEmpty() {
        // given
        final Ccpa ccpa = Ccpa.of("");

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAisBlank() {
        // given
        final Ccpa ccpa = Ccpa.of(" ");

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAisInvalid() {
        // given
        final Ccpa ccpa = Ccpa.of("invalid");

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAHasNoInOutSaleIndex() {
        // given
        final Ccpa ccpa = Ccpa.of("1YNY");

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAHasNoInOutSaleIndexLowercase() {
        // given
        final Ccpa ccpa = Ccpa.of("1yny");

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnFalseWhenCCPAHasNoInOutSaleIndexMixedCase() {
        // given
        final Ccpa ccpa = Ccpa.of("1-Ny");

        // when and then
        assertThat(ccpa.isEnforced()).isFalse();
    }

    @Test
    public void isEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndex() {
        // given
        final Ccpa ccpa = Ccpa.of("1NYN");

        // when and then
        assertThat(ccpa.isEnforced()).isTrue();
    }

    @Test
    public void isEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndexLowercase() {
        // given
        final Ccpa ccpa = Ccpa.of("1nyn");

        // when and then
        assertThat(ccpa.isEnforced()).isTrue();
    }

    @Test
    public void isEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndexMixedCase() {
        // given
        final Ccpa ccpa = Ccpa.of("1-Yn");

        // when and then
        assertThat(ccpa.isEnforced()).isTrue();
    }

    @Test
    public void isValidShouldReturnTrueWhenValidCcpaStringIsProvided() {
        // given, when and then
        assertThat(Ccpa.isValid("1YYY")).isTrue();
        assertThat(Ccpa.isValid("1NNN")).isTrue();
        assertThat(Ccpa.isValid("1NYN")).isTrue();
        assertThat(Ccpa.isValid("1NyN")).isTrue();
        assertThat(Ccpa.isValid("1nyN")).isTrue();
        assertThat(Ccpa.isValid("1---")).isTrue();
        assertThat(Ccpa.isValid("1--N")).isTrue();
        assertThat(Ccpa.isValid("1--y")).isTrue();
        assertThat(Ccpa.isValid("1Y-y")).isTrue();
    }

    @Test
    public void isValidShouldReturnFalseWhenNotValidCcpaStringIsProvided() {
        // given, when and then
        assertThat(Ccpa.isValid("2YYY")).isFalse();
        assertThat(Ccpa.isValid("")).isFalse();
        assertThat(Ccpa.isValid(null)).isFalse();
        assertThat(Ccpa.isValid("1iyN")).isFalse();
        assertThat(Ccpa.isValid("1nyNa")).isFalse();
        assertThat(Ccpa.isValid("1-----")).isFalse();
        assertThat(Ccpa.isValid("1#1251-N")).isFalse();
        assertThat(Ccpa.isValid("1")).isFalse();
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
