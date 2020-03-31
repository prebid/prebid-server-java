package org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class NoTypeStrategyTest {
    private static final int PURPOSE_ID = 1;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private NoTypeStrategy target;

    @Mock
    private TCString tcString;

    @Mock
    private IntIterable allowedVendors;
    @Mock
    private IntIterable allowedVendorsLI;

    @Before
    public void setUp() {
        given(tcString.getVendorConsent()).willReturn(allowedVendors);
        given(tcString.getVendorLegitimateInterest()).willReturn(allowedVendorsLI);

        given(allowedVendors.contains(anyInt())).willReturn(false);
        given(allowedVendorsLI.contains(anyInt())).willReturn(false);

        target = new NoTypeStrategy();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedListWhenVendorIsNotAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = singletonList(vendorPermission);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPermissions, false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnEmptyListWhenVendorIsNotAllowedAndVendorEnforced() {
        // given
        final List<VendorPermission> vendorPurpose = singletonList(VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll()));

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = singletonList(vendorPermission);

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = singletonList(vendorPermission);

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = singletonList(vendorPermission);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = singletonList(vendorPermission);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }


    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedForFirstAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = Arrays.asList(vendorPermission1, vendorPermission2);

        given(allowedVendors.contains(eq(1))).willReturn(true);
        given(allowedVendors.contains(eq(2))).willReturn(false);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedForFirstAndVendorIsEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = Arrays.asList(vendorPermission1, vendorPermission2);

        given(allowedVendors.contains(eq(1))).willReturn(true);
        given(allowedVendors.contains(eq(2))).willReturn(false);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeAndVendorLIIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = Arrays.asList(vendorPermission1, vendorPermission2);

        given(allowedVendorsLI.contains(eq(1))).willReturn(true);
        given(allowedVendorsLI.contains(eq(2))).willReturn(false);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeAndVendorLIIsAllowedAndVendorIsEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPurpose = Arrays.asList(vendorPermission1, vendorPermission2);

        given(allowedVendorsLI.contains(eq(1))).willReturn(true);
        given(allowedVendorsLI.contains(eq(2))).willReturn(false);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorPurpose, true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1);
    }
}

