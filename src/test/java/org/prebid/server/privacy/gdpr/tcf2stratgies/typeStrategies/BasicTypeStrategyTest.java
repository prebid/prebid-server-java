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
import static org.mockito.BDDMockito.given;

public class BasicTypeStrategyTest {
    private static final int PURPOSE_ID = 1;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private BasicTypeStrategy target;

    @Mock
    private TCString tcString;

    @Mock
    private IntIterable allowedVendors;
    @Mock
    private IntIterable allowedVendorsLI;
    @Mock
    private IntIterable purposesConsent;
    @Mock
    private IntIterable purposesLI;

    @Before
    public void setUp() {
        given(tcString.getAllowedVendors()).willReturn(allowedVendors);
        given(tcString.getVendorLegitimateInterest()).willReturn(allowedVendorsLI);
        given(tcString.getPurposesConsent()).willReturn(purposesConsent);
        given(tcString.getPurposesLITransparency()).willReturn(purposesLI);

        given(allowedVendors.contains(anyInt())).willReturn(false);
        given(allowedVendorsLI.contains(anyInt())).willReturn(false);
        given(purposesConsent.contains(anyInt())).willReturn(false);
        given(purposesLI.contains(anyInt())).willReturn(false);

        target = new BasicTypeStrategy();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsNotAllowed() {
        // given
        final List<VendorPermission> vendorGdprEnforced = singletonList(VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll()));
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll()));

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowed() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorGdprEnforced = singletonList(vendorPermission1);
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll()));

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).containsOnly(vendorPermission1);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowed() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorGdprEnforced = singletonList(vendorPermission1);
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll()));

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).containsOnly(vendorPermission1);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeIsAllowed() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorGdprEnforced = singletonList(vendorPermission1);
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll()));

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeAndVendorIsAllowed() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorGdprEnforced = singletonList(vendorPermission1);
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(vendorPermission2);

        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(purposesConsent.contains(PURPOSE_ID)).willReturn(true);
        given(purposesLI.contains(PURPOSE_ID)).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeAndVendorLIIsAllowed() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorGdprEnforced = singletonList(vendorPermission1);
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(vendorPermission2);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(purposesConsent.contains(PURPOSE_ID)).willReturn(true);
        given(purposesLI.contains(PURPOSE_ID)).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValues() {
        // given
        final VendorPermission vendorPermissionInVendorLI = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermissionInVendor = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermissionRestricted = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermissionInPurpose = VendorPermission.of(4, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermissionInPurposeLi = VendorPermission.of(5, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermissionPurposeRestricted = VendorPermission.of(6, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorGdprEnforced = Arrays.asList(vendorPermissionInVendorLI, vendorPermissionInVendor, vendorPermissionRestricted);
        final List<VendorPermission> vendorAndPurposeEnforced = Arrays.asList(vendorPermissionInPurpose, vendorPermissionInPurposeLi, vendorPermissionPurposeRestricted);

        given(allowedVendorsLI.contains(1)).willReturn(true);
        given(allowedVendors.contains(2)).willReturn(true);

        given(allowedVendors.contains(4)).willReturn(true);
        given(purposesConsent.contains(PURPOSE_ID)).willReturn(true);
        given(allowedVendors.contains(5)).willReturn(true);
        given(purposesLI.contains(PURPOSE_ID)).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);

        // then
        assertThat(result).containsOnly(vendorPermissionInVendorLI, vendorPermissionInVendor, vendorPermissionInPurpose, vendorPermissionInPurposeLi);
    }
}

