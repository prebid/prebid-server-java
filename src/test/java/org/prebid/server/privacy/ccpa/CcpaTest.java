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
    public void isCCPAEnforcedShouldReturnTrueWhenCCPAHasYesInOutSaleIndex() {
        // given
        final Ccpa ccpa = Ccpa.of("1NYN");

        // when and then
        assertThat(ccpa.isCCPAEnforced()).isTrue();
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
                .hasMessage("us_privacy must specify 'N', 'Y', or '-' for the explicit notice");
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenOptOutSaleInvalid() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("1NIN"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must specify 'N', 'Y', or '-' for the opt-out sale");
    }

    @Test
    public void validateUsPrivacyShouldThrowPrebidExceptionWhenServiceProviderAgreementInvalid() {
        // given, when and then
        assertThatThrownBy(() -> Ccpa.validateUsPrivacy("1NYI"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("us_privacy must specify 'N', 'Y', or '-' for the limited service provider agreement");
    }
}